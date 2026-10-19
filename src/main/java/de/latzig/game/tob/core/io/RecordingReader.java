package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.latzig.game.tob.core.sim.CameraId;
import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.ProgramStep;
import de.latzig.game.tob.core.sim.Recording;
import de.latzig.game.tob.core.sim.RecordingMeta;
import de.latzig.game.tob.core.sim.SimOptions;
import de.latzig.game.tob.core.sim.SpiderId;

/**
 * Reads recordings written by {@link RecordingWriter}: versions 1 and 2. The optional metadata of version 2 is read
 * when its {@code outcome} field is present; then all five metadata fields are required.
 */
public final class RecordingReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public Recording read(Path file) throws IOException {
        return readFromString(Files.readString(file, StandardCharsets.UTF_8));
    }

    public Recording readFromString(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        try {
            String format = text(root, "format");
            int version = integer(root, "version");
            if (!RecordingWriter.FORMAT.equals(format) || version < 1 || version > RecordingWriter.VERSION) {
                throw new IllegalArgumentException("unsupported format " + format + " version " + version
                        + " (expected " + RecordingWriter.FORMAT + " 1 to " + RecordingWriter.VERSION + ")");
            }
            SimOptions options = new SimOptions(bool(field(root, "options"), "backwardsAllowed"));
            JsonNode commands = field(root, "commands");
            if (!commands.isArray()) {
                throw new IllegalArgumentException("field 'commands' must be an array");
            }
            List<Recording.Entry> entries = new ArrayList<>();
            for (JsonNode node : commands) {
                entries.add(new Recording.Entry(integer(node, "step"), command(node)));
            }
            return new Recording(text(root, "tower"), options, integer(root, "length"), entries,
                    version >= 2 && root.has("outcome") ? Optional.of(meta(root)) : Optional.empty());
        } catch (IllegalArgumentException | NullPointerException | DateTimeParseException e) {
            throw new IOException("recording: " + e.getMessage(), e);
        }
    }

    private static RecordingMeta meta(JsonNode root) {
        return new RecordingMeta(RecordingMeta.Outcome.fromFileName(text(root, "outcome")),
                Instant.parse(text(root, "recordedAt")), integer(root, "frames"), text(root, "towerChecksum"),
                text(root, "game"));
    }

    private static Command command(JsonNode node) {
        String type = text(node, "type");
        return switch (type) {
            case "Forward" -> new Command.Forward(spider(node));
            case "Back" -> new Command.Back(spider(node));
            case "TurnLeft" -> new Command.TurnLeft(spider(node));
            case "TurnRight" -> new Command.TurnRight(spider(node));
            case "Fire" -> new Command.Fire(spider(node));
            case "EnterWriteMode" -> new Command.EnterWriteMode(spider(node));
            case "Write" -> new Command.Write(spider(node), ProgramStep.valueOf(text(node, "programStep")));
            case "SetCursor" -> new Command.SetCursor(spider(node), integer(node, "cursor"));
            case "Delete" -> new Command.Delete(spider(node));
            case "Clear" -> new Command.Clear(spider(node));
            case "Run" -> new Command.Run(spider(node));
            case "Stop" -> new Command.Stop(spider(node));
            case "RunAll" -> new Command.RunAll();
            case "SelectSpider" -> new Command.SelectSpider(spider(node));
            case "SelectCamera" -> new Command.SelectCamera(camera(node));
            case "CameraEnterWriteMode" -> new Command.CameraEnterWriteMode(camera(node));
            case "CameraWrite" -> new Command.CameraWrite(camera(node), ProgramStep.valueOf(text(node, "programStep")));
            case "CameraForward" -> new Command.CameraForward(camera(node));
            case "CameraBack" -> new Command.CameraBack(camera(node));
            case "CameraTurnLeft" -> new Command.CameraTurnLeft(camera(node));
            case "CameraTurnRight" -> new Command.CameraTurnRight(camera(node));
            case "CameraFire" -> new Command.CameraFire(camera(node));
            case "CameraSetCursor" -> new Command.CameraSetCursor(camera(node), integer(node, "cursor"));
            case "CameraDelete" -> new Command.CameraDelete(camera(node));
            case "CameraClear" -> new Command.CameraClear(camera(node));
            case "CameraZoomIn" -> new Command.CameraZoomIn(camera(node));
            case "CameraZoomOut" -> new Command.CameraZoomOut(camera(node));
            case "Abandon" -> new Command.Abandon();
            case "Restart" -> new Command.Restart();
            default -> throw new IllegalArgumentException("unknown command type '" + type + "'");
        };
    }

    private static SpiderId spider(JsonNode node) {
        return SpiderId.valueOf(text(node, "spider"));
    }

    private static CameraId camera(JsonNode node) {
        return CameraId.valueOf(text(node, "camera"));
    }

    private static JsonNode field(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("missing field '" + name + "'");
        }
        return value;
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("field '" + name + "' must be a string");
        }
        return value.asText();
    }

    private static int integer(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isInt()) {
            throw new IllegalArgumentException("field '" + name + "' must be an integer");
        }
        return value.asInt();
    }

    private static boolean bool(JsonNode node, String name) {
        JsonNode value = field(node, name);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("field '" + name + "' must be a boolean");
        }
        return value.asBoolean();
    }
}
