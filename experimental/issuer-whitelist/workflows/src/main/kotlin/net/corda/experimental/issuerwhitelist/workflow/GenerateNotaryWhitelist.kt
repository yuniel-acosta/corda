package net.corda.experimental.issuerwhitelist.workflow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.serialize
import net.corda.experimental.issuerwhitelist.contract.NotaryWhitelist
import net.corda.experimental.issuerwhitelist.contract.NotaryWhitelistVerifier
import net.corda.node.services.api.ServiceHubInternal
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * Generates a jar file containing the serialized notary whitelist.
 * The jar file is signed using the legal identity key, and upload to to the attachment storage.
 */
@StartableByRPC
class GenerateNotaryWhitelist(private val forStateType: Class<*>, private val notaries: List<Party>) : FlowLogic<AttachmentId>() {
    @Suspendable
    override fun call(): AttachmentId {
        val whitelist = NotaryWhitelist(forStateType, notaries)
        val jarPath = generateAndSignJar(whitelist)

        val attachmentId = serviceHub.attachments.importAttachment(
                jarPath.inputStream(),
                serviceHub.myInfo.legalIdentities.first().name.toString(),
                "notary-whitelist"
        )
        jarPath.delete()
        return attachmentId
    }

    private fun generateAndSignJar(whitelist: NotaryWhitelist): File {
        val whitelistBlob = whitelist.serialize()

        val keyAlias = X509Utilities.NODE_IDENTITY_KEY_ALIAS
        val tempFile = File("notary-whitelist.jar")
        val serviceHubInternal = serviceHub as ServiceHubInternal

        write(whitelistBlob.bytes, tempFile)

        val keyStore = serviceHubInternal.configuration.signingCertificateStore
        val cmd = "jarsigner -keystore ${keyStore.path} -storepass ${keyStore.storePassword} ${tempFile.absolutePath} $keyAlias"
        logger.info("CMD to run: $cmd")
        val process = Runtime.getRuntime().exec(cmd)

        val output = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))

        var line: String? = reader.readLine()
        while (line != null) {
            output.append(line + "\n")
            line = reader.readLine()
        }

        var line2: String? = errorReader.readLine()
        while (line2 != null) {
            output.append(line2 + "\n")
            line2 = errorReader.readLine()
        }

        val exitVal = process.waitFor()
        if (exitVal == 0) {
            logger.info("Success!\n$output")
        } else {
            logger.info("Error ..\n$output")
        }
        return tempFile
    }

    private fun write(contents: ByteArray, jarFile: File) {
        val fos = FileOutputStream(jarFile)
        val jos = JarOutputStream(fos)
        val bos = BufferedOutputStream(jos)
        jos.putNextEntry(JarEntry(NotaryWhitelistVerifier.NOTARY_WHITELIST_JAR_ENTRY))
        bos.write(contents)
        bos.flush()
        bos.close()
    }
}
