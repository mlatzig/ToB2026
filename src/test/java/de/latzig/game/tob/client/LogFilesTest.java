package de.latzig.game.tob.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

/**
 * Phase 7c (question 2): the log file in the user data folder, the last five runs kept. Every test writes into its
 * own temporary folder only.
 */
class LogFilesTest {

    @Test
    void theLastFiveRunsAreKept(@TempDir Path userDir) throws IOException {
        Path logs = userDir.resolve("logs");
        for (int run = 1; run <= 7; run++) {
            LogFiles.rotate(logs, LogFiles.KEEP);
            Files.writeString(LogFiles.file(logs, 0), "run " + run);
        }

        assertEquals(List.of("tob2026.1.log", "tob2026.2.log", "tob2026.3.log", "tob2026.4.log", "tob2026.log"),
                names(logs));
        assertEquals("run 7", Files.readString(logs.resolve("tob2026.log")));
        assertEquals("run 6", Files.readString(logs.resolve("tob2026.1.log")));
        assertEquals("run 3", Files.readString(logs.resolve("tob2026.4.log")));
    }

    @Test
    void theFirstRunCreatesTheFolder(@TempDir Path userDir) throws IOException {
        Path logs = userDir.resolve("data/logs");
        LogFiles.rotate(logs, LogFiles.KEEP);
        assertTrue(Files.isDirectory(logs));
        assertEquals(List.of(), names(logs));
    }

    @Test
    void theGameLogsIntoTheFileOfThisRun(@TempDir Path userDir) throws IOException {
        Files.createDirectories(userDir.resolve("logs"));
        Files.writeString(userDir.resolve("logs/tob2026.log"), "the run before\n");
        try {
            Path file = LogFiles.start(userDir);
            assertNotNull(file);
            LoggerFactory.getLogger(LogFilesTest.class).info("a line for the log file");
            LogFiles.stop();

            assertEquals(userDir.resolve("logs").resolve("tob2026.log"), file);
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(text.contains("INFO  d.l.game.tob.client.LogFilesTest - a line for the log file"), text);
            assertTrue(text.matches("(?s)\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} INFO .*"), text);
            assertFalse(text.contains("the run before"), "a new file per run");
            assertEquals("the run before\n", Files.readString(userDir.resolve("logs/tob2026.1.log")));
        } finally {
            LogFiles.stop();
        }
    }

    /**
     * Fix wave (Task 3-4 review #2): when another running game holds {@code tob2026.log} (Windows refuses to move
     * it), nothing is rotated - no run is lost and {@code tob2026.1.log} keeps the run before; the caller appends.
     */
    @Test
    void aCurrentFileHeldByAnotherGameKeepsEveryOlderRun(@TempDir Path userDir) throws IOException {
        Path logs = userDir.resolve("logs");
        Files.createDirectories(logs);
        for (int age = 0; age < LogFiles.KEEP; age++) {
            Files.writeString(LogFiles.file(logs, age), "age " + age);
        }
        LogFiles.Mover held = (from, to) -> {
            if (from.equals(LogFiles.file(logs, 0))) {
                throw new FileSystemException(from.toString(), null, "used by another process");
            }
            LogFiles.FILES.move(from, to);
        };

        assertThrows(IOException.class, () -> LogFiles.rotate(logs, LogFiles.KEEP, held));

        for (int age = 0; age < LogFiles.KEEP; age++) {
            assertEquals("age " + age, Files.readString(LogFiles.file(logs, age)), "run " + age + " kept");
        }
        assertEquals(LogFiles.KEEP, names(logs).size(), "no temporary file left: " + names(logs));
    }

    /** Fix wave (Task 3-4 review #3): any unexpected failure leaves the game with its console log, never stops it. */
    @Test
    void anUnexpectedFailureLeavesTheConsoleLogOnly(@TempDir Path userDir) throws IOException {
        Files.createDirectories(userDir.resolve("logs"));
        Files.writeString(userDir.resolve("logs/tob2026.log"), "the run before\n");
        try {
            assertNull(LogFiles.start(userDir, (from, to) -> {
                throw new IllegalStateException("broken");
            }));
            assertNull(root().getAppender(LogFiles.APPENDER), "no file appender");
        } finally {
            LogFiles.stop();
        }
    }

    /** Fix wave (Task 3-4 review #4): the log file is UTF-8 whatever the platform's charset. */
    @Test
    @SuppressWarnings("unchecked")
    void theLogFileIsUtf8(@TempDir Path userDir) {
        try {
            assertNotNull(LogFiles.start(userDir));
            FileAppender<ILoggingEvent> appender = (FileAppender<ILoggingEvent>) root().getAppender(LogFiles.APPENDER);
            assertEquals(StandardCharsets.UTF_8, ((LayoutWrappingEncoder<ILoggingEvent>) appender.getEncoder())
                    .getCharset());
        } finally {
            LogFiles.stop();
        }
    }

    private static ch.qos.logback.classic.Logger root() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static List<String> names(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
