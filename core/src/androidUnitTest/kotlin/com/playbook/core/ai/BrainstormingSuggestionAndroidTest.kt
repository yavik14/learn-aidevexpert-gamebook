package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class BrainstormingSuggestionAndroidTest {

    @Test
    fun happyPathUsesPromptAndContext() = runTest {
        verifyBrainstormingSuggestionHappyPath()
    }

    @Test
    fun fakeReturnsTwoParseableIdeas() = runTest {
        verifyBrainstormingSuggestionFake()
    }

    @Test
    fun partialAndInvalidResponses() = runTest {
        verifyBrainstormingSuggestionPartialAndInvalid()
    }

    @Test
    fun runtimeFailureIsReported() = runTest {
        verifyBrainstormingSuggestionFailure()
    }

    @Test
    fun deduplicatesAndFiltersDraftValues() = runTest {
        verifyBrainstormingSuggestionDedupeAndFilter()
    }

    @Test
    fun mechanicsParsingAndTruncation() = runTest {
        verifyBrainstormingSuggestionMechanicsAndTruncation()
    }

    @Test
    fun contextContainsCatalogAndNoInternalData() {
        verifyBrainstormingSuggestionContext()
    }
}
