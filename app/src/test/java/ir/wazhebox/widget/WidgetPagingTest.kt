package ir.wazhebox.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetPagingTest {

    private fun words(count: Int) = List(count) { WidgetWord(term = "word${it + 1}", meaning = "معنی${it + 1}") }

    @Test
    fun `a page holds five words`() {
        assertThat(WidgetPaging.slice(words(12), page = 0).map { it.term })
            .containsExactly("word1", "word2", "word3", "word4", "word5")
            .inOrder()
    }

    @Test
    fun `the next page continues where the previous one stopped`() {
        val deck = words(12)
        assertThat(WidgetPaging.slice(deck, page = 0).map { it.term }).containsExactly("word1", "word2", "word3", "word4", "word5").inOrder()
        assertThat(WidgetPaging.slice(deck, page = 1).map { it.term }).containsExactly("word6", "word7", "word8", "word9", "word10").inOrder()
        assertThat(WidgetPaging.slice(deck, page = 2).map { it.term }).containsExactly("word11", "word12", "word1", "word2", "word3").inOrder()
    }

    @Test
    fun `page count covers a partial last page`() {
        assertThat(WidgetPaging.pageCount(0)).isEqualTo(1)
        assertThat(WidgetPaging.pageCount(5)).isEqualTo(1)
        assertThat(WidgetPaging.pageCount(6)).isEqualTo(2)
        assertThat(WidgetPaging.pageCount(900)).isEqualTo(180)
    }

    @Test
    fun `each new day advances the page by one`() {
        val day = 20_560
        val pageToday = WidgetPaging.pageForDay(day, pageCount = 180)
        val pageTomorrow = WidgetPaging.pageForDay(day + 1, pageCount = 180)
        assertThat(pageTomorrow).isEqualTo((pageToday + 1) % 180)
    }

    @Test
    fun `a one page deck never moves`() {
        assertThat(WidgetPaging.pageForDay(20_560, pageCount = 1)).isEqualTo(0)
        assertThat(WidgetPaging.pageForDay(20_999, pageCount = 1)).isEqualTo(0)
    }

    @Test
    fun `a manual page wins on the day it was chosen`() {
        val resolved = WidgetPaging.resolvePage(
            natural = 3,
            pageCount = 10,
            overrideDay = 20_560,
            overridePage = 7,
            today = 20_560,
        )
        assertThat(resolved).isEqualTo(7)
    }

    @Test
    fun `yesterday's manual page is ignored today`() {
        val resolved = WidgetPaging.resolvePage(
            natural = 3,
            pageCount = 10,
            overrideDay = 20_559,
            overridePage = 7,
            today = 20_560,
        )
        assertThat(resolved).isEqualTo(3)
    }

    @Test
    fun `a stale manual page is clamped to the deck`() {
        val resolved = WidgetPaging.resolvePage(
            natural = 0,
            pageCount = 3,
            overrideDay = 20_560,
            overridePage = 42,
            today = 20_560,
        )
        assertThat(resolved).isEqualTo(2)
    }

    @Test
    fun `a small deck shows each word once`() {
        val deck = words(3)
        val page = WidgetPaging.slice(deck, page = 1)
        assertThat(page).hasSize(3)
        assertThat(page.map { it.term }).containsExactly("word1", "word2", "word3")
    }
}
