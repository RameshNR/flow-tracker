package com.example.flowtracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Nest Calls in Original Log": the plain log with each called method's lines placed right under the line
 * that calls it, indented, like a call stack. For example (Grouped View):
 * <pre>
 * Main.java  (TravelBooking)
 * main()
 *     13: List&lt;Travel&gt; packages = destinationService.search("", 15000, 4);
 *         DestinationService.java
 *         search()
 *             21: if (maxBudget &lt; 0 || maxDays &lt;= 0) {
 *
 *     14: packages.forEach(System.out::println);
 * </pre>
 * Code lines sit one tab (4 spaces) under their method, and an empty line closes each called block. The project is
 * named only when it differs from the caller's (for top-level blocks: from the previous one). Flat View gives
 * the same nesting as numbered steps ("2. Main.main():13 | ..."). A callee goes under the last saved line at
 * or before the line the call is on (before the first line when the call comes earlier). A method called
 * again later is shown once and referred to ("↳ ... see above") at the other calls.
 */
final class FlowCallLog {
    private FlowCallLog() {}

    private static final String STEP = "    ";

    /** One saved method: its key ({@link FlowSync#methodKey}), lines in line order, and calls. */
    private static final class Method {
        final String key;
        final List<FlowEntry> lines = new ArrayList<>();
        /** Callees with the caller's line each call is on (MAX_VALUE when unknown: after the last line). */
        final List<Map.Entry<String, Integer>> calls = new ArrayList<>();

        Method(String key) {
            this.key = key;
        }

        FlowEntry first() {
            return lines.get(0);
        }
    }

    /**
     * @param entries the lines, in the order the view shows them (roots are printed in this order)
     * @param flat    true = numbered steps, false = file / method headers
     * @param all     every line of the flow (Flat View names projects when the flow spans several)
     * @param calls   calls between the methods, with the line each call is on
     */
    static String render(List<FlowEntry> entries, boolean flat, List<FlowEntry> all, FlowSync.Calls calls) {
        List<FlowEntry> ordered = flat ? entries : groupedOrder(entries);
        Map<String, Method> methods = new LinkedHashMap<>();
        for (FlowEntry e : ordered) methods.computeIfAbsent(FlowSync.methodKey(e), Method::new).lines.add(e);
        for (Method m : methods.values()) m.lines.sort(Comparator.comparingInt(e -> e.line));

        Set<String> called = new HashSet<>();
        for (String link : calls.links()) {
            String[] ends = FlowSync.parseLink(link);
            if (ends == null || ends[0].equals(ends[1])) continue;
            Method caller = methods.get(ends[0]);
            if (caller == null || !methods.containsKey(ends[1])) continue;
            caller.calls.add(Map.entry(ends[1], calls.lines().getOrDefault(link, Integer.MAX_VALUE)));
            called.add(ends[1]);
        }

        Writer w = new Writer(flat, FlowFormat.spansProjects(all), methods);
        for (Method m : methods.values()) {
            if (!called.contains(m.key)) w.root(m);
        }
        for (Method m : methods.values()) {
            if (!w.shown.containsKey(m.key)) w.root(m); // only reachable through a cycle
        }
        String text = w.out.toString();
        while (text.endsWith("\n\n")) text = text.substring(0, text.length() - 1); // no empty line at the end
        return text;
    }

    /** Grouped View order: files in order of first appearance, then their methods. */
    private static String project(FlowEntry e) {
        return e.projectName == null ? "" : e.projectName.strip();
    }

    private static List<FlowEntry> groupedOrder(List<FlowEntry> entries) {
        List<FlowEntry> out = new ArrayList<>();
        for (FlowFormat.FileGroup f : FlowFormat.group(entries)) f.methods().values().forEach(out::addAll);
        return out;
    }

    private static final class Writer {
        final boolean flat;
        final boolean showProject;
        final Map<String, Method> methods;
        final StringBuilder out = new StringBuilder();
        /** Methods already printed, with their first step number (Flat View) or 0. */
        final Map<String, Integer> shown = new HashMap<>();
        final Set<String> stack = new HashSet<>();
        String lastRootFile;
        String lastRootProject;
        int step;

        Writer(boolean flat, boolean showProject, Map<String, Method> methods) {
            this.flat = flat;
            this.showProject = showProject;
            this.methods = methods;
        }

        void root(Method m) {
            if (!flat && out.length() > 0 && !m.first().fileUrl.equals(lastRootFile)) blankLine();
            boolean fileHeader = !m.first().fileUrl.equals(lastRootFile);
            lastRootFile = m.first().fileUrl;
            method(m, "", fileHeader, lastRootProject);
            lastRootProject = project(m.first());
        }

        /**
         * A method's lines, each followed by the methods it calls from that line.
         *
         * @param context the project of the caller (or of the previous top-level block); the file header
         *                names the project only when it is different
         */
        void method(Method m, String indent, boolean fileHeader, String context) {
            stack.add(m.key);
            shown.put(m.key, step + 1);
            String lineIndent = indent;
            FlowEntry f = m.first();
            if (!flat) {
                if (fileHeader) {
                    out.append(indent).append(f.fileName);
                    String project = project(f);
                    if (!project.isEmpty() && !project.equals(context)) out.append("  (").append(project).append(')');
                    out.append('\n');
                }
                out.append(indent).append(FlowFormat.methodLabel(f)).append('\n');
                lineIndent = indent + STEP; // code lines one tab under their method
            }
            String nested = lineIndent + STEP;
            String project = project(f);

            // Each call goes under the last saved line at or before it (index -1: before the first line).
            Map<Integer, List<String>> callsAfter = new HashMap<>();
            for (Map.Entry<String, Integer> c : m.calls) {
                int at = -1;
                for (int i = 0; i < m.lines.size(); i++) {
                    if (m.lines.get(i).line <= c.getValue()) at = i;
                }
                callsAfter.computeIfAbsent(at, k -> new ArrayList<>()).add(c.getKey());
            }
            callees(callsAfter.get(-1), nested, project);
            for (int i = 0; i < m.lines.size(); i++) {
                line(m.lines.get(i), lineIndent);
                callees(callsAfter.get(i), nested, project);
            }
            stack.remove(m.key);
        }

        /** An empty line, unless the text already ends with one. */
        void blankLine() {
            int n = out.length();
            if (n > 0 && !(n >= 2 && out.charAt(n - 1) == '\n' && out.charAt(n - 2) == '\n')) out.append('\n');
        }

        void callees(List<String> keys, String indent, String callerProject) {
            if (keys == null) return;
            for (String key : keys) {
                Method callee = methods.get(key);
                if (stack.contains(key) || shown.containsKey(key)) {
                    FlowEntry f = callee.first();
                    out.append(indent).append("↳ ").append(flat ? FlowFormat.flatLabel(f) : FlowFormat.methodLabel(f));
                    out.append(stack.contains(key) ? "  (recursive call)"
                            : flat ? "  (see step " + shown.get(key) + ")" : "  (" + f.fileName + ", shown above)");
                    out.append('\n');
                } else {
                    method(callee, indent, true, callerProject);
                    blankLine(); // an empty line closes each called block
                }
            }
        }

        void line(FlowEntry e, String indent) {
            step++;
            out.append(indent);
            if (flat) {
                out.append(step).append(". ");
                if (showProject && e.projectName != null && !e.projectName.isBlank()) out.append('[').append(e.projectName).append("] ");
                out.append(FlowFormat.flatLabel(e)).append(" | ");
            } else {
                out.append(e.line).append(": ");
            }
            out.append(e.text).append(FlowFormat.tagSuffix(e)).append('\n');
            if (e.note != null && !e.note.isBlank()) out.append(indent).append("   note: ").append(e.note).append('\n');
        }
    }
}
