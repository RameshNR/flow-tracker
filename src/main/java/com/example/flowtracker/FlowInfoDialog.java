package com.example.flowtracker;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

/** "Info" from the settings popup: what Flow Tracker does and how to use every part of it. */
final class FlowInfoDialog extends DialogWrapper {

    FlowInfoDialog(Project project) {
        super(project, false);
        setTitle("Flow Tracker — Info");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JEditorPane text = new JEditorPane("text/html", html());
        text.setEditable(false);
        text.setOpaque(false);
        text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        text.setFont(JBUI.Fonts.label());
        text.setBorder(JBUI.Borders.empty(4, 8));
        text.setCaretPosition(0);

        JBScrollPane scroll = new JBScrollPane(text);
        scroll.setBorder(JBUI.Borders.empty());
        scroll.setPreferredSize(new Dimension(JBUI.scale(580), JBUI.scale(400)));

        // Screenshots on top (when the plugin has them), the help text below.
        FlowCarousel carousel = new FlowCarousel();
        if (!carousel.hasSlides()) return scroll;
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        panel.add(carousel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }

    private static String version() {
        IdeaPluginDescriptor d = PluginManagerCore.getPlugin(PluginId.getId("io.github.rameshnr.flowtracker"));
        return d == null || d.getVersion() == null ? "dev" : d.getVersion();
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String html() {
        String muted = hex(FlowTheme.FG_MUTED);
        String accent = hex(FlowTheme.ACCENT);
        String fg = hex(UIUtil.getLabelForeground());
        return "<html><body style='color:" + fg + "'>"
                + "<h2 style='margin-bottom:2px'>🧭 Flow Tracker <span style='color:" + muted
                + ";font-weight:normal'>v" + version() + "</span></h2>"
                + "<p style='color:" + muted + "'>Collect the lines of a code path while you read it, keep them in "
                + "call order, add notes, and copy the whole flow in one click.</p>"

                + section(accent, "Add lines")
                + "<ul><li>Hover a line in a supported file and click the <b>+</b> icon in the gutter. Click the green "
                + "marker to remove the line again.</li>"
                + "<li>Or right-click in the editor \u2192 <b>Add to Flow Tracker</b> (<b>Ctrl+Alt+Shift+A</b>): the caret "
                + "line, or every selected line, as one undo step. On a saved line it reads <b>Remove from Flow "
                + "Tracker</b>.</li>"
                + "<li>Saved lines show their <b>step number</b> in the gutter; hover it for the method, tag and note. "
                + "They follow your edits (in both views, while the file is open).</li>"
                + "<li><b>Add Bottom / Add Top</b> chooses where new lines go (↓ end, ↑ start).</li>"
                + "<li><b>Undo / Redo</b> (toolbar) revert adding, deleting and clearing lines, wherever it "
                + "happened (editor gutter or panel). Hover them to see what they will change.</li>"
                + "<li>Each line remembers its file, class, method, line number and project.</li></ul>"

                + section(accent, "Supported files")
                + "<ul><li><b>Java, Groovy</b> (<code>.java .groovy .gradle</code>) and <b>Kotlin</b> "
                + "(<code>.kt .kts</code>): class and method from the code; Sync and the diagrams follow calls.</li>"
                + "<li><b>JavaScript / TypeScript</b> (<code>.js .jsx .mjs .cjs .ts .tsx</code>): the enclosing "
                + "class and function, or the callback, e.g. <i>addEventListener(\"submit\") callback</i>. Calls "
                + "between saved functions get arrows.</li>"
                + "<li><b>JSP / tag files</b> (<code>.jsp .jspf .jspx .tag .tagx</code>): the method in a "
                + "<code>&lt;%! %&gt;</code> declaration, <i>[scriptlet]</i>, <i>[script]</i> or the element, e.g. "
                + "<i>form#searchForm</i>. Calls from the page's Java code to saved Java methods get arrows "
                + "(matched by name and number of arguments).</li>"
                + "<li><b>TLD</b>: the tag or function, e.g. <i>tag status</i>. <b>XML / HTML</b>: the nearest "
                + "element with an id or name. <b>YAML</b>: the parent keys. <b>Properties, SQL</b>: the file.</li>"
                + "<li>Lines outside methods are grouped as <i>[fields]</i>, <i>[imports]</i> or <i>[class]</i>.</li>"
                + "<li>IntelliJ Community can't parse JavaScript or JSP, so for web and config files names are read "
                + "from the text (a best effort).</li></ul>"

                + section(accent, "Record while debugging")
                + "<ul><li>Click <b>Record</b>, then debug as usual: every line where the debugger stops "
                + "(a breakpoint or a step) is added at the bottom, in the order it ran. Click <b>Recording</b> to "
                + "stop.</li>"
                + "<li>It also picks up sessions that are already running, and turns Sync off so the execution "
                + "order is kept.</li></ul>"

                + section(accent, "Flows")
                + "<ul><li>Keep several named flows per project. The flow button at the top opens the <b>Flows</b> "
                + "popup: click a flow to show it (the current one is checked, with its line count).</li>"
                + "<li><b>Drag</b> a flow by its handle (or select it and press <b>Ctrl+Up / Ctrl+Down</b>) to "
                + "reorder. Copy All Flows copies the flows in this order.</li>"
                + "<li><b>New Flow</b> and <b>Rename Flow</b> start the name as <i>Flow 1: </i>, so you type the "
                + "name after it (<i>Flow 1: Checkout bug</i>). You can delete the prefix too.</li>"
                + "<li><b>Delete Flow</b> moves the flow to the Recycle Bin; deleting the only flow empties it.</li>"
                + "<li><b>Export Flow</b> saves the flow as a <code>.flow.json</code> file with paths relative to "
                + "the project. <b>Import Flow</b> opens it as a new flow on any machine; lines whose file isn't in "
                + "the project are marked <i>file missing</i>.</li>"
                + "<li>Undo / Redo history belongs to the flow it was made in.</li></ul>"

                + section(accent, "Recycle Bin")
                + "<ul><li>Everything you delete goes here: single lines (panel or gutter), Clear, and deleted "
                + "flows. Open it from <b>Settings \u2192 Recycle Bin</b>.</li>"
                + "<li><b>Restore</b> (or double-click) puts lines back into their flow at their old places (a deleted "
                + "flow comes back as a flow). <b>Delete Permanently</b> or the <b>Delete</b> key removes the selected "
                + "items for good, after asking; <b>Empty Recycle Bin</b> removes everything.</li>"
                + "<li>Undoing a delete takes it out of the bin. The bin keeps the latest 500 items.</li></ul>"

                + section(accent, "Views and search")
                + "<ul><li><b>Grouped View</b> — file → method → lines, with a project tag on each file.</li>"
                + "<li><b>Flat View</b> — one sequence in flow order, with step numbers.</li>"
                + "<li>The <b>search box</b> shows only lines whose code, file, class, method, note, project or tag "
                + "contains the text (or whose line number matches). Copy then copies only those lines.</li></ul>"

                + section(accent, "Working with rows")
                + "<ul><li><b>Hover a row</b> for its action bar: ↑ ↓ move (only when there is something "
                + "to swap with) | Copy and Note (code lines only) | Delete.</li>"
                + "<li><b>Drag</b> a file, method or line to move it. <b>Double-click</b> a line to open it.</li>"
                + "<li><b>Right-click</b> for Move Up/Down, Copy, Add/Edit Note, Jump to Source, Walk from Here, "
                + "Tag and Delete.</li>"
                + "<li><b>Tags</b> 🐞 Bug, ⭐ Important and ❓ Question color-mark lines and appear in every copy "
                + "format.</li>"
                + "<li>A <b>warning</b> marks lines that no longer match the code (file missing, moved to line N, "
                + "code changed). Right-click \u2192 <b>Re-link to Code</b> fixes one; <b>Re-link All Moved Lines</b> "
                + "fixes them all.</li>"
                + "<li><b>Ctrl+C</b> copies the selection, <b>Delete</b> removes it.</li></ul>"

                + section(accent, "Walk-through")
                + "<ul><li><b>Next Step / Previous Step</b> (toolbar, or <b>Alt+]</b> / <b>Alt+[</b> anywhere) open "
                + "each saved line in the editor in turn, with the step number, method, tag and note in a hint.</li>"
                + "<li>Select a line (or right-click \u2192 <b>Walk from Here</b>) to continue from it. Also under "
                + "Tools \u2192 Flow Tracker; rebind the keys in Settings \u2192 Keymap.</li></ul>"

                + section(accent, "Sync (call order)")
                + "<ul><li>When on, lines are kept in call order automatically: a method first, and the methods "
                + "it calls right after the line that calls them, across files (JSP pages calling Java too).</li>"
                + "<li>Moving anything by hand turns Sync off so your order is kept. Click <b>Sync</b> in the "
                + "toolbar (or the switch in Settings) to re-sort.</li>"
                + "<li>Only methods that have saved lines are linked. A configured project JDK gives the most "
                + "accurate results.</li></ul>"

                + section(accent, "Settings and copy formats")
                + "<ul><li><b>Default copy</b> — indented text (Grouped) or numbered steps (Flat).</li>"
                + "<li><b>Optimize Flat View Log</b> — in Flat View, a compact log with [project], file and "
                + "method headings and <code>line | code</code> rows.</li>"
                + "<li><b>Copy as Markdown</b> — a .md document: a summary table, Mermaid diagrams of the real "
                + "calls, each method's lines as a table (line number, syntax-highlighted code, tag and note; "
                + "GitHub shows the code without colors), and at the end an "
                + "<b>Original Log</b>: the plain copy of the current view (Grouped or Flat). Only one of the two "
                + "copy options can be on.</li>"
                + "<li>Diagrams title each box with the file name (e.g. <code>travel.js</code>), give code that "
                + "doesn't call across its own diagram, and leave out fields and imports.</li>"
                + "<li><b>Sequence Diagram</b> — draw the calls as a Mermaid sequence diagram instead of a "
                + "flowchart, following the real calls whatever order the lines are listed in.</li>"
                + "<li><b>Open Markdown</b> (next to Copy) — opens the flow as a Markdown file with the preview, "
                + "diagrams included; all flows when Copy All Flows is on.</li>"
                + "<li><b>Copy All Flows</b> — the Copy button copies every flow, one after the other under its "
                + "name, in the Flows popup's order. Ctrl+C still copies the selected lines.</li>"
                + "<li><b>Recycle Bin</b> and <b>Info</b> open from the same popup.</li></ul>"

                + section(accent, "Storage")
                + "<p>Flows, settings and the Recycle Bin are saved per project in <code>.idea/workspace.xml</code> "
                + "and survive restarts. Nothing leaves your machine unless you export a flow.</p>"
                + "</body></html>";
    }

    private static String section(String accent, String title) {
        return "<h3 style='color:" + accent + ";margin-top:10px;margin-bottom:0'>" + title + "</h3>";
    }
}
