package de.latzig.game.tob.core.io;

import java.io.IOException;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/** Shared JSON output settings: pretty printed with LF line endings and a trailing newline. */
final class JsonSupport {

    private JsonSupport() {
    }

    static ObjectWriter prettyWriter(ObjectMapper mapper) {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return mapper.writer(printer);
    }

    static String toJson(ObjectMapper mapper, JsonNode node) throws IOException {
        return prettyWriter(mapper).writeValueAsString(node) + "\n";
    }
}
