package com.example.srttranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {
    private val sample = """1
00:00:01,000 --> 00:00:03,000
Hello
world

2
00:00:04,000 --> 00:00:05,000
Bye
"""
    @Test fun parsesAndRendersWithoutChangingTiming() { val items = SrtParser.parse(sample); assertEquals(2, items.size); assertEquals("Hello\nworld", items[0].text); assertTrue(SrtParser.render(items).contains("00:00:01,000 --> 00:00:03,000")) }
    @Test fun splitsIntoThreeOrderedParts() { val items = (1..9).map { Subtitle(it, "00:00:00,000 --> 00:00:01,000", "x") }; val parts = SrtParser.splitThree(items); assertEquals(3, parts.size); assertEquals((1..9).toList(), parts.flatten().map { it.number }) }
}
