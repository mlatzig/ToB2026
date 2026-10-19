package de.latzig.game.tob.release;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 7d (7c final review M6): the Markdown documents as the packages carry them. The repository's
 * {@code README.md} starts with a block for GitHub - the download link and the screenshots, which are not in a
 * package - between {@value #GITHUB_START} and {@value #GITHUB_END}; the package's copy leaves it out. A relative
 * link to a file that is not in the package points to that file in the repository instead.
 */
final class PackageReadme {

    /** The public repository; the documents name its issues for problem reports and removal requests. */
    static final String REPOSITORY = "https://github.com/mlatzig/ToB2026";
    static final String GITHUB_START = "<!-- github-only -->";
    static final String GITHUB_END = "<!-- /github-only -->";
    /** A Markdown link's target: a path or URL, optionally with an anchor; a link to an anchor alone is kept. */
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s#]+)(#[^)\\s]*)?\\)");

    private PackageReadme() {
    }

    /** {@code markdown} for a package whose top folder holds the files {@code packaged} (relative paths). */
    static String forPackage(String markdown, Collection<String> packaged) {
        Matcher link = LINK.matcher(withoutGitHubBlock(markdown));
        StringBuilder out = new StringBuilder();
        while (link.find()) {
            String target = link.group(1);
            String anchor = link.group(2) == null ? "" : link.group(2);
            boolean keep = target.matches("[a-z]+:.*") || packaged.contains(target);
            link.appendReplacement(out, Matcher.quoteReplacement(keep ? link.group()
                    : "](" + REPOSITORY + "/blob/main/" + target + anchor + ")"));
        }
        link.appendTail(out);
        return out.toString();
    }

    /** {@code markdown} without the block for GitHub and the blank line after it; unchanged if it has none. */
    static String withoutGitHubBlock(String markdown) {
        int start = markdown.indexOf(GITHUB_START);
        int end = markdown.indexOf(GITHUB_END);
        if (start < 0 && end < 0) {
            return markdown;
        }
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("the block for GitHub is not opened and closed in order");
        }
        String rest = markdown.substring(end + GITHUB_END.length());
        for (int i = 0; i < 2 && rest.startsWith("\n"); i++) {
            rest = rest.substring(1);
        }
        return markdown.substring(0, start) + rest;
    }
}
