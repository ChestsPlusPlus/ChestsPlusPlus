import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Downloads a pinned file and verifies its checksum. Skipped when the output is already present and valid. */
abstract class DownloadFile : DefaultTask() {
    @get:Input abstract val url: Property<String>

    /** Expected digest, in the form `sha256:<hex>` or `sha512:<hex>`. */
    @get:Input abstract val checksum: Property<String>

    @get:OutputFile abstract val destination: RegularFileProperty

    init {
        outputs.upToDateWhen { task ->
            val t = task as DownloadFile
            val file = t.destination.get().asFile
            file.isFile && t.matches(file.toPath())
        }
    }

    @TaskAction
    fun download() {
        val target = destination.get().asFile.toPath()
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, target.fileName.toString(), ".part")
        try {
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
            val request = HttpRequest.newBuilder(URI.create(url.get()))
                .header("User-Agent", "ChestsPlusPlus-build (github.com/ChestsPlusPlus/ChestsPlusPlus)")
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(temp))
            if (response.statusCode() != 200) {
                throw GradleException("Download of ${url.get()} failed with HTTP ${response.statusCode()}")
            }
            if (!matches(temp)) throw GradleException("Checksum mismatch for ${url.get()}")
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun matches(path: java.nio.file.Path): Boolean {
        val (algorithm, expected) = checksum.get().split(':', limit = 2)
        val digest = MessageDigest.getInstance(
            when (algorithm) {
                "sha256" -> "SHA-256"
                "sha512" -> "SHA-512"
                else -> throw GradleException("Unsupported checksum algorithm $algorithm")
            }
        )
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }
}
