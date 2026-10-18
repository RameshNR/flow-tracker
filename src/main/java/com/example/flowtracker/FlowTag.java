package com.example.flowtracker;

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;

/** Colour tags a saved line can carry. Stored in {@link FlowEntry#tag} by {@link #id}. */
enum FlowTag {
    BUG("bug", "Bug", "🐞", new JBColor(0xD64550, 0xE5636D), new JBColor(0xFCE8EA, 0x3A1F25)),
    IMPORTANT("important", "Important", "⭐", new JBColor(0xC27C0E, 0xE5A83B), new JBColor(0xFDF1DC, 0x3A2E17)),
    QUESTION("question", "Question", "❓", new JBColor(0x7A4FD6, 0xA987F0), new JBColor(0xEFE8FC, 0x2B2240));

    final String id;
    final String label;
    final String emoji;
    final Color fg;
    final Color bg;

    FlowTag(String id, String label, String emoji, Color fg, Color bg) {
        this.id = id;
        this.label = label;
        this.emoji = emoji;
        this.fg = fg;
        this.bg = bg;
    }

    /** The tag of a line, or null when it has none (or an unknown one). */
    static @Nullable FlowTag of(FlowEntry e) {
        return byId(e.tag);
    }

    static @Nullable FlowTag byId(@Nullable String id) {
        if (id == null || id.isBlank()) return null;
        for (FlowTag t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
