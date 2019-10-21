package net.corda.core.serialization.internal

import net.corda.core.contracts.Attachment
import java.nio.ByteBuffer
import java.security.CodeSigner
import java.security.CodeSource
import java.security.SecureClassLoader

open class SimpleAttachmentsClassLoader(
        private val attachment: Attachment,
        parent: ClassLoader?
): SecureClassLoader(parent) {

    private val url = AttachmentURLStreamHandlerFactory.toUrl(attachment)

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        val connection = attachment.openAsJAR()
        connection.use { jar ->
            val resourceName = name.replace('.', '/') + ".class"
            while (true) {
                val entry = jar.nextJarEntry ?: break
                if (resourceName == entry.name) {
                    val byteCode = ByteBuffer.wrap(jar.readBytes())
                    val c = defineClass(name, byteCode, CodeSource(url, arrayOf<CodeSigner>()))
                    return c
                }
            }
        }
        throw ClassNotFoundException(name)
    }

}