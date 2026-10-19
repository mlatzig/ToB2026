package de.latzig.game.tob.client.logic.frontend;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * One of the original's 18 hard-coded demo scripts ({@code w00169E}, 16 bytes per demo, {@code S:907-1023}; report
 * 17): each byte's bits 0-2 are a command, 1 forward, 2 back or lift, 3 turn left, 4 turn right, 5 fire (the codes of
 * a spider program, fed through the key handler {@code L00A062}) or 6 wait 16 frames; a 0 byte ends the script. Bits
 * 6-7 drive the original's scripted camera dolly: the menu background replaces it by ToB 2009's orbit (Phase 5a spec
 * decision 13), the interludes behind the result window use it (Phase 5c-2 decision 8, {@link ChaseCamera}).
 */
public record DemoScript(int demo, String title, byte[] bytes) {

    public static final int FORWARD = 1;
    public static final int BACK = 2;
    public static final int TURN_LEFT = 3;
    public static final int TURN_RIGHT = 4;
    public static final int FIRE = 5;
    public static final int WAIT = 6;
    /** Frames of the wait code ({@code $10(a0) = 16}, {@code S:20430-20433}). */
    public static final int WAIT_FRAMES = 16;

    public DemoScript {
        bytes = bytes.clone();
    }

    /** A script from the report's hex notation, e.g. {@code "04 C1 C1 01 00"}. */
    public static DemoScript of(int demo, String title, String hex) {
        return new DemoScript(demo, title, HexFormat.ofDelimiter(" ").parseHex(hex));
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /** The command codes (bits 0-2) up to the terminating 0. */
    public List<Integer> commands() {
        List<Integer> codes = new ArrayList<>();
        for (byte b : bytes) {
            if (b == 0) {
                break;
            }
            codes.add(b & 7);
        }
        return codes;
    }
}
