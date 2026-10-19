package com.example.flowtracker.diagram;

import com.example.flowtracker.code.FlowCodeSearch;
import com.example.flowtracker.ui.FlowButton;
import com.example.flowtracker.ui.FlowTheme;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.TreeSet;

/**
 * The code panel under the Flow Diagram: the selected method's lines in the flow (the rest of its file folded
 * away), or with View Full Code the whole file at that method. A read-only editor with the IDE's colors; the
 * flow's lines are highlighted either way.
 */
final class FlowDiagramCode extends JPanel implements Disposable {
    private static final JBColor FLOW_LINE = new JBColor(0xE3EDFF, 0x25324D);

    private final Project project;
    private final JBLabel title = new JBLabel();
    private final JBLabel info = new JBLabel();
    private final FlowButton full = new FlowButton("View Full Code", FlowTheme.ICON_CODE, FlowButton.Style.OUTLINE);
    private final JPanel body = new JPanel(new BorderLayout());
    private final JBLabel placeholder = new JBLabel("", SwingConstants.CENTER);
    private @Nullable EditorEx viewer;
    private @Nullable String viewerUrl;
    private boolean showFull;
    /** What is shown now (to skip repeated updates). */
    private String shown = "";

    private String url = "";
    private String fileName = "";
    private List<Integer> lines = List.of();

    FlowDiagramCode(Project project) {
        super(new BorderLayout(0, JBUI.scale(4)));
        this.project = project;
        title.setFont(JBUI.Fonts.label().asBold());
        info.setForeground(FlowTheme.FG_MUTED);
        placeholder.setForeground(FlowTheme.FG_MUTED);
        full.addActionListener(e -> {
            showFull = !showFull;
            shown = "";
            render();
        });
        JPanel names = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        names.setOpaque(false);
        names.add(title);
        names.add(info);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(names, BorderLayout.CENTER);
        header.add(full, BorderLayout.EAST);
        body.setBorder(JBUI.Borders.customLine(FlowTheme.CARD_BORDER));
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        setBorder(JBUI.Borders.emptyTop(8));
        showMessage("Code", "Click a method in the diagram to see its lines of code here.");
    }

    /** A message instead of code (no method selected, a custom method, a missing file). */
    void showMessage(String heading, String message) {
        String key = "message\u0000" + heading + "\u0000" + message;
        if (key.equals(shown)) return;
        shown = key;
        title.setText(heading);
        info.setText("");
        full.setEnabled(false);
        full.setSelected(false);
        placeholder.setText(message);
        body.removeAll();
        body.add(placeholder, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    /** A method's code: its file and its lines in the flow (0-based). */
    void showMethod(String heading, String fileName, String url, List<Integer> lines) {
        String key = "method\u0000" + heading + "\u0000" + url + "\u0000" + lines + "\u0000" + showFull;
        if (key.equals(shown)) return;
        title.setText(heading);
        info.setText(fileName + " · " + lines.size() + (lines.size() == 1 ? " line" : " lines") + " in the flow");
        this.url = url;
        this.fileName = fileName;
        this.lines = lines;
        render();
        shown = key;
    }

    private void render() {
        VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(url);
        Document doc = vf == null ? null : FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            showMessage(title.getText(), fileName + " was not found in the project.");
            return;
        }
        if (lines.isEmpty()) {
            showMessage(title.getText(), "This method has no lines in the flow yet.");
            return;
        }
        full.setEnabled(true);
        full.setSelected(showFull);
        full.setText(showFull ? "Flow Lines Only" : "View Full Code");
        full.setToolTipText(showFull ? "Show only the flow's lines of this method"
                : "Show the whole file, at this method (the flow's lines stay highlighted)");
        if (viewer == null || !url.equals(viewerUrl)) {
            release();
            viewer = (EditorEx) EditorFactory.getInstance().createViewer(doc, project);
            viewer.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(project, vf));
            EditorSettings s = viewer.getSettings();
            s.setLineNumbersShown(true);
            s.setFoldingOutlineShown(false);
            s.setLineMarkerAreaShown(false);
            s.setGutterIconsShown(false);
            s.setIndentGuidesShown(false);
            s.setAdditionalLinesCount(0);
            s.setAdditionalColumnsCount(1);
            s.setCaretRowShown(false);
            s.setRightMarginShown(false);
            viewer.setBorder(JBUI.Borders.empty());
            viewerUrl = url;
        }
        EditorEx v = viewer;
        TreeSet<Integer> flow = new TreeSet<>();
        for (int l : lines) if (l >= 0 && l < doc.getLineCount()) flow.add(l);

        v.getMarkupModel().removeAllHighlighters();
        TextAttributes mark = new TextAttributes();
        mark.setBackgroundColor(FLOW_LINE);
        for (int l : flow) v.getMarkupModel().addLineHighlighter(l, HighlighterLayer.SELECTION - 1, mark);

        // Flow lines only: everything else is folded away (click a "⋯" to open it).
        FoldingModel folds = v.getFoldingModel();
        folds.runBatchFoldingOperation(() -> {
            for (FoldRegion r : folds.getAllFoldRegions()) folds.removeFoldRegion(r);
            if (showFull) return;
            int next = 0;
            for (int l : flow) {
                if (l > next) fold(folds, doc, next, l - 1);
                next = l + 1;
            }
            if (next < doc.getLineCount()) fold(folds, doc, next, doc.getLineCount() - 1);
        });

        int target = flow.isEmpty() ? 0 : flow.first();
        if (showFull) {
            int[] range = FlowCodeSearch.methodRange(project, url, target);
            if (range != null) target = range[0];
        }
        body.removeAll();
        body.add(v.getComponent(), BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
        int line = target;
        SwingUtilities.invokeLater(() -> {
            if (viewer != v) return;
            v.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, 0));
            if (showFull) v.getScrollingModel().scrollTo(new LogicalPosition(line, 0), ScrollType.CENTER_UP);
            else v.getScrollingModel().scrollVertically(0);
        });
    }

    private static void fold(FoldingModel folds, Document doc, int first, int last) {
        int n = last - first + 1;
        FoldRegion r = folds.addFoldRegion(doc.getLineStartOffset(first), doc.getLineEndOffset(last),
                "⋯ " + n + (n == 1 ? " line" : " lines"));
        if (r != null) r.setExpanded(false);
    }

    private void release() {
        if (viewer != null) {
            EditorFactory.getInstance().releaseEditor(viewer);
            viewer = null;
            viewerUrl = null;
        }
    }

    @Override
    public void dispose() {
        release();
    }
}
