package com.playbook.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun greetReturnsMessage() {
        assertEquals("Playbook core ready", Greeting().greet())
    }
}
