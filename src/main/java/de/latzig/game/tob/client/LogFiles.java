package de.latzig.game.tob.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

/**
 * Phase 7c (author's answer to question 2): the game always writes its log to a file as well, so a player can attach
 * it to a problem report - {@code <user data>/logs/tob2026.log}, i.e. {@code %APPDATA%\ToB2026\logs} on Windows,
 * the XDG data folder's {@code tob2026/logs} on Linux, and the {@code logs} folder of {@code -Dtob.userdir} during
 * development. The last {@value #KEEP} runs are kept: at each start the older files move up one number
 * ({@code tob2026.log} becomes {@code tob2026.1.log}, ..., {@code tob2026.4.log} is replaced). The console log of
 * {@code logback.xml} stays.
 */
public final class LogFiles {

    private static final Logger LOG = LoggerFactory.getLogger(LogFiles.class);

    /** The folder below the user data folder. */
    public static final String FOLDER = "logs";
    /** The current run's file. */
    public static final String FILE = "tob2026.log";
    /** The runs kept: the current one and four older ones. */
    public static final int KEEP = 5;
    /** The appender's name on the root logger. */
    static final String APPENDER = "TOB_FILE";
    static final String PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{36} - %msg%n";

    private LogFiles() {
    }

    /** Moves a log file, replacing the target (a seam: the tests simulate a file another running game holds). */
    @FunctionalInterface
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    /** The real move. */
    static final Mover FILES = (from, to) -> Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);

    /** The file of the run {@code age} runs ago in {@code logs} (0: the current one, {@value #FILE}). */
    static Path file(Path logs, int age) {
        return logs.resolve(age == 0 ? FILE : "tob2026." + age + ".log");
    }

    /**
     * Makes room for a new run in {@code logs} (created if missing): every older file moves up by one number (the
     * oldest kept one is replaced), so that at most {@code keep - 1} older files remain and {@value #FILE} is free.
     * {@value #FILE} is moved aside first: if another running game holds it (Windows refuses to move an open file),
     * nothing is rotated and no run is lost (fix wave, Task 3-4 review #2).
     */
    static void rotate(Path logs, int keep) throws IOException {
        rotate(logs, keep, FILES);
    }

    static void rotate(Path logs, int keep, Mover mover) throws IOException {
        if (keep < 2) {
            throw new IllegalArgumentException("keep at least the current run and one older: " + keep);
        }
        Files.createDirectories(logs);
        Path current = file(logs, 0);
        Path aside = logs.resolve(FILE + ".tmp");
        boolean previous = Files.exists(current);
        if (previous) {
            mover.move(current, aside);
        }
        for (int age = keep - 2; age >= 1; age--) {
            if (Files.exists(file(logs, age))) {
                mover.move(file(logs, age), file(logs, age + 1));
            }
        }
        if (previous) {
            mover.move(aside, file(logs, 1));
        }
    }

    /**
     * Starts the log file of this run in {@code userDir}'s {@value #FOLDER} folder: rotates the older ones and adds
     * a file appender (UTF-8) to the root logger. If the older files cannot be moved (e.g. another running game holds
     * them), this run appends to {@value #FILE}; if no file can be written at all, or anything else goes wrong, the
     * game logs to the console only.
     *
     * @return the log file, or null if none could be started
     */
    public static Path start(Path userDir) {
        return start(userDir, FILES);
    }

    static Path start(Path userDir, Mover mover) {
        Path logs = userDir.resolve(FOLDER);
        try {
            boolean append = false;
            try {
                rotate(logs, KEEP, mover);
            } catch (IOException e) {
                append = true;
                LOG.warn("Cannot rotate the log files in {}: {}; appending", logs, e.toString());
            }
            Path file = file(logs, 0);
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern(PATTERN);
            encoder.setCharset(StandardCharsets.UTF_8);
            encoder.start();
            FileAppender<ILoggingEvent> appender = new FileAppender<>();
            appender.setContext(context);
            appender.setName(APPENDER);
            appender.setFile(file.toAbsolutePath().toString());
            appender.setAppend(append);
            appender.setEncoder(encoder);
            appender.start();
            if (!appender.isStarted()) {
                LOG.warn("Cannot write the log file {}; logging to the console only", file.toAbsolutePath());
                return null;
            }
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
            LOG.info("Log file: {}", file.toAbsolutePath());
            return file;
        } catch (RuntimeException e) {
            // fix wave (Task 3-4 review #3): the log file is a help, never a reason not to start
            LOG.warn("Cannot start the log file in {}: {}; logging to the console only", logs, e.toString());
            return null;
        }
    }

    /** Stops and removes the log file appender (tests; the game keeps it until it exits). */
    static void stop() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender = root.getAppender(APPENDER);
        if (appender != null) {
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
