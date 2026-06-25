package com.echoling.app.speech

import org.junit.Assert.*
import org.junit.Test

class WordMatcherTest {

    @Test
    fun `exact match passes`() {
        val result = WordMatcher.match("I love you", "I love you")
        assertTrue(result.passed)
        assertEquals("ok", result.reason)
    }

    @Test
    fun `case insensitive match passes`() {
        val result = WordMatcher.match("I LOVE YOU", "i love you")
        assertTrue(result.passed)
    }

    @Test
    fun `punctuation removed passes`() {
        val result = WordMatcher.match("Hello, world!", "hello world")
        assertTrue(result.passed)
    }

    @Test
    fun `apostrophe preserved in contraction`() {
        val result = WordMatcher.match("don't stop", "don't stop")
        assertTrue(result.passed)
    }

    @Test
    fun `dont vs dont fails due to apostrophe`() {
        val result = WordMatcher.match("don't", "dont")
        assertFalse(result.passed)
        assertEquals("wrong_word", result.reason)
    }

    @Test
    fun `missing word fails`() {
        val result = WordMatcher.match("I love you", "I love")
        assertFalse(result.passed)
        assertEquals("missing_word", result.reason)
    }

    @Test
    fun `extra word fails`() {
        val result = WordMatcher.match("I love you", "I love you too")
        assertFalse(result.passed)
        assertEquals("extra_word", result.reason)
    }

    @Test
    fun `wrong word fails`() {
        val result = WordMatcher.match("I love you", "I love her")
        assertFalse(result.passed)
        assertEquals("wrong_word", result.reason)
    }

    @Test
    fun `empty transcription fails`() {
        val result = WordMatcher.match("I love you", "")
        assertFalse(result.passed)
        assertEquals("empty_transcription", result.reason)
    }

    @Test
    fun `whitespace only transcription fails`() {
        val result = WordMatcher.match("I love you", "   ")
        assertFalse(result.passed)
        assertEquals("empty_transcription", result.reason)
    }

    @Test
    fun `numbers preserved`() {
        val result = WordMatcher.match("Lesson 1 is done", "lesson 1 is done")
        assertTrue(result.passed)
    }
}