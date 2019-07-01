What is this?
=============

This is a javaagent that may be used to output the type and size of serialized Kryo objects at runtime when running Corda.
For a given Kryo serialized object, the agent outputs 

1. information about the object such as a Fiber id (associated with a flow) that can be used to correlate with that flows details in the main Corda logs.

Example
```
Fiber@10000006:[800b8dca-a668-4705-a18d-3e71bd9a7f57][task: co.paralleluniverse.fibers.RunnableFiberTask@4dda48db(Fiber@10000006), target: null, scheduler: net.corda.node.services.statemachine.StateMachineManagerImpl$FiberScheduler@5a99377a]
```

2. a nested hierarchical view of its containing types (indented and tagged with depth size) and associated sizes (to a log4j2 logger)
 
Example:
```
000:net.corda.node.services.statemachine.FlowStateMachineImpl 15,124
001:  net.corda.node.services.statemachine.FlowStateMachineImpl 15,122
002:    java.lang.String 107
003:      [C 77
002:    co.paralleluniverse.fibers.Stack 14,065
003:      [J 181
003:      [Ljava.lang.Object; 13,678
004:        net.corda.node.services.FinalityHandler 615
005:          net.corda.node.services.statemachine.FlowSessionImpl 426
006:            net.corda.core.identity.Party 314
007:              net.i2p.crypto.eddsa.EdDSAPublicKey 45
007:              net.corda.core.identity.CordaX500Name 215
008:                java.lang.String 12
009:                  [C 5
008:                java.lang.String 17
009:                  [C 11
008:                java.lang.String 19
009:                  [C 13
```

Arguments
=========
The following arguments may be specified to customize what gets output:
 
``instrumentClassname``: specifies a Kryo type to process.
                        (default is to process all Flow object types: "net.corda.node.services.statemachine.FlowStateMachineImpl")
``minimumSize``: minimum size for a Kryo type to be considered for logging 
                (default is 8192)

There arguments are passed to the JVM along with the agent specification:

```
-javaagent:<PROJECT>/experimental/kryo-hook/build/libs/kryo-hook.jar=instrumentClassname=net.corda.vega.flows.SimmFlow,minimumSize=10240
```

Logging configuration
=====================
The agent will log output to a log4j2 configured logger.

It is recommended to configure a separate log file to capture this information by configuring an appender as follows:

```
    <Logger name="KryoHook" level="debug" additivity="false">
        <AppenderRef ref="kryohook-RollingFile-Appender"/>
    </Logger>
```

Note: it important to use "KryoHook" as the logger name.

In this instance we are specifying a Rolling File appender as follows:

```
    <RollingFile name="kryohook-RollingFile-Appender"
                 fileName="${log-path}/${kryohook-log-name}.log"
                 filePattern="${archive}/${kryohook-log-name}.%date{yyyy-MM-dd}-%i.log.gz">
        <PatternLayout pattern="[%-5level] %date{ISO8601}{UTC}Z [%t] %c{2}.%method - %msg%n"/>
        <Policies>
            <TimeBasedTriggeringPolicy/>
            <SizeBasedTriggeringPolicy size="100MB"/>
        </Policies>
        <DefaultRolloverStrategy min="1" max="100">
            <Delete basePath="${archive}" maxDepth="1">
                <IfFileName glob="${log-name}*.log.gz"/>
                <IfLastModified age="60d">
                    <IfAny>
                        <IfAccumulatedFileSize exceeds="10 GB"/>
                    </IfAny>
                </IfLastModified>
            </Delete>
        </DefaultRolloverStrategy>
    </RollingFile>   
```

You will need to define the following additional properties used by the above appender:

```
    <Properties>
        <Property name="log-path">${sys:log-path:-logs}</Property>
        <Property name="archive">${log-path}/archive</Property>
        <Property name="kryohook-log-name">kryohook-${hostName}</Property>
    </Properties>
```

Use ``-Dlog4j.configurationFile=/path/to/log4j2.xml`` to pass in the specified log4j2.xml file to the JVM at runtime (when launching Corda).

How do I run it
---------------

Build the agent:
```
./gradlew experimental:kryo-hook:jar
```

Run the JVM with the java agent parameter (mandatory), optional agent configuration items (instrumentClassname, minimumSize)
and (optional) custom log4j2 configuration file: 

```
java -Dlog4j.configurationFile=/path/to/log4j2.xml \
     -javaagent:<PROJECT>/experimental/kryo-hook/build/libs/kryo-hook.jar=instrumentClassname=net.corda.vega.flows.SimmFlow,minimumSize=10240 \  
     -jar corda.jar
```

