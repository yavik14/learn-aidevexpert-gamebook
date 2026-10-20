package com.playbook.core.gdd

import kotlin.test.Test

class GddNarrationIosTest {

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
