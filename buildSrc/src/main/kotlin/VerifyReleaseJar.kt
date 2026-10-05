import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Release guard: the shipped jar must not contain the test harness, spikes or any debug entry point.
 */
@CacheableTask
abstract class VerifyReleaseJar : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val jar: RegularFileProperty

    /** Package prefixes (slash form) that must not appear, e.g. the harness package. */
    @get:Input abstract val forbiddenPackages: ListProperty<String>

    /** Case-insensitive strings that must not appear in any entry name or content. */
    @get:Input abstract val forbiddenStrings: ListProperty<String>

    @get:Input abstract val expectedMain: Property<String>
    @get:Input abstract val expectedBootstrapper: Property<String>
    @get:Input abstract val expectedLoader: Property<String>

    /** Top-level paper-plugin.yml keys the release descriptor may declare. */
    @get:Input abstract val allowedDescriptorKeys: ListProperty<String>

    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val problems = mutableListOf<String>()
        val forbidden = forbiddenStrings.get().map { it.lowercase() }
        var descriptor: String? = null
        ZipFile(jar.get().asFile).use { zip ->
            for (entry in zip.entries()) {
                val name = entry.name
                forbiddenPackages.get().filter { name.startsWith(it) }.forEach { problems += "$name is in forbidden package $it" }
                if (name.contains("TestHarness", ignoreCase = true)) problems += "$name looks like a test-harness resource"
                forbidden.filter { name.lowercase().contains(it) }.forEach { problems += "entry name $name contains '$it'" }
                if (entry.isDirectory) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                // ISO-8859-1 maps bytes 1:1, so ASCII literals in class constant pools are found too.
                val text = String(bytes, Charsets.ISO_8859_1).lowercase()
                forbidden.filter { text.contains(it) }.forEach { problems += "$name contains '$it'" }
                if (name == "paper-plugin.yml") descriptor = String(bytes, Charsets.UTF_8)
            }
        }
        val yaml = descriptor
        if (yaml == null) {
            problems += "paper-plugin.yml is missing"
        } else {
            val keys = yaml.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("#") && !it.startsWith("-") }
                .map { it.substringBefore(':').trim() }
                .toList()
            (keys - allowedDescriptorKeys.get().toSet()).forEach { problems += "paper-plugin.yml declares unexpected key '$it'" }
            mapOf("main" to expectedMain, "bootstrapper" to expectedBootstrapper, "loader" to expectedLoader).forEach { (key, expected) ->
                val actual = value(yaml, key)
                if (actual != expected.get()) problems += "paper-plugin.yml $key is $actual, expected ${expected.get()}"
            }
        }
        val out = report.get().asFile
        out.parentFile.mkdirs()
        if (problems.isNotEmpty()) {
            out.writeText(problems.joinToString("\n", postfix = "\n"))
            throw GradleException("Release jar ${jar.get().asFile.name} failed verification:\n  " + problems.joinToString("\n  "))
        }
        out.writeText("OK\n")
    }

    private fun value(yaml: String, key: String): String? =
        yaml.lineSequence()
            .firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')
            ?.trim()
            ?.trim('\'', '"')
}
