package de.latzig.game.tob.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.latzig.game.tob.core.model.ObjectType;
import de.latzig.game.tob.core.model.TowerDefinition;
import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.RandomCommands;
import de.latzig.game.tob.core.sim.Recorder;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.Replay;
import de.latzig.game.tob.core.sim.SimEvent;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.Simulation;
import de.latzig.game.tob.core.sim.Snapshots;
import de.latzig.game.tob.core.sim.SpiderId;
import de.latzig.game.tob.core.sim.TestTowers;

class RecordingJsonTest {

    /** A recording in the exact shape the Phase 2 writer produced (before the camera commands of Phase 3b-1). */
    private static final String PHASE2_RECORDING = """
            {
              "format" : "tob2026-recording",
              "version" : 1,
              "tower" : "test-000",
              "options" : {
                "backwardsAllowed" : false
              },
              "length" : 6,
              "commands" : [
                {
                  "step" : 0,
                  "type" : "SelectCamera",
                  "camera" : "EAST"
                },
                {
                  "step" : 1,
                  "type" : "CameraEnterWriteMode",
                  "camera" : "EAST"
                },
                {
                  "step" : 2,
                  "type" : "CameraWrite",
                  "camera" : "EAST",
                  "programStep" : "LEFT"
                },
                {
                  "step" : 5,
                  "type" : "Forward",
                  "spider" : "ZAPPER"
                }
              ]
            }
            """;

    private final RecordingWriter writer = new RecordingWriter();
    private final RecordingReader reader = new RecordingReader();

