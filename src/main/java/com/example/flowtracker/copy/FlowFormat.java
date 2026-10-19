package com.example.flowtracker.copy;

import com.example.flowtracker.code.FlowLang;
import com.example.flowtracker.diagram.FlowDiagram;
import com.example.flowtracker.model.FlowEntry;
import com.example.flowtracker.model.FlowTag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups entries as File -> method -> lines (in the order they were first added) and prints them as text. */
public final class FlowFormat {
    private FlowFormat() {}

    public record FileGroup(String fileUrl, String fileName, String projectName, Map<String, List<FlowEntry>> methods) {}

    public static List<FileGroup> group(Collection<FlowEntry> entries) {
        Map<String, FileGroup> byFile = new LinkedHashMap<>();
        for (FlowEntry e : entries) {
            FileGroup g = byFile.computeIfAbsent(e.fileUrl, k -> new FileGroup(e.fileUrl, e.fileName, e.projectName, new LinkedHashMap<>()));
            g.methods().computeIfAbsent(methodLabel(e), k -> new ArrayList<>()).add(e);
        }
        return new ArrayList<>(byFile.values());
    }

    /** "main()" normally; "Inner.foo()" when the class is not the file's main class. */
    public static String methodLabel(FlowEntry e) {
        String base = FlowLang.baseName(e.fileName);
        String method = methodName(e);
        // Kotlin top-level functions live in a "<File>Kt" class.
        if (e.className.equals(base) || e.className.equals(base + "Kt") || e.className.equals("-")) return method;
        return e.className + "." + method;
    }

    /**
     * The method name to show. Lines outside any method were saved as "-" by earlier versions: they read as
     * "[class level]" in Java / Kotlin / Groovy files and "[top level]" elsewhere.
     */
    public static String methodName(FlowEntry e) {
        if (e.methodName != null && !e.methodName.isBlank() && !e.methodName.equals("-")) return e.methodName;
        String f = e.fileName;
        return FlowLang.usesJavaPsi(f) || FlowLang.isKotlin(f) ? CLASS_LEVEL : "[top level]";
    }

    private static final String CLASS_LEVEL = "[class level]";

    /** Labels for lines that sit in a class but outside its methods; the diagrams leave them out. */
    private static final java.util.Set<String> CLASS_LEVEL_LABELS =
            java.util.Set.of(CLASS_LEVEL, "[fields]", "[properties]", "[imports]", "[class]");

    /** True for a method label (or "Class.label") that is a field, import or class header group. */
    public static boolean isClassLevel(String methodLabel) {
        int dot = methodLabel.lastIndexOf(".[");
        return CLASS_LEVEL_LABELS.contains(dot >= 0 ? methodLabel.substring(dot + 1) : methodLabel);
    }

    /** The line number to show: "+" for a line added in the Flow Diagram (it has no code yet). */
    public static String lineNumber(FlowEntry e) {
        return FlowDiagram.isDiagramOnly(e) ? "+" : String.valueOf(e.line);
    }

    static String toText(Collection<FlowEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (FileGroup f : group(entries)) {
            if (!sb.isEmpty()) sb.append('\n'); // one empty line between files
            sb.append(f.fileName());
            if (hasText(f.projectName())) sb.append("  (").append(f.projectName()).append(')');
            sb.append('\n');
            for (Map.Entry<String, List<FlowEntry>> m : f.methods().entrySet()) {
                sb.append("  ").append(m.getKey()).append('\n');
                for (FlowEntry e : m.getValue()) {
                    sb.append("    ").append(lineNumber(e)).append(": ").append(e.text).append(tagSuffix(e)).append('\n');
                    if (e.note != null && !e.note.isBlank()) {
                        sb.append("       note: ").append(e.note).append('\n');
                    }
                }
            }
        }
        return sb.toString();
    }

    // ---------- Flat View ----------

    /** "Class.method():line" (falls back to the file name when class and method are unknown). */
    public static String flatLabel(FlowEntry e) {
        boolean hasClass = !e.className.isEmpty() && !e.className.equals("-");
        boolean hasMethod = !e.methodName.isEmpty() && !e.methodName.equals("-");
        String head = hasClass && hasMethod ? e.className + "." + e.methodName
                : hasClass ? e.className + "." + methodName(e)
                : hasMethod ? e.methodName
                : e.fileName;
        return head + ":" + lineNumber(e);
    }

    /** Flat sequence with step numbers. Step numbers are the position in the full list. */
    static String toFlatText(Collection<FlowEntry> subset, List<FlowEntry> all) {
        StringBuilder sb = new StringBuilder();
        boolean showProject = spansProjects(all);
        for (FlowEntry e : subset) {
            int step = all.indexOf(e) + 1;
            sb.append(step).append(". ");
            if (showProject && hasText(e.projectName)) sb.append('[').append(e.projectName).append("] ");
            sb.append(flatLabel(e)).append(" | ").append(e.text).append(tagSuffix(e)).append('\n');
            if (e.note != null && !e.note.isBlank()) {
                sb.append("   note: ").append(e.note).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * "Optimize Flat View Log": the lines in flow order, with a [project], file and method heading printed
     * whenever it changes from the previous line, e.g.
     * <pre>
     * [TravelBooking]
     *
     * BookingService.java
     *
     * bookTrip()
     *
     * 21 | if (customerName == null) {
     *    note: Storing the Data
     *
     * 27 | if (nights >= days) {
     * </pre>
     */
    static String toOptimizedLog(Collection<FlowEntry> subset) {
        StringBuilder sb = new StringBuilder();
        String project = null, file = null, method = null;
        boolean afterNote = false;
        for (FlowEntry e : subset) {
            String p = e.projectName == null ? "" : e.projectName;
            String m = methodLabel(e);
            boolean newProject = !p.equals(project);
            boolean newFile = newProject || !e.fileUrl.equals(file);
            boolean newMethod = newFile || !m.equals(method);

            if (newProject) {
                if (!sb.isEmpty()) sb.append("\n\n"); // two empty lines between projects
                if (!p.isEmpty()) sb.append('[').append(p).append("]\n\n");
            } else if (newFile || newMethod || afterNote) {
                sb.append('\n'); // one empty line between files, methods, and after a note
            }
            if (newFile) sb.append(e.fileName).append("\n\n");
            if (newMethod) sb.append(m).append("\n\n");

            sb.append(lineNumber(e)).append(" | ").append(e.text).append(tagSuffix(e)).append('\n');
            afterNote = hasText(e.note);
            if (afterNote) sb.append("   note: ").append(e.note).append('\n');

            project = p;
            file = e.fileUrl;
            method = m;
        }
        return sb.toString();
    }

    /** True when the entries come from more than one project (then Flat View names the project). */
    public static boolean spansProjects(Collection<FlowEntry> entries) {
        return entries.stream().map(e -> e.projectName == null ? "" : e.projectName).distinct().count() > 1;
    }

    /** "  [bug]" for a tagged line, "" otherwise. */
    public static String tagSuffix(FlowEntry e) {
        FlowTag tag = FlowTag.of(e);
        return tag == null ? "" : "  [" + tag.id + "]";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
