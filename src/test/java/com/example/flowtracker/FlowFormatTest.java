package com.example.flowtracker;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.example.flowtracker.Fixtures.line;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Text copies, labels, file types, names and other small helpers. */
class FlowFormatTest {

    @Test
    void tagsShowInTextCopies() {
        FlowEntry e = line("Service.kt", "Service", "book()", 5, "repo.save(order)");
        e.tag = "bug";
        assertTrue(FlowFormat.toFlatText(List.of(e), List.of(e)).contains("repo.save(order)  [bug]"));
        assertTrue(FlowFormat.toText(List.of(e)).contains("[bug]"));
        assertEquals(FlowTag.QUESTION, FlowTag.byId("question"));
        assertNull(FlowTag.byId("nope"));
    }

    @Test
    void methodLabels() {
        assertEquals("helper()", FlowFormat.methodLabel(line("Util.kt", "UtilKt", "helper()", 1, "x")), "Kotlin top-level function");
        assertEquals("Main.Inner.run()", FlowFormat.methodLabel(line("Main.java", "Main.Inner", "run()", 1, "x")));
        assertEquals("[class level]", FlowFormat.methodLabel(line("A.java", "A", "-", 1, "int x;")), "saved by older versions");
        assertEquals("[top level]", FlowFormat.methodLabel(line("a.properties", "-", "-", 1, "k=v")));
        assertTrue(FlowFormat.isClassLevel("[fields]") && FlowFormat.isClassLevel("Inner.[fields]"));
        assertFalse(FlowFormat.isClassLevel("bookTrip()"));
    }

    @Test
    void supportedFilesAndCodeFences() {
        for (String f : List.of("a.jsp", "a.js", "a.jsx", "a.tld", "a.ts", "a.tsx", "a.xml", "a.html", "a.groovy",
                "a.properties", "a.yml", "a.sql", "A.java", "A.kt")) {
            assertTrue(FlowLang.isSupportedName(f), f);
            assertFalse(FlowLang.fence(f).isEmpty(), f);
        }
        assertFalse(FlowLang.isSupportedName("a.png"));
        assertEquals("typescript", FlowLang.fence("a.ts"));
    }

    @Test
    void highlighterTokens() {
        List<CodeHighlighter.Span> spans = CodeHighlighter.highlight("int n = 42; // count", FlowLang.Kind.JAVA);
        assertEquals(CodeHighlighter.Token.KEYWORD, spans.get(0).token());
        assertTrue(spans.stream().anyMatch(s -> s.text().equals("42") && s.token() == CodeHighlighter.Token.NUMBER));
        assertEquals(CodeHighlighter.Token.COMMENT, spans.get(spans.size() - 1).token());
        assertTrue(CodeHighlighter.highlight("fun go() = when (x) {", FlowLang.Kind.KOTLIN).stream()
                .anyMatch(s -> s.text().equals("fun") && s.token() == CodeHighlighter.Token.KEYWORD));
    }

    @Test
    void flowNamesAndExportFileNames() {
        assertEquals("Flow 3: Checkout bug", FlowToolWindowFactory.normalizeFlowName("  Flow 3: Checkout bug "));
        assertEquals("Flow 3", FlowToolWindowFactory.normalizeFlowName("Flow 3: "));
        assertEquals("Checkout bug", FlowToolWindowFactory.normalizeFlowName("Checkout bug"));
        assertNull(FlowToolWindowFactory.normalizeFlowName("   "));
        assertEquals("Flow-1-Checkout-bug.flow.json", FlowTransfer.fileNameFor("Flow 1: Checkout bug"));
        assertEquals("Checkout-bug-v2.flow.json", FlowTransfer.fileNameFor("Checkout bug / v2"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void movingWithAFilterKeepsHiddenLinesInPlace() throws Exception {
        FlowEntry a = line("A.java", "A", "a()", 1, "a"), b = line("A.java", "A", "a()", 2, "b"),
                c = line("A.java", "A", "a()", 3, "c"), d = line("A.java", "A", "a()", 4, "d"), e = line("A.java", "A", "a()", 5, "e");
        Method merge = FlowToolWindowFactory.class.getDeclaredMethod("mergeOrder", List.class, List.class);
        merge.setAccessible(true);
        List<FlowEntry> merged = (List<FlowEntry>) merge.invoke(null, List.of(a, b, c, d, e), List.of(e, b));
        assertEquals(List.of(a, e, c, d, b), merged);
    }
}