    private static Recording everyCommand() {
        SpiderId z = SpiderId.ZAPPER;
        CameraId n = CameraId.NORTH;
        List<Command> commands = List.of(new Command.Forward(z), new Command.Back(SpiderId.PUSHER),
                new Command.TurnLeft(SpiderId.GRABBER), new Command.TurnRight(z), new Command.Fire(z),
                new Command.EnterWriteMode(z), new Command.Write(z, ProgramStep.FIRE), new Command.SetCursor(z, 7),
                new Command.Delete(z), new Command.Clear(z), new Command.Run(z), new Command.Stop(z),
                new Command.RunAll(), new Command.SelectSpider(SpiderId.PUSHER),
                new Command.SelectCamera(CameraId.EAST), new Command.CameraEnterWriteMode(CameraId.SOUTH),
                new Command.CameraWrite(CameraId.WEST, ProgramStep.BACK), new Command.CameraForward(n),
                new Command.CameraBack(n), new Command.CameraTurnLeft(n), new Command.CameraTurnRight(n),
                new Command.CameraFire(n), new Command.CameraSetCursor(CameraId.EAST, 6), new Command.CameraDelete(n),
                new Command.CameraClear(n), new Command.CameraZoomIn(n), new Command.CameraZoomOut(CameraId.SOUTH),
                new Command.Abandon(), new Command.Restart());
        List<Recording.Entry> entries = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            entries.add(new Recording.Entry(i * 2, commands.get(i)));
        }
        return new Recording("original-007", new SimOptions(true), 60, entries);
    }

    @Test
    void everyCommandTypeSurvivesTheRoundTrip() throws IOException {
        Recording recording = everyCommand();

        assertEquals(recording, reader.readFromString(writer.toJson(recording)));
    }

    @Test
    void writesTheDocumentedShapeWithLfOnly() throws IOException {
        String json = writer.toJson(everyCommand());

        assertTrue(json.contains("\"format\" : \"tob2026-recording\""));
        assertTrue(json.contains("\"version\" : 2"));
        assertFalse(json.contains("\"outcome\""), "no metadata, no metadata fields");
        assertTrue(json.contains("\"tower\" : \"original-007\""));
        assertTrue(json.contains("\"backwardsAllowed\" : true"));
        assertTrue(json.contains("\"length\" : 60"));
        assertTrue(json.contains("\"type\" : \"Write\""));
        assertTrue(json.contains("\"programStep\" : \"FIRE\""));
        assertTrue(json.contains("\"cursor\" : 7"));
        assertTrue(json.contains("\"camera\" : \"WEST\""));
        assertTrue(json.contains("\"type\" : \"CameraZoomOut\",\n      \"camera\" : \"SOUTH\""));
        assertTrue(json.contains(
                "\"type\" : \"CameraSetCursor\",\n      \"camera\" : \"EAST\",\n      \"cursor\" : 6"));
        assertFalse(json.contains("\r"));
        assertTrue(json.endsWith("\n"));
    }

    @Test
    void aRecordingWrittenBeforeTheCameraCommandsReadsAndWritesUnchanged() throws IOException {
        Recording read = reader.readFromString(PHASE2_RECORDING);

        assertEquals(new Recording("test-000", SimOptions.DEFAULT, 6, List.of(
                new Recording.Entry(0, new Command.SelectCamera(CameraId.EAST)),
                new Recording.Entry(1, new Command.CameraEnterWriteMode(CameraId.EAST)),
                new Recording.Entry(2, new Command.CameraWrite(CameraId.EAST, ProgramStep.LEFT)),
                new Recording.Entry(5, new Command.Forward(SpiderId.ZAPPER)))), read);
        assertEquals(PHASE2_RECORDING.replace("\"version\" : 1", "\"version\" : 2"), writer.toJson(read),
                "written as version 2 without metadata: the shape of the older commands is unchanged");
    }

    @Test
    void rejectsForeignFormatsAndUnknownCommands() throws IOException {
        String json = writer.toJson(everyCommand());

        IOException format = assertThrows(IOException.class,
                () -> reader.readFromString(json.replace("tob2026-recording", "tob2026-towerlib")));
        assertTrue(format.getMessage().contains("unsupported format"), format.getMessage());
        IOException unknown = assertThrows(IOException.class,
                () -> reader.readFromString(json.replace("\"type\" : \"Fire\"", "\"type\" : \"Jump\"")));
        assertTrue(unknown.getMessage().contains("Jump"), unknown.getMessage());
        IOException future = assertThrows(IOException.class,
                () -> reader.readFromString(json.replace("\"version\" : 2", "\"version\" : 3")));
        assertTrue(future.getMessage().contains("version 3"), future.getMessage());
    }

    @Test
    void metadataSurvivesTheRoundTripAndIsWrittenAfterTheLength() throws IOException {
        RecordingMeta meta = new RecordingMeta(RecordingMeta.Outcome.COMPLETED,
                Instant.parse("2026-10-09T18:30:05Z"), 1234,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", "ToB 2026 test");
        Recording recording = everyCommand().withMeta(meta);

        String json = writer.toJson(recording);

        assertEquals(recording, reader.readFromString(json));
        assertTrue(json.contains("\"length\" : 60,\n  \"outcome\" : \"completed\",\n  \"recordedAt\" : "
                + "\"2026-10-09T18:30:05Z\",\n  \"frames\" : 1234,\n  \"towerChecksum\" : \"0123"), json);
        assertTrue(json.contains("\"game\" : \"ToB 2026 test\",\n  \"commands\""), json);
        assertEquals(Optional.empty(), reader.readFromString(writer.toJson(everyCommand())).meta());
        IOException broken = assertThrows(IOException.class,
                () -> reader.readFromString(json.replace("\"frames\" : 1234,\n", "")));
        assertTrue(broken.getMessage().contains("frames"), broken.getMessage());
    }

    @Test
    void aRecordingFileReplaysToTheSameEvents(@TempDir Path dir) throws IOException {
        TowerDefinition tower = TestTowers.tower().level(0, "FFF", "FUF").level(1, "...", ".u.")
                .object(1, 0, 0, ObjectType.ZAP).object(0, 1, 0, ObjectType.PSH).build();
        Recorder recorder = new Recorder(Simulation.start(tower, SimOptions.DEFAULT));
        RandomCommands random = new RandomCommands(7);
        List<SimEvent> events = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            events.addAll(recorder.step(random.next()));
        }
        Path file = dir.resolve("sub/session.json");

        writer.write(recorder.recording(), file);
        Recording read = reader.read(file);
        List<SimEvent> replayed = new ArrayList<>();
        Replay.run(read, tower, replayed);

        assertEquals(recorder.recording(), read);
        assertEquals(events, replayed);
    }

    @Test
    void cameraMovesZoomsAndACameraProgramReplayExactlyFromAFile(@TempDir Path dir) throws IOException {
        TowerDefinition tower = TestTowers.tower().level(0, "FFF", "FFF").object(0, 0, 0, ObjectType.ZAP)
                .object(2, 0, 0, ObjectType.PSH).cameras(true).build();
        Recorder recorder = new Recorder(Simulation.start(tower, SimOptions.DEFAULT));
        CameraId w = CameraId.WEST;
        List<Command> script = List.of(new Command.SelectCamera(w), new Command.CameraForward(w),
                new Command.CameraTurnLeft(w), new Command.CameraZoomOut(w), new Command.CameraEnterWriteMode(w),
                new Command.CameraForward(w), new Command.CameraFire(w), new Command.CameraSetCursor(w, 1),
                new Command.SelectSpider(SpiderId.PUSHER), new Command.RunAll(), new Command.CameraZoomIn(w));
        List<SimEvent> events = new ArrayList<>();
        for (Command command : script) {
            events.addAll(recorder.step(List.of(command)));
            for (int i = 0; i < 8; i++) {
                events.addAll(recorder.step(List.of()));
            }
        }
        Path file = dir.resolve("cameras.json");

        writer.write(recorder.recording(), file);
        List<SimEvent> replayed = new ArrayList<>();
        Simulation replay = Replay.run(reader.read(file), tower, replayed);

        assertEquals(events, replayed);
        assertEquals(Snapshots.of(recorder.simulation()), Snapshots.of(replay), "including the camera poses");
        assertEquals(0x180, replay.camera(w).height());
        assertEquals(-0x80, replay.camera(w).offset());
        assertEquals(-3136, replay.camera(w).distance(), "out once, in once");
        assertTrue(events.contains(new SimEvent.Turned(0, 1, w.heading())), "the copy turned the pusher west");
    }
}
