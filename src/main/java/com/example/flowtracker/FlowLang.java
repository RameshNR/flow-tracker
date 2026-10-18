package com.example.flowtracker;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;

import java.util.Locale;
import java.util.Map;

/**
 * The file types Flow Tracker works in and how each is understood:
 * <ul>
 *   <li>Java and Groovy through Java PSI, Kotlin through UAST (the IDE's common syntax tree for JVM
 *       languages, so no compile-time dependency on the Kotlin plugin is needed). Sync follows their calls.</li>
 *   <li>Web and config files (JSP, JavaScript / TypeScript, TLD, XML, HTML, properties, YAML, SQL) through
 *       {@link FlowOutline}, which finds the enclosing function, tag or element from the text, since the
 *       Community edition has no parser for most of them.</li>
 * </ul>
 */
final class FlowLang {
    private FlowLang() {}

    enum Kind {
        JAVA("java"), KOTLIN("kotlin"), GROOVY("groovy"),
        /** JavaScript / TypeScript, including JSX and TSX. */
        SCRIPT("javascript"),
        /** JSP pages and tag files: markup with Java scriptlets. */
        JSP("jsp"),
        /** XML-like markup: TLD, XML, HTML. */
        MARKUP("xml"),
        /** Line-oriented config and query files. */
        CONFIG("properties");

        final String fence;

        Kind(String fence) {
            this.fence = fence;
        }
    }

    private static final Map<String, Kind> BY_EXTENSION = Map.ofEntries(
            Map.entry("java", Kind.JAVA),
            Map.entry("kt", Kind.KOTLIN), Map.entry("kts", Kind.KOTLIN),
            Map.entry("groovy", Kind.GROOVY), Map.entry("gradle", Kind.GROOVY),
            Map.entry("js", Kind.SCRIPT), Map.entry("jsx", Kind.SCRIPT), Map.entry("mjs", Kind.SCRIPT),
            Map.entry("cjs", Kind.SCRIPT), Map.entry("ts", Kind.SCRIPT), Map.entry("tsx", Kind.SCRIPT),
            Map.entry("jsp", Kind.JSP), Map.entry("jspf", Kind.JSP), Map.entry("jspx", Kind.JSP),
            Map.entry("tag", Kind.JSP), Map.entry("tagx", Kind.JSP),
            Map.entry("tld", Kind.MARKUP), Map.entry("xml", Kind.MARKUP), Map.entry("html", Kind.MARKUP),
            Map.entry("htm", Kind.MARKUP), Map.entry("xhtml", Kind.MARKUP),
            Map.entry("properties", Kind.CONFIG), Map.entry("yaml", Kind.CONFIG), Map.entry("yml", Kind.CONFIG),
            Map.entry("sql", Kind.CONFIG));

    /** Code fence names that differ from the kind's default. */
    private static final Map<String, String> FENCE_BY_EXTENSION = Map.of(
            "jsx", "jsx", "ts", "typescript", "tsx", "tsx", "html", "html", "htm", "html", "xhtml", "html",
            "yaml", "yaml", "yml", "yaml", "sql", "sql");

    /** The kind of a file, or null when Flow Tracker doesn't support it. */
    static @Nullable Kind kind(String fileName) {
        return BY_EXTENSION.get(extension(fileName));
    }

    static boolean isSupported(@Nullable VirtualFile vf) {
        return vf != null && isSupportedName(vf.getName());
    }

    static boolean isSupportedName(String fileName) {
        return kind(fileName) != null;
    }

    static boolean isJava(String fileName) {
        return kind(fileName) == Kind.JAVA;
    }

    static boolean isKotlin(String fileName) {
        return kind(fileName) == Kind.KOTLIN;
    }

    /** Java and Groovy: their PSI has real PsiMethod / PsiClass elements. */
    static boolean usesJavaPsi(String fileName) {
        Kind k = kind(fileName);
        return k == Kind.JAVA || k == Kind.GROOVY;
    }

    /** File name without its extension, e.g. "BookingService" for BookingService.kt. */
    static String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Language name for Markdown code fences. */
    static String fence(String fileName) {
        String special = FENCE_BY_EXTENSION.get(extension(fileName));
        if (special != null) return special;
        Kind k = kind(fileName);
        return k == null ? "" : k.fence;
    }

    /** The innermost UAST element at or above a PSI element (leaf tokens have none of their own). */
    static @Nullable UElement uElementAt(@Nullable PsiElement el) {
        for (PsiElement p = el; p != null && !(p instanceof PsiFile); p = p.getParent()) {
            UElement u = UastContextKt.toUElement(p);
            if (u != null) return u;
        }
        return null;
    }

    /** The closest UAST element of the given type at or above u. */
    static <T extends UElement> @Nullable T uParent(@Nullable UElement u, Class<T> type) {
        for (UElement x = u; x != null; x = x.getUastParent()) {
            if (type.isInstance(x)) return type.cast(x);
        }
        return null;
    }
}
