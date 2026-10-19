package de.latzig.game.tob.client.logic.audio;

import java.util.Map;
import java.util.TreeMap;

import de.latzig.game.tob.core.model.SoundCatalog;
import de.latzig.game.tob.core.model.SoundDetail;
import de.latzig.game.tob.core.model.SoundEntry;

/** ToB 2005's 24 sounds with {@code sounds.json}'s flags and detail levels (distances simplified), for tests. */
public final class SoundTestBank {

    private SoundTestBank() {
    }

    /** The bank of {@link #catalog()}. */
    public static SoundBank bank() {
        return SoundBank.of(catalog());
    }

    /** {@code sounds.json}'s ids, loop and positional flags and detail levels. */
    public static SoundCatalog catalog() {
        Map<String, SoundEntry> sounds = new TreeMap<>();
        put(sounds, "0090", false, false, SoundDetail.MED);
        put(sounds, "0091", true, false, SoundDetail.MED);
        put(sounds, "0092", false, true, SoundDetail.LOW);
        put(sounds, "0900", false, true, SoundDetail.MED);
        put(sounds, "0901", false, true, SoundDetail.HIGH);
        put(sounds, "0902", false, true, SoundDetail.LOW);
        put(sounds, "0903", false, true, SoundDetail.HIGH);
        put(sounds, "0904", false, true, SoundDetail.MED);
        put(sounds, "1000", false, true, SoundDetail.MED);
        put(sounds, "1900", true, true, SoundDetail.LOW);
        put(sounds, "1901", false, true, SoundDetail.LOW);
        put(sounds, "2000", false, true, SoundDetail.LOW);
        put(sounds, "2500", false, true, SoundDetail.MED);
        put(sounds, "3200", false, true, SoundDetail.HIGH);
        put(sounds, "3300", false, true, SoundDetail.HIGH);
        put(sounds, "4000", false, true, SoundDetail.HIGH);
        put(sounds, "4100", false, true, SoundDetail.HIGH);
        put(sounds, "4200", true, true, SoundDetail.HIGH);
        put(sounds, "5400", false, true, SoundDetail.MED);
        put(sounds, "5500", true, true, SoundDetail.HIGH);
        put(sounds, "5600", true, true, SoundDetail.HIGH);
        put(sounds, "5800", true, true, SoundDetail.HIGH);
        put(sounds, "5900", false, true, SoundDetail.HIGH);
        put(sounds, "6100", true, true, SoundDetail.HIGH);
        return new SoundCatalog(sounds);
    }

    private static void put(Map<String, SoundEntry> sounds, String id, boolean loop, boolean positional,
            SoundDetail detail) {
        sounds.put(id, new SoundEntry("sounds/" + id + ".wav", loop, positional, positional ? 1.0 : 0.0,
                positional ? 32.0 : 0.0, detail, "test"));
    }
}
