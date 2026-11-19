package ir.wazhebox.data.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvCodecTest {

    @Test
    fun `field with comma is quoted`() {
        assertThat(CsvCodec.encodeField("a,b")).isEqualTo("\"a,b\"")
    }

    @Test
    fun `quotes are doubled`() {
        assertThat(CsvCodec.encodeField("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"")
    }

    @Test
    fun `document starts with a BOM and uses CRLF`() {
        val text = CsvCodec.buildDocument(listOf(listOf("a", "b")))
        assertThat(text).startsWith(CsvCodec.BOM)
        assertThat(text).contains("\r\n")
    }

    @Test
    fun `semicolon delimiter is detected`() {
        assertThat(CsvCodec.detectDelimiter("term;meaning\nx;y")).isEqualTo(';')
    }

    @Test
    fun `tab delimiter is detected`() {
        assertThat(CsvCodec.detectDelimiter("term\tmeaning\nx\ty")).isEqualTo('\t')
    }

    @Test
    fun `quoted comma does not split cells`() {
        val rows = CsvCodec.parse("a,\"b,c\",d\n")
        assertThat(rows.first().second).containsExactly("a", "b,c", "d").inOrder()
    }

    @Test
    fun `quoted newline stays inside the cell`() {
        val rows = CsvCodec.parse("a,\"line1\nline2\",c\n")
        assertThat(rows).hasSize(1)
        assertThat(rows.first().second[1]).isEqualTo("line1\nline2")
    }

    @Test
    fun `double quotes are unescaped`() {
        val rows = CsvCodec.parse("a,\"say \"\"hi\"\"\"\n")
        assertThat(rows.first().second[1]).isEqualTo("say \"hi\"")
    }

    @Test
    fun `bom is stripped and line numbers are tracked`() {
        val rows = CsvCodec.parse(CsvCodec.BOM + "a,b\nc,d\n")
        assertThat(rows.map { it.first }).containsExactly(1, 2).inOrder()
        assertThat(rows[0].second[0]).isEqualTo("a")
    }

    @Test
    fun `round trip preserves persian and zwnj`() {
        val text = CsvCodec.buildDocument(
            listOf(
                listOf("term", "meaning"),
                listOf("house", "خانه|منزل"),
                listOf("go", "می‌روم"),
            ),
        )
        val parsed = CsvCodec.parse(text)
        assertThat(parsed[1].second[1]).isEqualTo("خانه|منزل")
        assertThat(parsed[2].second[1]).isEqualTo("می‌روم")
    }

    @Test
    fun `header aliases map persian columns`() {
        assertThat(CsvCodec.HEADER_ALIASES["کلمه"]).isEqualTo("term")
        assertThat(CsvCodec.HEADER_ALIASES["معنی"]).isEqualTo("meaning_fa")
        assertThat(CsvCodec.HEADER_ALIASES["برچسب"]).isEqualTo("tags")
    }
}
