package ir.wazhebox.data.io

/**
 * RFC 4180 CSV with the quirks Persian spreadsheets produce (PRD §14.1):
 * UTF-8 BOM, `,`/`;`/tab delimiters, CRLF or LF, quoted fields with commas.
 */
object CsvCodec {

    const val BOM = "\uFEFF"

    fun encodeField(value: String?): String {
        val text = value ?: return ""
        val needsQuotes = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }
        return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }

    fun encodeRow(cells: List<String?>): String = cells.joinToString(",") { encodeField(it) }

    fun buildDocument(rows: List<List<String?>>, withBom: Boolean = true): String {
        val body = rows.joinToString("\r\n") { encodeRow(it) }
        return (if (withBom) BOM else "") + body + "\r\n"
    }

    /** Detects the delimiter from the header line. */
    fun detectDelimiter(text: String): Char {
        val firstLine = text.removePrefix(BOM).lineSequence().firstOrNull().orEmpty()
        val counts = mapOf(',' to firstLine.count { it == ',' }, ';' to firstLine.count { it == ';' }, '\t' to firstLine.count { it == '\t' })
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /**
     * Parses a whole document into rows, keeping quoted newlines intact.
     * Returns pairs of (1-based line number, cells).
     */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<Pair<Int, List<String>>> {
        val content = text.removePrefix(BOM)
        val rows = mutableListOf<Pair<Int, List<String>>>()
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var line = 1
        var rowStartLine = 1
        var i = 0

        fun endCell() {
            cells += current.toString()
            current.clear()
        }

        fun endRow() {
            endCell()
            rows += rowStartLine to cells.toList()
            cells.clear()
            rowStartLine = line
        }

        while (i < content.length) {
            val char = content[i]
            when {
                inQuotes && char == '"' && i + 1 < content.length && content[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> endCell()
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && i + 1 < content.length && content[i + 1] == '\n') i++
                    endRow()
                    line++
                    rowStartLine = line
                }
                else -> current.append(char)
            }
            i++
        }
        if (current.isNotEmpty() || cells.isNotEmpty()) {
            endRow()
        }
        return rows
    }

    /**
     * Pipe tables (`| term | meaning | day |`) are what chat tools export when a
     * spreadsheet is pasted as Markdown. The first row is kept as data: the caller
     * decides from its cells whether it is a header.
     */
    fun isMarkdownTable(text: String): Boolean {
        val first = text.removePrefix(BOM)
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            .orEmpty()
        return first.startsWith("|") && first.count { it == '|' } >= 2
    }

    /** Parses a pipe table, trimming cells and skipping `| --- |` separator rows. */
    fun parseMarkdownTable(text: String): List<Pair<Int, List<String>>> {
        val rows = mutableListOf<Pair<Int, List<String>>>()
        text.removePrefix(BOM).lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || !line.contains('|')) return@forEachIndexed
            val cells = splitTableRow(line)
            if (cells.isEmpty()) return@forEachIndexed
            if (cells.all { it.isBlank() || isTableSeparator(it) }) return@forEachIndexed
            rows += (index + 1) to cells
        }
        return rows
    }

    private fun splitTableRow(line: String): List<String> {
        val body = line.removePrefix("|").removeSuffix("|")
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < body.length) {
            val char = body[i]
            when {
                char == '\\' && i + 1 < body.length && body[i + 1] == '|' -> {
                    current.append('|')
                    i++
                }
                char == '|' -> {
                    cells += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
            i++
        }
        cells += current.toString().trim()
        return cells
    }

    private fun isTableSeparator(cell: String): Boolean {
        val compact = cell.trim().trim(':').replace(" ", "")
        return compact.isNotEmpty() && compact.all { it == '-' || it == '=' }
    }

    data class MeaningBlob(val meaning: String, val exampleEn: String?, val exampleFa: String?)

    /**
     * A vocab export keeps the meaning, an English example and its translation in
     * one HTML cell: `آدرس، نشانی<br><br><i>Write it here.</i><br>آدرس را اینجا بنویس.`
     */
    fun splitMeaningBlob(cell: String): MeaningBlob {
        val italic = Regex("<i>(.*?)</i>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(cell)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val lines = cell
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</?i>"), "")
            .replace(Regex("(?i)</?(b|strong|em|span)[^>]*>"), "")
            .replace("&nbsp;", " ")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (italic == null) {
            return MeaningBlob(
                meaning = lines.firstOrNull().orEmpty(),
                exampleEn = lines.getOrNull(1),
                exampleFa = lines.getOrNull(2),
            )
        }
        val splitAt = lines.indexOfFirst { it.equals(italic, ignoreCase = true) }
        val meaningCells = if (splitAt >= 0) lines.take(splitAt) else lines.filterNot { it.equals(italic, true) }
        val tail = if (splitAt >= 0) lines.drop(splitAt + 1) else emptyList()
        return MeaningBlob(
            meaning = meaningCells.joinToString("، ").ifEmpty { cell.trim() },
            exampleEn = italic,
            exampleFa = tail.firstOrNull(),
        )
    }

    /** Column aliases: English header names and the Persian ones from §14.1. */
    val HEADER_ALIASES: Map<String, String> = mapOf(
        "term" to "term",
        "word" to "term",
        "english" to "term",
        "کلمه" to "term",
        "واژه" to "term",
        "لغت" to "term",
        "meaning_fa" to "meaning_fa",
        "meaning" to "meaning_fa",
        "persian" to "meaning_fa",
        "fa" to "meaning_fa",
        "معنی" to "meaning_fa",
        "ترجمه" to "meaning_fa",
        "pos" to "pos",
        "نوع" to "pos",
        "ipa" to "ipa",
        "تلفظ" to "ipa",
        "example_en" to "example_en",
        "example" to "example_en",
        "sentence" to "example_en",
        "example_sentence" to "example_en",
        "جمله" to "example_en",
        "example_fa" to "example_fa",
        "sentence_fa" to "example_fa",
        "example_translation" to "example_fa",
        "ترجمه جمله" to "example_fa",
        "tags" to "tags",
        "برچسب" to "tags",
        "برچسب‌ها" to "tags",
        "box" to "box",
        "جعبه" to "box",
        "due_at" to "due_at",
        "reps" to "reps",
        "lapses" to "lapses",
        "created_at" to "created_at",
        "level" to "level",
    )
}
