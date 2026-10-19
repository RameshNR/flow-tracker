package com.example.flowtracker.editor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/** Attaches the hover handler to every normal code editor. */
public final class FlowEditorFactoryListener implements EditorFactoryListener {
    private static final Key<Disposable> KEY = Key.create("flowtracker.disposable");

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        if (editor.getProject() == null || editor.getEditorKind() != EditorKind.MAIN_EDITOR) return;

        Disposable disposable = Disposer.newDisposable("FlowTrackerHover");
        LineHoverHandler handler = new LineHoverHandler(editor, disposable);
        Disposer.register(disposable, handler::dispose);

        var multicaster = EditorFactory.getInstance().getEventMulticaster();
        multicaster.addEditorMouseMotionListener(handler, disposable);
        multicaster.addEditorMouseListener(handler, disposable);
        editor.putUserData(KEY, disposable);
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        Editor editor = event.getEditor();
        Disposable d = editor.getUserData(KEY);
        if (d != null) {
            editor.putUserData(KEY, null);
            Disposer.dispose(d);
        }
    }
}
