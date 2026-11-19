package ir.wazhebox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import ir.wazhebox.domain.model.CardState
import ir.wazhebox.domain.model.Direction
import ir.wazhebox.domain.model.Verdict
import ir.wazhebox.domain.model.WordSource
import kotlinx.datetime.LocalDate

class Converters {

    @TypeConverter
    fun directionToString(value: Direction): String = value.name

    @TypeConverter
    fun stringToDirection(value: String): Direction = Direction.valueOf(value)

    @TypeConverter
    fun stateToString(value: CardState): String = value.name

    @TypeConverter
    fun stringToState(value: String): CardState = CardState.valueOf(value)

    @TypeConverter
    fun sourceToString(value: WordSource): String = value.name

    @TypeConverter
    fun stringToSource(value: String): WordSource =
        runCatching { WordSource.valueOf(value) }.getOrDefault(WordSource.MANUAL)

    @TypeConverter
    fun verdictToString(value: Verdict?): String? = value?.name

    @TypeConverter
    fun stringToVerdict(value: String?): Verdict? = value?.let { runCatching { Verdict.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun localDateToString(value: LocalDate): String = value.toString()

    @TypeConverter
    fun stringToLocalDate(value: String): LocalDate = LocalDate.parse(value)
}

/**
 * Single source of truth on the device (PRD §7.1). Version 1 ships with the
 * schema from §8.1; every later change needs a written migration — destructive
 * fallback is forbidden (§8.3).
 */
@Database(
    entities = [
        DeckEntity::class,
        WordEntity::class,
        CardEntity::class,
        ReviewEntity::class,
        TagEntity::class,
        WordTagEntity::class,
        DailyStatEntity::class,
    ],
    version = WazheBoxDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class WazheBoxDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun wordDao(): WordDao
    abstract fun cardDao(): CardDao
    abstract fun reviewDao(): ReviewDao
    abstract fun tagDao(): TagDao
    abstract fun statsDao(): StatsDao

    companion object {
        const val VERSION = 1
        const val NAME = "wazhebox.db"
    }
}
