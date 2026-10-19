package com.example.flowtracker.code;

import org.junit.jupiter.api.Test;

import static com.example.flowtracker.Fixtures.calls;
import static com.example.flowtracker.Fixtures.offset;
import static com.example.flowtracker.Fixtures.scope;
import static com.example.flowtracker.Fixtures.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading web files from their text: scopes of lines, and calls (JSP -> Java, JS -> JS). */
class FlowOutlineTest {

    // ---------- scopes ----------

    @Test
    void javaScriptCallbacksAndFunctions() {
        assertEquals("- | addEventListener(\"submit\") callback", scope("travel.js", 9));
        assertEquals("- | addEventListener(\"DOMContentLoaded\") callback", scope("travel.js", 4));
        assertEquals("- | validate()", scope("booking.js", 5));
        assertEquals("- | checkDays()", scope("booking.js", 15));
    }

    @Test
    void jspDeclarationsScriptletsAndMarkup() {
        assertEquals("- | escapeHtml()", scope("search.jsp", 5));
        assertEquals("- | [scriptlet]", scope("search.jsp", 18));
        assertEquals("- | <h1>", scope("search.jsp", 29));
        assertEquals("- | form#searchForm", scope("index.jsp", 10));
        assertEquals("- | form#searchForm", scope("index.jsp", 12)); // an input inside the form
        assertEquals("- | <body>", scope("index.jsp", 8));          // "</head><body><main>"
        assertEquals("- | p#formMessage", scope("index.jsp", 15));
    }

    @Test
    void tagLibraryTagsAndHeader() {
        assertEquals("travel | tag status", scope("travel.tld", 8));
        assertEquals("travel | tag status", scope("travel.tld", 9));
        assertEquals("travel | [taglib]", scope("travel.tld", 6));
    }

    @Test
    void functionNames() {
        assertEquals("loadPackages", FlowOutline.functionName("export async function loadPackages(budget)"));
        assertEquals("onSubmit", FlowOutline.functionName("const onSubmit = async (event) =>"));
        assertEquals("search", FlowOutline.functionName("search(term: string): Promise<void>"));
        assertEquals("find", FlowOutline.functionName("public List<Travel> find(int id) throws IOException"));
        assertNull(FlowOutline.functionName("if (budget < 0)"));
        assertNull(FlowOutline.functionName("} else if (x)"));
        assertNull(FlowOutline.functionName("new Runnable()"));
        assertEquals("map() callback", FlowOutline.callbackName("items.map((x) =>"));
    }

    // ---------- JSP -> Java calls ----------

    private static String jspCalls(int line) {
        String jsp = text("search.jsp");
        return calls(FlowOutline.jspJavaCalls(jsp, offset(jsp, line)));
    }

    @Test
    void scriptletCallsWithArgumentCounts() {
        String l21 = jspCalls(21);
        assertTrue(l21.contains("new DestinationService/0") && l21.contains("search/3"), l21);
        assertTrue(l21.contains("getParameter/1") && l21.contains("parseDouble/1"), "string arguments count\n" + l21);
    }

    @Test
    void expressionsInMarkupAndDeclarations() {
        String l33 = jspCalls(33);
        assertTrue(l33.contains("getDestination/0") && l33.contains("escapeHtml/1") && l33.contains("getPackagePrice/0"), l33);
        String l5 = jspCalls(5);
        assertFalse(l5.contains("escapeHtml"), "a declaration is not a call: " + l5);
        assertTrue(l5.contains("replace/2"), l5);
        assertEquals("", jspCalls(29), "plain markup has no calls");
    }

    @Test
    void scriptsAndStringsAreNotJavaCalls() {
        assertFalse(calls(FlowOutline.jspJavaCalls("<script>search(1, 2, 3);</script>\n", 0)).contains("search"));
        assertFalse(calls(FlowOutline.jspJavaCalls("<% String s = \"x.search(a, b, c)\"; %>\n", 0)).contains("search"));
    }

    // ---------- JS -> JS calls ----------

    private static String scriptCalls(int line) {
        String js = text("booking.js");
        return calls(FlowOutline.scriptCalls(js, offset(js, line)));
    }

    @Test
    void callsInTheEnclosingFunction() {
        String inValidate = scriptCalls(6);
        assertTrue(inValidate.contains("checkBudget/1") && inValidate.contains("checkDays/1")
                && inValidate.contains("showError/1"), "the whole function body is read\n" + inValidate);
        assertTrue(scriptCalls(23).contains("validate/1"), "the submit callback calls validate()");
    }

    @Test
    void declarationsStringsAndCommentsAreNotCalls() {
        String inBudget = scriptCalls(11);
        assertFalse(inBudget.contains("checkDays"), "\"checkDays(1)\" in a string / comment: " + inBudget);
        assertFalse(scriptCalls(5).contains("validate/"), "the declaration itself is not a call");
    }
}
