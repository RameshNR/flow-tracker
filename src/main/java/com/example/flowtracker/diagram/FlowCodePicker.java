package com.example.flowtracker.diagram;

import com.example.flowtracker.code.FlowCodeSearch;
import com.example.flowtracker.code.FlowLang;
import com.example.flowtracker.copy.CodeHighlighter;
import com.example.flowtracker.ui.FlowTheme;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The pickers of the Flow Diagram window: a class (searched by name or pattern), a method of that class, and
 * the method's lines to add to the flow.
 */
final class FlowCodePicker {
    private FlowCodePicker() {}

    /**
     * What the class picker returns: a class (or file) of the project, or the name typed for a custom class (one
     * that isn't in the code).
     */
    record ClassChoice(@Nullable FlowCodeSearch.ClassHit hit, @Nullable String customName) {
        boolean isCustom() { return hit == null; }
    }

    /**
     * Asks for a class of the project, listing the matches as the user types (by name, {@code *} / {@code ?}
     * wildcards or a {@code /regex/}). A name no class has can be taken as a custom class. Null when cancelled.
     *
     * @param title       the window title, e.g. "Add Class" or "Rename Class"
     * @param query       the search to start with (e.g. the class's current name)
     * @param customCheck why a typed name can't be a custom class (null when it can)
     */
    static @Nullable ClassChoice pickClass(Project project, Component parent, String title, String query,
                                           Function<String, @Nullable String> customCheck) {
        ClassDialog d = new ClassDialog(project, parent, title, customCheck);
        if (!query.isEmpty()) {
            d.search.setText(query); // starts the search
            d.search.getTextEditor().selectAll();
        }
        if (!d.showAndGet()) return null;
        Object v = d.list.getSelectedValue();
        if (v instanceof FlowCodeSearch.ClassHit h) return new ClassChoice(h, null);
        return v instanceof Custom c ? new ClassChoice(null, c.name()) : null;
    }

    /** The list row that takes the typed name as a custom class. */
    private record Custom(String name) {}

    /** Asks for a method of the class; null when cancelled. */
    static @Nullable FlowCodeSearch.MethodHit pickMethod(Project project, Component parent, FlowCodeSearch.ClassHit cls) {
        List<FlowCodeSearch.MethodHit> methods = ReadAction.computeBlocking(() -> FlowCodeSearch.methods(project, cls));
        MethodDialog d = new MethodDialog(project, parent, cls, methods);
        return d.showAndGet() ? d.list.getSelectedValue() : null;
    }

    /**
     * Shows the method's code and asks which lines to add. Returns the 0-based lines picked (none picked: the
     * method's first line), or null when cancelled.
     *
     * @param saved 0-based lines of this file the flow (or the diagram) has already
     */
    static @Nullable List<Integer> pickLines(Project project, Component parent, FlowCodeSearch.ClassHit cls,
                                             FlowCodeSearch.MethodHit method, Set<Integer> saved) {
        List<FlowCodeSearch.CodeLine> lines = ReadAction.computeBlocking(() -> FlowCodeSearch.lines(cls.url(), method.start(), method.end()));
        LinesDialog d = new LinesDialog(project, parent, cls, method, lines, saved);
        if (!d.showAndGet()) return null;
        List<Integer> picked = new ArrayList<>(d.checked);
        if (picked.isEmpty()) picked.add(method.declLine());
        picked.sort(null);
        return picked;
    }

    // ---------- class ----------

    private static final class ClassDialog extends DialogWrapper {
        private final Project project;
        private final SearchTextField search = new SearchTextField(false);
        private final DefaultListModel<Object> model = new DefaultListModel<>();
        final JBList<Object> list = new JBList<>(model);
        private final JBLabel status = new JBLabel();
        private final Timer debounce = new Timer(250, e -> runSearch());
        private final String okText;
        private final Function<String, @Nullable String> customCheck;
        /** The classes found for the current search. */
        private List<FlowCodeSearch.ClassHit> hits = List.of();
        private int generation;

        ClassDialog(Project project, Component parent, String title, Function<String, @Nullable String> customCheck) {
            super(project, parent, false, IdeModalityType.IDE);
            this.project = project;
            this.customCheck = customCheck;
            this.okText = title.startsWith("Add") ? "Add" : "OK";
            search.getTextEditor().getAccessibleContext().setAccessibleName("Class or file name");
            setTitle(title);
            setOKButtonText(okText);
            debounce.setRepeats(false);
            Disposer.register(getDisposable(), debounce::stop);
            init();
            updateOk();
        }

