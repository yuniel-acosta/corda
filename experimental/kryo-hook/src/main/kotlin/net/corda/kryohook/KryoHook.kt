package net.corda.kryohook

import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import javassist.ClassPool
import javassist.CtClass
import net.corda.kryohook.KryoHookAgent.Companion.instrumentClassname
import net.corda.kryohook.KryoHookAgent.Companion.minimumSize
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class KryoHookAgent {
    companion object {
        val DEFAULT_INSTRUMENT_CLASSNAME = "net.corda.node.services.statemachine.FlowStateMachineImpl"
        val DEFAULT_MINIMUM_SIZE = 8 * 1024
        var instrumentClassname = DEFAULT_INSTRUMENT_CLASSNAME
        var minimumSize = DEFAULT_MINIMUM_SIZE

        val log by lazy {
            LoggerFactory.getLogger("KryoHook")
        }

        @JvmStatic
        fun premain(argumentsString: String?, instrumentation: Instrumentation) {
            parseArguments(argumentsString)
            instrumentation.addTransformer(KryoHook)
        }

        fun parseArguments(argumentsString: String?) {
            instrumentClassname = DEFAULT_INSTRUMENT_CLASSNAME
            minimumSize = DEFAULT_MINIMUM_SIZE
            argumentsString?.let {
                val nvpList = it.split(",")
                nvpList.forEach {
                    val nvpItem = it.split("=")
                    if (nvpItem.size == 2) {
                        if (nvpItem[0].trim() == "instrumentClassname")
                            instrumentClassname = nvpItem[1]
                        else if (nvpItem[1].trim() == "minimumSize")
                            try {
                                minimumSize = nvpItem[1].toInt()
                            } catch (e: NumberFormatException) {
                            }
                    }
                }
            }
            println("Running Kryo hook agent with following arguments: instrumentClassname = $instrumentClassname, minimumSize = $minimumSize")
        }
    }
}

/**
 * The hook simply records the write() entries and exits together with the output offset at the time of the call.
 * This is recorded in a StrandID -> List<StatsEvent> map.
 *
 * Later we "parse" these lists into a tree.
 */
object KryoHook : ClassFileTransformer {
    val classPool = ClassPool.getDefault()

    val hookClassName = javaClass.name

    override fun transform(
            loader: ClassLoader?,
            className: String,
            classBeingRedefined: Class<*>?,
            protectionDomain: ProtectionDomain?,
            classfileBuffer: ByteArray
    ): ByteArray? {
        if (className.startsWith("java") || className.startsWith("javassist") || className.startsWith("kotlin")) {
            return null
        }
        return try {
            val clazz = classPool.makeClass(ByteArrayInputStream(classfileBuffer))
            instrumentClass(clazz)?.toBytecode()
        } catch (throwable: Throwable) {
            println("SOMETHING WENT WRONG")
            throwable.printStackTrace(System.out)
            null
        }
    }

    private fun instrumentClass(clazz: CtClass): CtClass? {
        for (method in clazz.declaredBehaviors) {
            if (method.name == "write") {
                val parameterTypeNames = method.parameterTypes.map { it.name }
                if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Output", "java.lang.Object")) {
                    if (method.isEmpty) continue
                    println("Instrumenting ${clazz.name}")
                    method.insertBefore("$hookClassName.${this::writeEnter.name}($1, $2, $3);")
                    method.insertAfter("$hookClassName.${this::writeExit.name}($1, $2, $3);")
                    return clazz
                }
            }
        }
        return null
    }

    // StrandID -> StatsEvent map
    val events = ConcurrentHashMap<Long, Pair<ArrayList<StatsEvent>, AtomicInteger>>()

    @JvmStatic
    fun writeEnter(kryo: Kryo, output: Output, obj: Any) {
        val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
        list.add(StatsEvent.Enter(obj.javaClass.name, output.total()))
        count.incrementAndGet()
    }
    @JvmStatic
    fun writeExit(kryo: Kryo, output: Output, obj: Any) {
        val (list, count) = events[Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(obj.javaClass.name, output.total()))
        if ((count.decrementAndGet() == 0) &&
                (obj.javaClass.name == instrumentClassname) &&
                (output.total() >= minimumSize)) {
            val sb = StringBuilder()
            prettyStatsTree(0, readTree(list, 0).second, sb)
            KryoHookAgent.log.info("$obj\n$sb")
            list.clear()
        }
    }

    private fun prettyStatsTree(indent: Int, statsTree: StatsTree, builder: StringBuilder) {
        when (statsTree) {
            is StatsTree.Object -> {
                builder.append(String.format("%03d:", indent / 2))
                builder.append(kotlin.CharArray(indent) { ' ' })
                builder.append(statsTree.className)
                builder.append(" ")
                builder.append(String.format("%,d", statsTree.size))
                builder.append("\n")
                for (child in statsTree.children) {
                    prettyStatsTree(indent + 2, child, builder)
                }
            }
        }
    }
}

/**
 * TODO we could add events on entries/exits to field serializers to get more info on what's being serialised.
 */
sealed class StatsEvent {
    data class Enter(val className: String, val offset: Long) : StatsEvent()
    data class Exit(val className: String, val offset: Long) : StatsEvent()
}

/**
 * TODO add Field constructor.
 */
sealed class StatsTree {
    data class Object(
            val className: String,
            val size: Long,
            val children: List<StatsTree>
    ) : StatsTree()
}

fun readTree(events: List<StatsEvent>, index: Int): Pair<Int, StatsTree> {
    val event = events[index]
    when (event) {
        is StatsEvent.Enter -> {
            val (nextIndex, children) = readTrees(events, index + 1)
            val exit = events[nextIndex] as StatsEvent.Exit
            require(event.className == exit.className)
            return Pair(nextIndex + 1, StatsTree.Object(event.className, exit.offset - event.offset, children))
        }
        is StatsEvent.Exit -> {
            throw IllegalStateException("Wasn't expecting Exit")
        }
    }
}

fun readTrees(events: List<StatsEvent>, index: Int): Pair<Int, List<StatsTree>> {
    val trees = ArrayList<StatsTree>()
    var i = index
    while (true) {
        val event = events.getOrNull(i)
        when (event) {
            is StatsEvent.Enter -> {
                val (nextIndex, tree) = readTree(events, i)
                trees.add(tree)
                i = nextIndex
            }
            is StatsEvent.Exit -> {
                return Pair(i, trees)
            }
            null -> {
                return Pair(i, trees)
            }
        }
    }
}
