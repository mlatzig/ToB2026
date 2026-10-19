package de.latzig.game.tob.client.logic.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CreditsTickerTest {

    @Test
    void typesOneCharacterPerTwentiethSecondUpperCasedAndHoldsTheLineFiveSeconds() {
        CreditsTicker ticker = new CreditsTicker("Ab\n\tCd\n\t");

        ticker.advance(0.0);
        assertEquals("A", ticker.line(), "the first character at once");
        ticker.advance(0.05);
        assertEquals("AB", ticker.line());
        ticker.advance(0.05);
        assertEquals("AB", ticker.line(), "the line feed holds the line");
        ticker.advance(4.9);
        assertEquals("AB", ticker.line());
        ticker.advance(0.1);
        assertEquals("", ticker.line(), "the tab clears it after 5 s");
        ticker.advance(0.05);
        assertEquals("C", ticker.line());
    }

    @Test
    void theCursorBlinksTwiceASecondAndTheTextWraps() {
        CreditsTicker ticker = new CreditsTicker("X\n\t");
        ticker.advance(0.0);

        assertEquals("X", ticker.shown(0.25));
        assertEquals("X" + CreditsTicker.CURSOR, ticker.shown(0.75));
        ticker.advance(0.05);
        ticker.advance(5.0);
        ticker.advance(0.05);
        assertEquals("X", ticker.line(), "wrapped to the start");
    }
}
