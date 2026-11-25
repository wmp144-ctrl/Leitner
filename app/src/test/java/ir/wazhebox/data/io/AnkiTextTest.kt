package ir.wazhebox.data.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnkiTextTest {

    @Test
    fun `strips html tags and keeps line breaks`() {
        val text = AnkiText.stripHtml("house<br>خانه<div>ساختمان</div>")
        assertThat(text).isEqualTo("house\nخانه\nساختمان")
    }

    @Test
    fun `removes sound tags and decodes entities`() {
        val text = AnkiText.stripHtml("[sound:house.mp3] house &amp; home&nbsp;")
        assertThat(text).isEqualTo("house & home")
    }

    @Test
    fun `detects cloze markup and keeps the answer`() {
        val front = "I live in a {{c1::house::building}} near the park."
        assertThat(AnkiText.isCloze(front)).isTrue()
        assertThat(AnkiText.clozeAnswers(front)).containsExactly("house")
        assertThat(AnkiText.renderCloze(front)).isEqualTo("I live in a house near the park.")
    }

    @Test
    fun `plain notes are not cloze`() {
        val front = "house"
        assertThat(AnkiText.isCloze(front)).isFalse()
        assertThat(AnkiText.renderCloze(front)).isEqualTo("house")
    }

    @Test
    fun `fields are split on the unit separator`() {
        val fields = AnkiText.splitFields("house\u001Fخانه\u001Fextra")
        assertThat(fields).containsExactly("house", "خانه", "extra").inOrder()
    }

    @Test
    fun `tags are trimmed and deduplicated`() {
        assertThat(AnkiText.parseTags("  home house home  ")).containsExactly("home", "house")
        assertThat(AnkiText.parseTags(null)).isEmpty()
    }

    @Test
    fun `only the first line of a field is the term`() {
        assertThat(AnkiText.firstLine("house\nخانه")).isEqualTo("house")
    }
}
