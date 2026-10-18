package com.example.flowtracker;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.List;
import java.util.Objects;

/** Works out which (sub)project a file belongs to, so files with the same name can be told apart. */
final class FlowProjects {
    private FlowProjects() {}

    private static final List<String> BUILD_FILES =
            List.of("build.gradle", "build.gradle.kts", "pom.xml", "build.xml", "settings.gradle", "settings.gradle.kts");

    /**
     * Name of the closest folder above the file that has its own build file (a Gradle or Maven project),
     * e.g. "TravelBooking" for TravelWorkspace/TravelBooking/src/main/java/.../Hotel.java.
     * Falls back to the IDE project's name.
     */
    static String nameFor(Project project, VirtualFile file) {
        String base = project.getBasePath();
        for (VirtualFile dir = file.getParent(); dir != null; dir = dir.getParent()) {
            for (String b : BUILD_FILES) {
                if (dir.findChild(b) != null) return dir.getName();
            }
            if (Objects.equals(dir.getPath(), base)) break; // never look above the opened project
        }
        return project.getName();
    }

    /** Fills in the project name of entries saved before it was recorded. Returns true if anything changed. */
    static boolean backfill(Project project, List<FlowEntry> entries) {
        boolean changed = false;
        for (FlowEntry e : entries) {
            if (e.projectName != null && !e.projectName.isEmpty()) continue;
            VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(e.fileUrl);
            e.projectName = vf == null ? project.getName() : nameFor(project, vf);
            changed = true;
        }
        return changed;
    }
}
