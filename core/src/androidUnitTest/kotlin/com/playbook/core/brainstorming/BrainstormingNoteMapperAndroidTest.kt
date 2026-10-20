package com.playbook.core.brainstorming

import kotlin.test.Test

class BrainstormingNoteMapperAndroidTest {
    @Test
    fun mapsEveryStep() {
        verifyBrainstormingNoteMapper()
    }

    @Test
    fun detectsDuplicatesWithNormalizedGenres() {
        verifyBrainstormingNoteDuplicate()
    }
}
