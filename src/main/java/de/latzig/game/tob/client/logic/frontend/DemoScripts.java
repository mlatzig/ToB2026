package de.latzig.game.tob.client.logic.frontend;

import java.util.List;

/**
 * The original's 18 demo scripts (report 17 table, {@code S:907-1023}), index = the demo tower's index in
 * {@code demo.json}: 0-5 success interludes, 6-11 failure interludes, 12-17 the Demo Sequence. The menu background
 * plays the Demo Sequence (Phase 5a spec decision 13) in the original's order: it starts with demo 17 (the start index
 * 0 is clamped to 17, {@code S:20329-20334}) and then runs 12, 13, ... 17, 12, ...
 */
public final class DemoScripts {

    public static final List<DemoScript> ALL = List.of(
            DemoScript.of(0, "Zapper Demo", "04 C1 C1 01 01 03 05 04 01 41 41 00 00 00 00 00"),
            DemoScript.of(1, "Pusher Demo", "04 01 01 01 03 41 44 01 04 05 06 01 03 C1 C1 00"),
            DemoScript.of(2, "Grabber Demo", "04 C1 C1 01 01 03 05 46 41 04 05 06 01 01 01 00"),
            DemoScript.of(3, "Zapper + Lifts", "04 41 41 01 02 01 01 02 C1 C1 00 00 00 00 00 00"),
            DemoScript.of(4, "Pusher and Glass Blocks", "04 05 46 05 41 05 01 05 01 03 C1 04 01 01 01 00"),
            DemoScript.of(5, "Grabber + Forcers", "46 05 04 01 01 02 01 03 05 04 01 01 02 C1 01 00"),
            DemoScript.of(6, "Zapper Dies 2", "04 C1 01 01 00 00 00 00 00 00 00 00 00 00 00 00"),
            DemoScript.of(7, "Pusher Dies 2", "44 45 41 45 41 00 00 00 00 00 00 00 00 00 00 00"),
            DemoScript.of(8, "Grabber Dies 2", "04 01 01 01 01 02 03 00 00 00 00 00 00 00 00 00"),
            DemoScript.of(9, "Zapper Dies 1", "04 01 01 03 05 04 01 01 01 00 00 00 00 00 00 00"),
            DemoScript.of(10, "Pusher Dies 1", "04 01 C1 03 05 00 00 00 00 00 00 00 00 00 00 00"),
            DemoScript.of(11, "Grabber Dies 1", "04 41 41 01 03 05 06 06 00 00 00 00 00 00 00 00"),
            DemoScript.of(12, "Zapper Demo 3", "04 41 01 01 03 05 04 C1 C1 03 C1 04 01 01 00 00"),
            DemoScript.of(13, "Pusher and Mines", "41 04 05 41 01 05 C1 C1 C1 01 01 01 00 00 00 00"),
            DemoScript.of(14, "Grabber + Mines Demo", "05 04 01 01 01 C1 03 05 04 C1 03 05 04 00 00 00"),
            DemoScript.of(15, "Zapper Demo", "04 01 C1 03 05 04 01 01 01 03 05 04 01 01 00 00"),
            DemoScript.of(16, "Pusher Demo", "04 05 01 03 05 06 01 04 C1 C1 01 01 01 01 00 00"),
            DemoScript.of(17, "Grabber Demo", "41 42 05 04 05 04 02 C1 03 05 C1 C1 01 01 01 00"));

    /** The Demo Sequence in the original's order (demo indices). */
    public static final List<Integer> SEQUENCE = List.of(17, 12, 13, 14, 15, 16);

    private DemoScripts() {
    }

    public static DemoScript script(int demo) {
        return ALL.get(demo);
    }
}
