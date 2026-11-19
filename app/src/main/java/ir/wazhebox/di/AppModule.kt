package ir.wazhebox.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.wazhebox.core.time.StudyClock
import ir.wazhebox.core.time.SystemStudyClock
import ir.wazhebox.data.db.CardDao
import ir.wazhebox.data.db.DeckDao
import ir.wazhebox.data.db.ReviewDao
import ir.wazhebox.data.db.StatsDao
import ir.wazhebox.data.db.TagDao
import ir.wazhebox.data.db.WazheBoxDatabase
import ir.wazhebox.data.db.WordDao
import ir.wazhebox.data.repo.DeckRepositoryImpl
import ir.wazhebox.data.repo.SeedRepositoryImpl
import ir.wazhebox.data.repo.StatsRepositoryImpl
import ir.wazhebox.data.repo.StudyRepositoryImpl
import ir.wazhebox.data.repo.WordRepositoryImpl
import ir.wazhebox.domain.repo.DeckRepository
import ir.wazhebox.domain.repo.SeedRepository
import ir.wazhebox.domain.repo.StatsRepository
import ir.wazhebox.domain.repo.StudyRepository
import ir.wazhebox.domain.repo.WordRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WazheBoxDatabase =
        Room.databaseBuilder(context, WazheBoxDatabase::class.java, WazheBoxDatabase.NAME)
            .build()

    @Provides fun provideDeckDao(db: WazheBoxDatabase): DeckDao = db.deckDao()
    @Provides fun provideWordDao(db: WazheBoxDatabase): WordDao = db.wordDao()
    @Provides fun provideCardDao(db: WazheBoxDatabase): CardDao = db.cardDao()
    @Provides fun provideReviewDao(db: WazheBoxDatabase): ReviewDao = db.reviewDao()
    @Provides fun provideTagDao(db: WazheBoxDatabase): TagDao = db.tagDao()
    @Provides fun provideStatsDao(db: WazheBoxDatabase): StatsDao = db.statsDao()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): StudyClock = SystemStudyClock()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @dagger.Binds
    @Singleton
    abstract fun bindWordRepository(impl: WordRepositoryImpl): WordRepository

    @dagger.Binds
    @Singleton
    abstract fun bindStudyRepository(impl: StudyRepositoryImpl): StudyRepository

    @dagger.Binds
    @Singleton
    abstract fun bindStatsRepository(impl: StatsRepositoryImpl): StatsRepository

    @dagger.Binds
    @Singleton
    abstract fun bindDeckRepository(impl: DeckRepositoryImpl): DeckRepository

    @dagger.Binds
    @Singleton
    abstract fun bindSeedRepository(impl: SeedRepositoryImpl): SeedRepository

    @dagger.Binds
    @Singleton
    abstract fun bindDataTransferRepository(
        impl: ir.wazhebox.data.io.DataTransferRepositoryImpl,
    ): ir.wazhebox.data.io.DataTransferRepository
}
