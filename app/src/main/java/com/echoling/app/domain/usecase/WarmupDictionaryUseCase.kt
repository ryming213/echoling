package com.echoling.app.domain.usecase

import com.echoling.app.domain.repository.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background-warms the dictionary's merged-lookup map. Used by
 * `ReciteViewModel` right after the picker categories finish loading
 * so the heavy 12 MB parse happens while the user is reading the
 * category cards, not while they're tapping one.
 *
 * Idempotent — see [DictionaryRepository.warmupAll].
 */
@Singleton
class WarmupDictionaryUseCase @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
) {
    suspend operator fun invoke() = dictionaryRepository.warmupAll()
}