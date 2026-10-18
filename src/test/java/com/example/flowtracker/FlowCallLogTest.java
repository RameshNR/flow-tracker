package com.example.flowtracker;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.flowtracker.Fixtures.line;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Nest Calls in Original Log": callees under the line that calls them. */
class FlowCallLogTest {

    private final FlowEntry m8 = line("Main.java", "Main", "main()", 8, "DestinationService destinationService = new DestinationService();");
    private final FlowEntry m13 = line("Main.java", "Main", "main()", 13, "List<Travel> packages = destinationService.search(\"\", 15000, 4);");
    private final FlowEntry m14 = line("Main.java", "Main", "main()", 14, "packages.forEach(System.out::println);");
    private final FlowEntry m26 = line("Main.java", "Main", "main()", 26, "boolean cancelled = bookingService.cancelBooking(booking.getBookingId());");
    private final FlowEntry m27 = line("Main.java", "Main", "main()", 27, "System.out.println(\"Cancellation successful: \" + cancelled);");
    private final FlowEntry s21 = line("DestinationService.java", "DestinationService", "search()", 21, "if (maxBudget < 0 || maxDays <= 0) {");
    private final FlowEntry c55 = line("BookingService.java", "BookingService", "cancelBooking()", 55, "Booking booking = bookings.get(bookingId);");
    private final List<FlowEntry> grouped = List.of(m8, m13, m14, m26, m27, s21, c55);

    private FlowSync.Calls calls(Object... linkAndLine) {
        Set<String> links = new LinkedHashSet<>();
        Map<String, Integer> lines = new java.util.HashMap<>();
        for (int i = 0; i < linkAndLine.length; i += 2) {
            links.add((String) linkAndLine[i]);
            if (linkAndLine[i + 1] != null) lines.put((String) linkAndLine[i], (Integer) linkAndLine[i + 1]);
        }
        return new FlowSync.Calls(links, lines);
    }

    private static String link(FlowEntry caller, FlowEntry callee) {
        return FlowSync.link(FlowSync.methodKey(caller), FlowSync.methodKey(callee));
    }

    private FlowSync.Calls travelCalls() {
        return calls(link(m8, s21), 13, link(m8, c55), 26);
    }

    @Test
    void groupedViewNestsCalleesUnderTheCallingLine() {
        // Called blocks: lines level with file and method, an empty line after, no repeated project.
        String expected = """
                Main.java  (TravelBooking)
                main()
                    8: DestinationService destinationService = new DestinationService();
                    13: List<Travel> packages = destinationService.search("", 15000, 4);
                        DestinationService.java
                        search()
                            21: if (maxBudget < 0 || maxDays <= 0) {

                    14: packages.forEach(System.out::println);
                    26: boolean cancelled = bookingService.cancelBooking(booking.getBookingId());
                        BookingService.java
                        cancelBooking()
                            55: Booking booking = bookings.get(bookingId);

                    27: System.out.println("Cancellation successful: " + cancelled);
                """;
        assertEquals(expected, FlowCallLog.render(grouped, false, grouped, travelCalls()));
    }

    @Test
    void flatViewNestsNumberedSteps() {
        List<FlowEntry> flat = List.of(m8, m13, s21, m14, m26, c55, m27);
        String expected = """
                1. Main.main():8 | DestinationService destinationService = new DestinationService();
                2. Main.main():13 | List<Travel> packages = destinationService.search("", 15000, 4);
                    3. DestinationService.search():21 | if (maxBudget < 0 || maxDays <= 0) {

                4. Main.main():14 | packages.forEach(System.out::println);
                5. Main.main():26 | boolean cancelled = bookingService.cancelBooking(booking.getBookingId());
                    6. BookingService.cancelBooking():55 | Booking booking = bookings.get(bookingId);

                7. Main.main():27 | System.out.println("Cancellation successful: " + cancelled);
                """;
        assertEquals(expected, FlowCallLog.render(flat, true, flat, travelCalls()));
    }

    @Test
    void callBetweenSavedLinesGoesUnderTheLineBeforeIt() {
        // search() is called from line 10, which isn't saved: it goes under line 8, the saved line before it.
        String log = FlowCallLog.render(grouped, false, grouped, calls(link(m8, s21), 10));
        assertTrue(log.contains("    8: DestinationService destinationService = new DestinationService();\n"
                + "        DestinationService.java\n        search()\n            21:"), log);
    }

    @Test
    void callBeforeTheFirstSavedLineComesFirst() {
        String log = FlowCallLog.render(grouped, false, grouped, calls(link(m8, s21), 3));
        assertTrue(log.contains("main()\n        DestinationService.java\n        search()\n"
                + "            21: if (maxBudget < 0 || maxDays <= 0) {\n\n    8:"), log);
    }

