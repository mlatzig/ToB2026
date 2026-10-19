package de.latzig.game.tob.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 7b: the project's publication documents (README, CREDITS, the licence files, {@code docs/}), read from the
 * project root, which is the working directory of the Maven build.
 */
final class PublishedDocs {

    /**
     * A fenced code block of a Markdown text.
     *
     * @param info    the info string after the opening fence ({@code json}, {@code text}, or empty)
     * @param content the lines between the fences, joined with '\n'
     * @param open    the 0-based line of the opening fence
     * @param close   the 0-based line of the closing fence
     */
    record CodeBlock(String info, String content, int open, int close) {
    }

    private PublishedDocs() {
    }

    /** The project root: the working directory, which holds {@code pom.xml}. */
    static Path root() {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalStateException("the tests must run in the project root, not in " + root);
        }
        return root;
    }

    /** The UTF-8 text of a file, given relative to the project root with '/'. */
    static String read(String relative) throws IOException {
        return Files.readString(root().resolve(relative), StandardCharsets.UTF_8);
    }

    /**
     * The fenced code blocks of a Markdown text with LF line endings: a line of three or more backticks (indentation
     * allowed) opens a block, a line of at least as many backticks and nothing else closes it.
     */
    static List<CodeBlock> fencedBlocks(String markdown) {
        List<CodeBlock> blocks = new ArrayList<>();
        String[] lines = markdown.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String fence = lines[i].strip();
            int ticks = fence.length() - fence.replaceFirst("^`+", "").length();
            if (ticks < 3) {
                continue;
            }
            int close = i + 1;
            while (close < lines.length && !closes(lines[close].strip(), ticks)) {
                close++;
            }
            if (close == lines.length) {
                throw new IllegalStateException("code block at line " + (i + 1) + " is not closed");
            }
            List<String> content = List.of(lines).subList(i + 1, close);
            blocks.add(new CodeBlock(fence.substring(ticks).strip(), String.join("\n", content), i, close));
            i = close;
        }
        return blocks;
    }

    /** The contents of the fenced code blocks with the given info string, in document order. */
    static List<String> codeBlocks(String markdown, String info) {
        return fencedBlocks(markdown).stream().filter(block -> block.info().equals(info)).map(CodeBlock::content)
                .toList();
    }

    private static boolean closes(String line, int ticks) {
        return line.length() >= ticks && line.chars().allMatch(c -> c == '`');
    }
}
