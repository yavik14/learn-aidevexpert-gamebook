package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NoteContentSuggestionIosTest {

    @Test
    fun happyPathUsesPromptAndBoundedContext() = runTest {
        verifyNoteContentSuggestionHappyPath()
    }

    @Test
    fun fakeReturnsTwoParseableIdeasWithoutBreakingOtherBranches() = runTest {
        verifyNoteContentSuggestionFake()
    }

    @Test
    fun partialInvalidFailureAndCancellation() = runTest {
        verifyNoteContentSuggestionPartialInvalidAndFailure()
    }

    @Test
    fun deduplicatesBodyAndTruncatesTo200Chars() = runTest {
        verifyNoteContentSuggestionDedupeAndTruncation()
    }

    @Test
    fun emptyBodyUsesProjectContextAndNoProjectStillWorks() = runTest {
        verifyNoteContentSuggestionEmptyBodyAndNoProject()
    }

    @Test
    fun contextRespectsEntryAndValueLimits() = runTest {
        verifyNoteContentSuggestionContextLimits()
    }

    @Test
    fun parserHandlesRealMarkdownFencesAndNoise() = runTest {
        verifyNoteContentSuggestionParserFormat()
    }

    @Test
    fun appendHelperKeepsBlankLineAndPreservesBody() {
        verifyAppendNoteContentSuggestion()
    }
}