        @Override
        protected JComponent createCenterPanel() {
            search.getTextEditor().getEmptyText().setText("Class or file name, e.g. Booking, *Cruise*.java or /Book.*Service/");
            search.addDocumentListener(new DocumentAdapter() {
                @Override protected void textChanged(@NotNull DocumentEvent e) {
                    fill(); // the custom class row follows the text right away; the search waits for a pause
                    debounce.restart();
                }
            });
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new ColoredListCellRenderer<>() {
                @Override
                protected void customizeCellRenderer(@NotNull JList<?> l, Object value, int index, boolean selected, boolean focus) {
                    if (value instanceof Custom c) {
                        setIcon(FlowTheme.ICON_CLASS_UNKNOWN);
                        append(c.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                        append("  custom class: not in the code (yet)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                        return;
                    }
                    FlowCodeSearch.ClassHit h = (FlowCodeSearch.ClassHit) value;
                    setIcon(h.isFile() ? FileTypeManager.getInstance().getFileTypeByFileName(h.fileName()).getIcon() : AllIcons.Nodes.Class);
                    append(h.name(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    if (!h.isFile()) append("  " + h.fileName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    append("  " + h.path(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    if (!h.projectName().isEmpty()) append("  [" + h.projectName() + "]", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                }
            });
            list.addListSelectionListener(e -> updateOk());
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && list.getSelectedValue() != null) doOKAction();
                }
            });
            // Up / Down in the search box move through the results.
            search.getTextEditor().registerKeyboardAction(e -> move(1), KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);
            search.getTextEditor().registerKeyboardAction(e -> move(-1), KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), JComponent.WHEN_FOCUSED);

            status.setForeground(UIUtil.getContextHelpForeground());
            status.setText(hint());
            JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(6)));
            panel.add(search, BorderLayout.NORTH);
            panel.add(new JBScrollPane(list), BorderLayout.CENTER);
            panel.add(status, BorderLayout.SOUTH);
            panel.setPreferredSize(new Dimension(JBUI.scale(640), JBUI.scale(420)));
            return panel;
        }

        private static String hint() {
            return "<html>Type at least " + FlowCodeSearch.MIN_LETTERS + " letters of a class or file name. "
                    + "Use * and ? as wildcards (*Cruise*.java), or /…/ for a regular expression (/Book.*Service/). "
                    + "A name no class has can be added as a custom class.</html>";
        }

        private void move(int delta) {
            if (model.isEmpty()) return;
            int i = Math.max(0, Math.min(model.size() - 1, list.getSelectedIndex() + delta));
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
        }

        private void runSearch() {
            String q = search.getText();
            int gen = ++generation;
            Pattern p = FlowCodeSearch.pattern(q);
            if (p == null) {
                hits = List.of();
                fill();
                String error = FlowCodeSearch.patternError(q);
                status.setText(error != null ? error : hint());
                return;
            }
            status.setText(DumbService.isDumb(project) ? "Waiting for indexing to finish…" : "Searching…");
            ReadAction.nonBlocking(() -> FlowCodeSearch.findClasses(project, q, 100))
                    .inSmartMode(project)
                    .expireWith(getDisposable())
                    .finishOnUiThread(ModalityState.stateForComponent(list), found -> {
                        if (gen != generation) return; // a newer search is running
                        hits = found;
                        fill();
                        status.setText(found.isEmpty()
                                ? "No class or file matches “" + q.strip() + "”" + (custom() != null ? ": it can be added as a custom class." : "")
                                : found.size() + (found.size() == 1 ? " match" : found.size() >= 100 ? "+ matches (type more to narrow)" : " matches"));
                    })
                    .submit(AppExecutorUtil.getAppExecutorService());
        }

        /**
         * The typed name as a custom class, or null: when it isn't a valid class name, or a class of the results
         * is called exactly that (then that class is the one meant).
         */
        private @Nullable Custom custom() {
            String name = search.getText().strip();
            if (name.isEmpty() || customCheck.apply(name) != null) return null;
            for (FlowCodeSearch.ClassHit h : hits) {
                String simple = h.className().substring(h.className().lastIndexOf('.') + 1);
                if (simple.equals(name) || h.className().equals(name) || h.fileName().equals(name)) return null;
            }
            return new Custom(name);
        }

        /** Shows the classes found and, last, the typed name as a custom class; keeps the selection when it can. */
        private void fill() {
            Object was = list.getSelectedValue();
            model.clear();
            hits.forEach(model::addElement);
            Custom c = custom();
            if (c != null) model.addElement(c);
            int keep = was instanceof Custom ? (c == null ? -1 : model.size() - 1) : model.indexOf(was);
            if (keep >= 0) list.setSelectedIndex(keep);
            else if (!model.isEmpty()) list.setSelectedIndex(0);
            updateOk();
        }

