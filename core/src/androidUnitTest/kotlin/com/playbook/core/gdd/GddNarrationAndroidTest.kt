package com.playbook.core.gdd

import kotlin.test.Test

class GddNarrationAndroidTest {

    @Test
    fun completeNarrationIncludesProjectAndSections() {
        verifyGddNarrationComplete()
    }

    @Test
    fun emptyAndTitleOnlyNarrations() {
        verifyGddNarrationEmptyAndTitleOnly()
    }

    @Test
    fun withoutProjectKeepsNoteBodies() {
        verifyGddNarrationWithoutProjectKeepsNotes()
    }
}
