package com.example.flowtracker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/** Shared test data: saved lines and the sample files in src/test/resources/fixtures. */
final class Fixtures {
    private Fixtures() {}

    /** A saved line in the TravelBooking project. */
    static FlowEntry line(String file, String cls, String method, int line, String text) {
        FlowEntry e = new FlowEntry(file, "file:///p/" + file, cls, method, line, text);
        e.projectName = "TravelBooking";
        return e;
    }

    /** A fixture file's text, with \n line ends. */
    static String text(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) throw new IllegalArgumentException("No fixture " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Offset of the first non-blank character of a 1-based line. */
    static int offset(String text, int line) {
        int start = 0;
        for (int i = 1; i < line; i++) start = text.indexOf('\n', start) + 1;
        int at = start;
        while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == '\t')) at++;
        return at;
    }

    /** "class | method" for a 1-based line of a fixture. */
    static String scope(String fixture, int line) {
        String text = text(fixture);
        FlowOutline.Scope s = FlowOutline.locate(text, offset(text, line), fixture);
        return s.className() + " | " + s.methodName();
    }

    /** Calls as "name/args" ("new X/0" for constructors), in order. */
    static String calls(List<FlowOutline.TextCall> calls) {
        return calls.stream().map(c -> (c.constructor() ? "new " : "") + c.name() + "/" + c.args())
                .collect(Collectors.joining(", "));
    }
}
