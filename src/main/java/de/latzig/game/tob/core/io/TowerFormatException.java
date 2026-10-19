package de.latzig.game.tob.core.io;

import java.io.IOException;
import java.util.List;

/** A tower library could not be read; lists every problem found. */
public class TowerFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public TowerFormatException(List<String> problems) {
        super(problems.size() + " problem(s): " + String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