    @Test
    void unknownCallLineGoesAfterTheLastLine() {
        String log = FlowCallLog.render(grouped, false, grouped, calls(link(m8, s21), null));
        assertTrue(log.contains("    27: System.out.println(\"Cancellation successful: \" + cancelled);\n"
                + "        DestinationService.java"), log);
    }

    @Test
    void aMethodCalledTwiceIsShownOnceAndReferredTo() {
        FlowSync.Calls twice = calls(link(m8, s21), 13, link(c55, s21), 55, link(m8, c55), 26);
        String grouped2 = FlowCallLog.render(grouped, false, grouped, twice);
        assertEquals(1, grouped2.split("21: if").length - 1, grouped2);
        assertTrue(grouped2.contains("                ↳ search()  (DestinationService.java, shown above)"), grouped2);
        List<FlowEntry> flat = List.of(m8, m13, s21, m14, m26, c55, m27);
        String flat2 = FlowCallLog.render(flat, true, flat, twice);
        assertTrue(flat2.contains("↳ DestinationService.search():21  (see step 3)"), flat2);
    }

    @Test
    void notesTagsAndUnrelatedMethods() {
        FlowEntry noted = line("Main.java", "Main", "main()", 8, "x();");
        noted.note = "starts here";
        noted.tag = "important";
        FlowEntry other = line("Util.java", "Util", "helper()", 3, "return 1;");
        List<FlowEntry> lines = List.of(noted, other);
        String log = FlowCallLog.render(lines, false, lines, FlowSync.Calls.NONE);
        assertEquals("""
                Main.java  (TravelBooking)
                main()
                    8: x();  [important]
                       note: starts here

                Util.java
                helper()
                    3: return 1;
                """, log);
    }

    @Test
    void anotherProjectIsNamedAndConnectionsMadeByHandAreUsed() {
        // An API call the code can't follow: the client line is connected by hand to the handler.
        FlowEntry client = line("Main.java", "Main", "main()", 13, "http.post(\"/bookings\", request);");
        FlowEntry after = line("Main.java", "Main", "main()", 14, "log(\"sent\");");
        FlowEntry handler = line("BookingController.java", "BookingController", "create()", 40, "return service.book(request);");
        handler.projectName = "BookingApi";
        client.calls.add(FlowSync.methodKey(handler));
        List<FlowEntry> lines = List.of(client, after, handler);

        FlowSync.Calls calls = FlowSync.withManual(FlowSync.Calls.NONE, lines);
        String link = link(client, handler);
        assertTrue(calls.links().contains(link));
        assertEquals(13, calls.lines().get(link), "nested under the connected line");
        assertEquals("""
                Main.java  (TravelBooking)
                main()
                    13: http.post("/bookings", request);
                        BookingController.java  (BookingApi)
                        create()
                            40: return service.book(request);

                    14: log("sent");
                """, FlowCallLog.render(lines, false, lines, calls));

        String md = FlowMarkdown.toMarkdown(lines, true, calls.links(), true);
        assertTrue(md.contains("p1->>p2: create()"), "the diagram draws the connection\n" + md);

        FlowEntry gone = line("Main.java", "Main", "main()", 20, "x();");
        gone.calls.add("file:///p/Removed.java#gone()");
        assertTrue(FlowSync.withManual(FlowSync.Calls.NONE, List.of(gone)).links().isEmpty(), "methods no longer in the flow are skipped");
    }

    @Test
    void connectionsAreToggledInTheStore() {
        FlowStore store = new FlowStore();
        store.addAll(List.of(m13, s21), false);
        String key = FlowSync.methodKey(s21);
        store.toggleConnection(m13, key);
        assertEquals(List.of(key), m13.calls);
        assertTrue(store.getCallLinks().contains(link(m13, s21)), "the store's calls include it");
        store.toggleConnection(m13, key);
        assertTrue(m13.calls.isEmpty(), "picking it again removes it");
        store.toggleConnection(m13, key);
        store.clearConnections(m13);
        assertTrue(m13.calls.isEmpty());
    }

    @Test
    void theSwitchChoosesTheOriginalLogLayout() {
        FlowStore store = new FlowStore();
        assertTrue(store.isNestedCallLog() && store.isCopyMarkdown() && store.isSyncFiles()
                && store.isSequenceDiagram() && store.isCopyAllFlows(), "on by default");
        String nested = FlowCopy.textFor(store, grouped, grouped, travelCalls(), "Flow 1");
        assertTrue(nested.contains("with each call nested under the line that makes it")
                && nested.contains("        DestinationService.java\n        search()"), nested);
        store.setNestedCallLog(false);
        String plain = FlowCopy.textFor(store, grouped, grouped, travelCalls(), "Flow 1");
        assertTrue(plain.contains("```text\n" + FlowFormat.toText(grouped) + "```"), plain);
    }
}
