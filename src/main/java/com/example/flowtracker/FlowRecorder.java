package com.example.flowtracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * "Record": while on, every line where the debugger stops (a breakpoint or a step) is added to the active
 * flow, at the bottom, in the order it ran. Works for debug sessions that are already running and for
 * ones started later. Sync is turned off when recording starts, so the execution order is kept.
 */
@Service(Service.Level.PROJECT)
public final class FlowRecorder implements Disposable {
    private final Project project;
    private @Nullable Disposable recording;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public FlowRecorder(Project project) {
        this.project = project;
    }

    public static FlowRecorder get(Project project) {
        return project.getService(FlowRecorder.class);
    }

    public boolean isRecording() {
        return recording != null;
    }

    public void toggle() {
        if (isRecording()) stop(); else start();
    }

    public void start() {
        if (recording != null) return;
        Disposable d = Disposer.newDisposable("FlowTrackerRecording");
        Disposer.register(this, d);
        recording = d;
        FlowStore.get(project).setSyncFiles(false);

        for (XDebugSession session : XDebuggerManager.getInstance(project).getDebugSessions()) watch(session, d);
        project.getMessageBus().connect(d).subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
            @Override
            public void processStarted(@NotNull XDebugProcess debugProcess) {
                watch(debugProcess.getSession(), d);
            }
        });
        fire();
    }

    public void stop() {
        Disposable d = recording;
        if (d == null) return;
        recording = null;
        Disposer.dispose(d);
        fire();
    }

    private void watch(XDebugSession session, Disposable parent) {
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionPaused() {
                XSourcePosition pos = session.getCurrentPosition();
                if (pos != null) ApplicationManager.getApplication().invokeLater(() -> record(pos));
            }
        }, parent);
        // Already stopped at a line when recording starts: take that line too.
        if (session.isSuspended() && session.getCurrentPosition() != null) {
            XSourcePosition pos = session.getCurrentPosition();
            ApplicationManager.getApplication().invokeLater(() -> record(pos));
        }
    }

    private void record(XSourcePosition pos) {
        if (!isRecording() || project.isDisposed() || !FlowLang.isSupported(pos.getFile())) return;
        Document doc = FileDocumentManager.getInstance().getDocument(pos.getFile());
        if (doc != null) FlowCapture.addLine(project, doc, pos.getLine(), false);
    }

    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    private void fire() {
        for (Runnable r : listeners) r.run();
    }

    @Override
    public void dispose() {
        recording = null;
    }
}
