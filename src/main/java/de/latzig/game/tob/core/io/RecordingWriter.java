package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.latzig.game.tob.core.sim.Command;
import de.latzig.game.tob.core.sim.Recording;

/**
 * Writes an input recording as JSON (format {@value #FORMAT}, version {@value #VERSION}, LF line endings). Phase 3b-1
 * added the camera commands as new {@code type} values only. Version 2 (Phase 3c) adds the optional metadata fields
 * {@code outcome}, {@code recordedAt}, {@code frames}, {@code towerChecksum} and {@code game} after {@code length};
 * a recording without metadata is written without them. {@link RecordingReader} reads versions 1 and 2.
 */
public final class RecordingWriter {

    public static final String FORMAT = "tob2026-recording";
    public static final int VERSION = 2;

    private final ObjectMapper mapper = new ObjectMapper();

    public void write(Recording recording, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, toJson(recording), StandardCharsets.UTF_8);
    }

    public String toJson(Recording recording) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("tower", recording.towerId());
        ObjectNode options = root.putObject("options");
        options.put("backwardsAllowed", recording.options().backwardsAllowed());
        root.put("length", recording.length());
        recording.meta().ifPresent(meta -> {
            root.put("outcome", meta.outcome().fileName());
            root.put("recordedAt", meta.recordedAt().toString());
            root.put("frames", meta.frames());
            root.put("towerChecksum", meta.towerChecksum());
            root.put("game", meta.game());
        });
        ArrayNode commands = root.putArray("commands");
        for (Recording.Entry entry : recording.entries()) {
            commands.add(entry(entry));
        }
        return JsonSupport.toJson(mapper, root);
    }

    private ObjectNode entry(Recording.Entry entry) {
        ObjectNode node = mapper.createObjectNode();
        Command command = entry.command();
        node.put("step", entry.step());
        node.put("type", command.getClass().getSimpleName());
        if (command instanceof Command.SpiderCommand spiderCommand) {
            node.put("spider", spiderCommand.spider().name());
        }
        if (command instanceof Command.CameraCommand cameraCommand) {
            node.put("camera", cameraCommand.camera().name());
        }
        if (command instanceof Command.SelectSpider select) {
            node.put("spider", select.spider().name());
        }
        if (command instanceof Command.SelectCamera select) {
            node.put("camera", select.camera().name());
        }
        if (command instanceof Command.Write write) {
            node.put("programStep", write.step().name());
        }
        if (command instanceof Command.CameraWrite write) {
            node.put("programStep", write.step().name());
        }
        if (command instanceof Command.SetCursor setCursor) {
            node.put("cursor", setCursor.cursor());
        }
        if (command instanceof Command.CameraSetCursor setCursor) {
            node.put("cursor", setCursor.cursor());
        }
        return node;
    }
}
