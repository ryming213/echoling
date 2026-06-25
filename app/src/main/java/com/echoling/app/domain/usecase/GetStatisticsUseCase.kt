package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.LearningProgressRepository
import com.echoling.app.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

data class StatisticsData(
    val progressList: List<LearningProgress>,
    val words: List<Word>
)

@Singleton
class GetStatisticsUseCase @Inject constructor(
    private val progressRepository: LearningProgressRepository,
    private val wordRepository: WordRepository
) {
    fun getStatisticsFlow(): Flow<StatisticsData> = combine(
        progressRepository.getAllProgress(),
        wordRepository.getAllWords()
    ) { progressList, words ->
        StatisticsData(progressList, words)
    }
}