        private void updateOk() {
            Object v = list.getSelectedValue();
            setOKActionEnabled(v != null);
            setOKButtonText(v instanceof Custom ? okText.equals("Add") ? "Add Custom Class" : "Use Custom Class" : okText);
        }

        @Override
        public @Nullable JComponent getPreferredFocusedComponent() {
            return search.getTextEditor();
        }
    }

    // ---------- method ----------

    private static final class MethodDialog extends DialogWrapper {
        private final List<FlowCodeSearch.MethodHit> all;
        private final SearchTextField filter = new SearchTextField(false);
        private final DefaultListModel<FlowCodeSearch.MethodHit> model = new DefaultListModel<>();
        final JBList<FlowCodeSearch.MethodHit> list = new JBList<>(model);
        private final JBLabel status = new JBLabel();

        MethodDialog(Project project, Component parent, FlowCodeSearch.ClassHit cls, List<FlowCodeSearch.MethodHit> methods) {
            super(project, parent, false, IdeModalityType.IDE);
            this.all = methods;
            setTitle("Add Method / Function to " + cls.name());
            setOKButtonText("Next: Pick Lines…");
            init();
            refill();
        }

        @Override
        protected JComponent createCenterPanel() {
            filter.getTextEditor().getEmptyText().setText("Method name, e.g. search, get*, *Booking* or /^(get|set)/");
            filter.addDocumentListener(new DocumentAdapter() {
                @Override protected void textChanged(@NotNull DocumentEvent e) { refill(); }
            });
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new ColoredListCellRenderer<>() {
                @Override
                protected void customizeCellRenderer(@NotNull JList<? extends FlowCodeSearch.MethodHit> l, FlowCodeSearch.MethodHit m,
                                                     int index, boolean selected, boolean focus) {
                    setIcon(AllIcons.Nodes.Method);
                    append(m.display(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    append("   lines " + (m.start() + 1) + "–" + (m.end() + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            });
            list.addListSelectionListener(e -> setOKActionEnabled(list.getSelectedValue() != null));
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && list.getSelectedValue() != null) doOKAction();
                }
            });
            // Up / Down in the filter move through the methods.
            filter.getTextEditor().registerKeyboardAction(e -> move(1), KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED);
            filter.getTextEditor().registerKeyboardAction(e -> move(-1), KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), JComponent.WHEN_FOCUSED);
            status.setForeground(UIUtil.getContextHelpForeground());
            JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(6)));
            panel.add(filter, BorderLayout.NORTH);
            panel.add(new JBScrollPane(list), BorderLayout.CENTER);
            panel.add(status, BorderLayout.SOUTH);
            panel.setPreferredSize(new Dimension(JBUI.scale(520), JBUI.scale(380)));
            return panel;
        }

        /** The methods matching the filter: the same name / wildcard / regex search as Add Class, from the first letter. */
        private void refill() {
            String q = filter.getText();
            Pattern p = FlowCodeSearch.filterPattern(q);
            String error = FlowCodeSearch.patternError(q);
            model.clear();
            for (FlowCodeSearch.MethodHit m : all) {
                if (error == null && (p == null || FlowCodeSearch.matchesMethod(p, m))) model.addElement(m);
            }
            if (!model.isEmpty()) list.setSelectedIndex(0);
            setOKActionEnabled(list.getSelectedValue() != null);
            if (all.isEmpty()) status.setText("This class has no methods.");
            else if (error != null) status.setText(error);
            else if (p == null) status.setText("<html>Pick a method; the next step shows its code. Filter by name, "
                    + "* and ? wildcards (get*), or /…/ for a regular expression.</html>");
            else status.setText(model.isEmpty() ? "No method matches “" + q.strip() + "”"
                    : model.size() + " of " + all.size() + (all.size() == 1 ? " method" : " methods"));
        }

        private void move(int delta) {
            if (model.isEmpty()) return;
            int i = Math.max(0, Math.min(model.size() - 1, list.getSelectedIndex() + delta));
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
        }

        @Override
        public @Nullable JComponent getPreferredFocusedComponent() {
            return filter.getTextEditor();
        }
    }

    // ---------- lines ----------

    private static final class LinesDialog extends DialogWrapper {
        private final List<FlowCodeSearch.CodeLine> lines;
        private final Set<Integer> saved;
        private final FlowLang.Kind kind;
        final Set<Integer> checked = new LinkedHashSet<>();
        private final JBList<FlowCodeSearch.CodeLine> list;
        private final FlowCodeSearch.MethodHit method;

        LinesDialog(Project project, Component parent, FlowCodeSearch.ClassHit cls, FlowCodeSearch.MethodHit method,
                    List<FlowCodeSearch.CodeLine> lines, Set<Integer> saved) {
            super(project, parent, false, IdeModalityType.IDE);
            this.lines = lines;
            this.saved = saved;
            this.method = method;
            this.kind = FlowLang.kind(cls.fileName());
            this.list = new JBList<>(lines);
            setTitle(cls.name() + "." + method.name() + " – Pick Lines");
            setOKButtonText("Add");
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new LineRenderer());
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) toggle(lines.get(i));
                }
            });
            list.registerKeyboardAction(e -> {
                FlowCodeSearch.CodeLine l = list.getSelectedValue();
                if (l != null) toggle(l);
            }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);

            JBLabel hint = new JBLabel("<html>Click the lines to add to the flow (Space toggles). They are listed in the Markdown "
                    + "copy, not drawn in the diagram. Add without picking any adds the method's first line, so the method "
                    + "is in the flow.</html>");
            hint.setForeground(UIUtil.getContextHelpForeground());
            JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(6)));
            panel.add(hint, BorderLayout.NORTH);
            panel.add(new JBScrollPane(list), BorderLayout.CENTER);
            panel.setPreferredSize(new Dimension(JBUI.scale(760), JBUI.scale(Math.min(560, 120 + lines.size() * 22))));
            return panel;
        }

        private boolean selectable(FlowCodeSearch.CodeLine l) {
            return !l.text().isBlank() && !saved.contains(l.line());
        }

        private void toggle(FlowCodeSearch.CodeLine l) {
            if (!selectable(l)) return;
            if (!checked.remove(l.line())) checked.add(l.line());
            setOKButtonText(checked.isEmpty() ? "Add" : "Add " + checked.size() + (checked.size() == 1 ? " Line" : " Lines"));
            list.repaint();
        }

        @Override
        public @Nullable JComponent getPreferredFocusedComponent() {
            return list;
        }

        /** A check box, the line number and the code, colored like the panel. */
        private final class LineRenderer extends JPanel implements ListCellRenderer<FlowCodeSearch.CodeLine> {
            private final JCheckBox box = new JCheckBox();
            private final SimpleColoredComponent code = new SimpleColoredComponent();

            LineRenderer() {
                super(new BorderLayout(JBUI.scale(4), 0));
                box.setOpaque(false);
                code.setOpaque(false);
                code.setFont(FlowTheme.codeFont());
                add(box, BorderLayout.WEST);
                add(code, BorderLayout.CENTER);
                setBorder(JBUI.Borders.empty(1, 4));
            }

            @Override
            public Component getListCellRendererComponent(JList<? extends FlowCodeSearch.CodeLine> l, FlowCodeSearch.CodeLine line,
                                                          int index, boolean selected, boolean focus) {
                boolean inFlow = saved.contains(line.line());
                box.setSelected(inFlow || checked.contains(line.line()));
                box.setEnabled(selectable(line));
                box.setVisible(!line.text().isBlank());
                setBackground(selected ? UIUtil.getListSelectionBackground(focus) : UIUtil.getListBackground());
                setOpaque(true);
                code.clear();
                code.append(String.format("%4d  ", line.line() + 1), new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FlowTheme.LINE_NO));
                String text = line.text().replace("\t", "    ");
                for (CodeHighlighter.Span s : CodeHighlighter.highlight(text, kind)) {
                    java.awt.Color c = switch (s.token()) {
                        case KEYWORD -> FlowTheme.KEYWORD;
                        case TYPE -> FlowTheme.TYPE;
                        case STRING -> FlowTheme.STRING;
                        case NUMBER -> FlowTheme.NUMBER;
                        case COMMENT -> FlowTheme.COMMENT;
                        case PLAIN -> FlowTheme.CODE_FG;
                    };
                    int style = s.token() == CodeHighlighter.Token.KEYWORD ? SimpleTextAttributes.STYLE_BOLD : SimpleTextAttributes.STYLE_PLAIN;
                    code.append(s.text(), new SimpleTextAttributes(style, c));
                }
                if (inFlow) code.append("   in the flow", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                if (line.line() == method.declLine() && !inFlow && checked.isEmpty()) {
                    code.append("   ← added if no line is picked", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                }
                return this;
            }
        }
    }
}
