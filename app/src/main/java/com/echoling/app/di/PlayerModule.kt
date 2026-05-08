package com.echoling.app.di

import com.echoling.app.player.AudioPlayer
import com.echoling.app.player.subtitle.AssParser
import com.echoling.app.player.subtitle.SrtParser
import com.echoling.app.player.subtitle.SubtitleParserFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideSrtParser(): SrtParser = SrtParser()

    @Provides
    @Singleton
    fun provideAssParser(): AssParser = AssParser()

    @Provides
    @Singleton
    fun provideSubtitleParserFactory(
        srtParser: SrtParser,
        assParser: AssParser
    ): SubtitleParserFactory = SubtitleParserFactory(srtParser, assParser)
}
