/**
 * Turns the probe's results into report.md. Each figure is the median over the rounds. A plugin's overhead is its setup minus the baseline
 * on the same Minecraft version, which is the only fair way to compare v2 (Paper 1.21.7) with v3 (Paper 26.3).
 */
class BenchmarkReport(private val results: Map<String, List<Map<String, Any>>>, private val run: String) {

    private class Metric(val label: String, val value: (Map<String, Any>) -> Double, val decimals: Int = 2)

    private val metrics = listOf(
        Metric("MSPT mean", { it.number("msptMean") }),
        Metric("MSPT p50", { it.number("msptP50") }),
        Metric("MSPT p95", { it.number("msptP95") }),
        Metric("MSPT p99", { it.number("msptP99") }),
        Metric("MSPT max", { it.number("msptMax") }),
        Metric("TPS", { it.number("tps") }),
        Metric("Main thread CPU ms/tick", { it.number("mainThreadCpuMillis") / it.number("ticks") }, 3),
        Metric("Process CPU ms/tick", { it.number("processCpuMillis") / it.number("ticks") }, 3),
        Metric("GCs", { it.number("gcCount") }, 0),
        Metric("GC ms", { it.number("gcMillis") }, 0),
        Metric("Heap used MB", { it.number("heapUsedMb") }, 0),
        Metric("Errors logged", { it.number("errors") }, 0),
    )

    private val overheads = metrics.filter { it.label in setOf("MSPT mean", "MSPT p95", "Main thread CPU ms/tick", "Process CPU ms/tick") }

    /** What each kind of cell delivers into its barrels. */
    private val work = listOf(
        "ChestLink (cobblestone)" to "COBBLESTONE",
        "AutoCraft (torches)" to "TORCH",
        "Filter, allowed (andesite)" to "ANDESITE",
        "Filter, denied (dirt)" to "DIRT",
    )

    fun markdown(): String = buildString {
        appendLine("# ChestsPlusPlus benchmark")
        appendLine()
        appendLine(run)
        appendLine()
        appendLine("## Results")
        appendLine()
        table(listOf("Setup") + metrics.map { it.label }, results.keys.map { name -> listOf(name) + metrics.map { format(median(name, it.value), it.decimals) } })
        overheadSection()
        appendLine()
        appendLine("## Work done")
        appendLine()
        appendLine("Items that reached the barrels while measuring. Without the plugin, nothing goes through ChestLinks or AutoCraft and filters let everything through.")
        appendLine()
        table(listOf("Setup") + work.map { it.first }, results.keys.map { name -> listOf(name) + work.map { (_, item) -> format(median(name) { it.moved(item) }, 0) } })
    }

    private fun StringBuilder.overheadSection() {
        val pairs = listOf("v2", "v3").filter { it in results && "$it-baseline" in results }
        if (pairs.isEmpty()) return
        appendLine()
        appendLine("## Plugin overhead")
        appendLine()
        appendLine("Each plugin minus the server without it, on the same Minecraft version. Lower is better.")
        appendLine()
        val rows = pairs.map { version -> listOf(version) + overheads.map { format(overhead(version, it), it.decimals, signed = true) } }.toMutableList()
        if (pairs.size == 2) rows.add(listOf("v3 / v2") + overheads.map { ratio(overhead("v3", it), overhead("v2", it)) })
        table(listOf("Plugin") + overheads.map { it.label }, rows)
    }

    private fun overhead(version: String, metric: Metric) = median(version, metric.value) - median("$version-baseline", metric.value)

    private fun median(setup: String, value: (Map<String, Any>) -> Double): Double {
        val values = results.getValue(setup).map(value).sorted()
        return if (values.size % 2 == 1) values[values.size / 2] else (values[values.size / 2 - 1] + values[values.size / 2]) / 2
    }

    private fun StringBuilder.table(header: List<String>, rows: List<List<String>>) {
        appendLine(header.joinToString(" | ", "| ", " |"))
        appendLine(header.joinToString(" | ", "| ", " |") { if (it == header.first()) "---" else "---:" })
        rows.forEach { appendLine(it.joinToString(" | ", "| ", " |")) }
    }

    private fun format(value: Double, decimals: Int, signed: Boolean = false) = (if (signed && value >= 0) "+" else "") + "%.${decimals}f".format(value)

    /** Below 1 means v3 costs less than v2. Meaningless when v2's overhead is within noise of zero. */
    private fun ratio(v3: Double, v2: Double) = if (v2 <= 0) "n/a" else "%.2fx".format(v3 / v2)

    private fun Map<String, Any>.number(key: String) = (get(key) as Number).toDouble()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.moved(item: String) = ((get("itemsMoved") as Map<String, Number>)[item] ?: 0).toDouble()
}
