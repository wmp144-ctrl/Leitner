package ir.wazhebox.data.seed

import com.google.common.truth.Truth.assertThat
import ir.wazhebox.data.repo.SeedDeck
import java.io.File
import org.junit.Test

/** The bundled starter deck must be complete and parse cleanly (PRD §13.4). */
class SeedDeckTest {

    private val csv: String by lazy {
        val candidates = listOf(
            File("src/main/assets/starter_deck.csv"),
            File("app/src/main/assets/starter_deck.csv"),
        )
        candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }

    @Test
    fun `starter deck reaches the five hundred entries the PRD asks for`() {
        assertThat(SeedDeck.parse(csv).size).isAtLeast(500)
    }

    @Test
    fun `every entry has term meaning and example`() {
        val entries = SeedDeck.parse(csv)
        assertThat(entries.all { it.term.isNotBlank() }).isTrue()
        assertThat(entries.all { it.meaningFa.isNotBlank() }).isTrue()
        assertThat(entries.all { !it.exampleEn.isNullOrBlank() }).isTrue()
        assertThat(entries.all { !it.exampleFa.isNullOrBlank() }).isTrue()
    }

    @Test
    fun `terms are unique after normalization`() {
        val terms = SeedDeck.parse(csv).map { it.term.lowercase().trim() }
        assertThat(terms).containsNoDuplicates()
    }

    @Test
    fun `levels cover beginner to intermediate`() {
        val levels = SeedDeck.parse(csv).map { it.level }.toSet()
        assertThat(levels).containsAtLeast("A1", "A2", "B1")
    }

    @Test
    fun `examples are short enough for a card`() {
        val long = SeedDeck.parse(csv).filter { it.exampleEn!!.split(' ').size > 16 }
        assertThat(long).isEmpty()
    }
}
