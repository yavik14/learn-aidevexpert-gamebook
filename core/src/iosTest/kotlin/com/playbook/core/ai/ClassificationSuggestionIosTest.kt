package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ClassificationSuggestionIosTest {

    @Test
    fun fakeIsDeterministicAndUsesCatalogGenre() = runTest {
        verifyClassificationSuggestionFakeDeterministic()
    }

    @Test
    fun normalizesCatalogCustomBlankAndDuplicates() = runTest {
        verifyClassificationSuggestionNormalization()
    }

    @Test
    fun unusableSuggestionIsEmpty() = runTest {
        verifyClassificationSuggestionEmpty()
    }

    @Test
    fun failureAndTimeoutAreReported() = runTest {
        verifyClassificationSuggestionFailureAndTimeout()
    }

    @Test
    fun applyReplacesTrackAndMergesGenres() {
        verifyClassificationSuggestionApply()
    }

    @Test
    fun blankBodyDoesNotCallClient() = runTest {
        verifyClassificationSuggestionBlankBodyDoesNotCallClient()
    }
}