Sample output
=============
Using the log4j2 configuration described about, the following output is generated to a file called ``logs\kryohook-myHostname`` under 
the Corda node directory for a single flow execution (in this case ):

```
Fiber@10000006:[800b8dca-a668-4705-a18d-3e71bd9a7f57][task: co.paralleluniverse.fibers.RunnableFiberTask@4dda48db(Fiber@10000006), target: null, scheduler: net.corda.node.services.statemachine.StateMachineManagerImpl$FiberScheduler@5a99377a]
000:net.corda.node.services.statemachine.FlowStateMachineImpl 15,124
001:  net.corda.node.services.statemachine.FlowStateMachineImpl 15,122
002:    java.lang.String 107
003:      [C 77
002:    co.paralleluniverse.fibers.Stack 14,065
003:      [J 181
003:      [Ljava.lang.Object; 13,678
004:        net.corda.node.services.FinalityHandler 615
005:          net.corda.node.services.statemachine.FlowSessionImpl 426
006:            net.corda.core.identity.Party 314
007:              net.i2p.crypto.eddsa.EdDSAPublicKey 45
007:              net.corda.core.identity.CordaX500Name 215
008:                java.lang.String 12
009:                  [C 5
008:                java.lang.String 17
009:                  [C 11
008:                java.lang.String 19
009:                  [C 13
004:        net.corda.core.flows.ReceiveTransactionFlow 227
005:          net.corda.core.node.StatesToRecord 1
004:        org.apache.logging.slf4j.Log4jLogger 53
004:        net.corda.core.internal.ResolveTransactionsFlow 10,973
005:          net.corda.core.transactions.SignedTransaction 10,638
006:            net.corda.core.serialization.SerializedBytes 9,900
006:            java.util.Collections$UnmodifiableList 734
007:              java.util.ArrayList 711
008:                net.corda.core.crypto.TransactionSignature 377
009:                  [B 65
009:                  net.i2p.crypto.eddsa.EdDSAPublicKey 45
009:                  net.corda.core.crypto.SignatureMetadata 72
008:                net.corda.core.crypto.TransactionSignature 141
009:                  [B 65
009:                  net.i2p.crypto.eddsa.EdDSAPublicKey 45
009:                  net.corda.core.crypto.SignatureMetadata 6
008:                net.corda.core.crypto.TransactionSignature 141
009:                  [B 65
009:                  net.i2p.crypto.eddsa.EdDSAPublicKey 45
009:                  net.corda.core.crypto.SignatureMetadata 6
004:        net.corda.core.utilities.UntrustworthyData 41
004:        net.corda.core.internal.FetchDataFlow$Request$End 0
004:        net.corda.node.services.statemachine.FlowSessionInternal 563
005:          net.corda.node.services.statemachine.SessionId 28
005:          java.util.concurrent.ConcurrentLinkedQueue 1
005:          net.corda.node.services.statemachine.FlowSessionState$Initiated 177
006:            net.corda.core.flows.FlowInfo 92
007:              java.lang.String 47
008:                [C 41
006:            net.corda.node.services.statemachine.SessionId 11
004:        net.corda.node.services.statemachine.ExistingSessionMessage 394
005:          net.corda.node.services.statemachine.DataSessionMessage 257
006:            net.corda.core.serialization.SerializedBytes 226
004:        net.corda.node.services.statemachine.SendOnly 41
004:        net.corda.node.services.statemachine.FlowStateMachineImpl$suspend$2 0
004:        kotlin.jvm.internal.Ref$ObjectRef 21
002:    co.paralleluniverse.strands.Strand$State 1
002:    net.corda.core.context.InvocationContext 532
003:      net.corda.core.context.InvocationOrigin$Peer 14
003:      net.corda.core.context.Trace 305
004:        net.corda.core.context.Trace$InvocationId 185
005:          java.lang.String 31
006:            [C 21
005:          java.time.Instant 10
005:          java.lang.String 79
006:            [C 73
004:        net.corda.core.context.Trace$SessionId 68
005:          java.lang.String 21
006:            [C 15
002:    net.corda.core.flows.StateMachineRunId 84
003:      java.util.UUID 56
002:    java.util.HashMap 50
003:      kotlin.Pair 32
002:    net.corda.core.identity.Party 146
003:      net.i2p.crypto.eddsa.EdDSAPublicKey 45
003:      net.corda.core.identity.CordaX500Name 82
004:        java.lang.String 12
005:          [C 5
004:        java.lang.String 23
005:          [C 13
004:        java.lang.String 23
005:          [C 13
002:    net.corda.node.services.statemachine.FlowStateMachineImpl$suspend$1 20
```
