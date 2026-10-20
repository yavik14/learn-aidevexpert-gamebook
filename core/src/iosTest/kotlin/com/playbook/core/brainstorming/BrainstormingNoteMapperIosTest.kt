package com.playbook.core.brainstorming

import kotlin.test.Test

class BrainstormingNoteMapperIosTest {
    @Test
    fun mapsEveryStep() {
        verifyBrainstormingNoteMapper()
    }

    @Test
    fun detectsDuplicatesWithNormalizedGenres() {
        verifyBrainstormingNoteDuplicate()
    }
}
