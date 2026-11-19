package ir.wazhebox.domain.model

/** Onboarding level, used to choose which starter words are unlocked (PRD §18.5). */
enum class Level(val seedRank: Int) {
    BEGINNER(0),
    INTERMEDIATE(1),
    ADVANCED(2),
    ;

    companion object {
        fun fromName(name: String?): Level =
            entries.firstOrNull { it.name == name } ?: BEGINNER
    }
}
