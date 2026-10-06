package com.nicch.mediatar;

import java.io.File;
import java.util.prefs.Preferences;

/**
 * Manages default directories for projects, rendered video exports, and media imports,
 * and persists user settings across application restarts.
 */
public class WorkspaceStorage {

    private static final String PREF_THEME = "ui_theme";
    private static final String PREF_PROJECTS_DIR = "projects_directory";
    private static final String PREF_EXPORT_DIR = "export_directory";
    private static final String PREF_IMPORT_DIR = "last_import_directory";

    private static final Preferences prefs = Preferences.userNodeForPackage(MMediatar.class);

    /** Initializes default directories on startup, creating them if they do not exist. */
    public static void initDefaults() {
        getProjectsDirectory();
        getExportDirectory();
    }

    /**
     * Returns the default projects directory (~/Documents/MMediatar/Projects),
     * creating it if necessary.
     */
    public static File getProjectsDirectory() {
        String saved = prefs.get(PREF_PROJECTS_DIR, null);
        if (saved != null) {
            File dir = new File(saved);
            if (dir.exists() || dir.mkdirs()) {
                return dir;
            }
        }

        String userHome = System.getProperty("user.home");
        File docs = new File(userHome, "Documents");
        File target = docs.exists()
                ? new File(docs, "MMediatar" + File.separator + "Projects")
                : new File(userHome, "MMediatar" + File.separator + "Projects");

        if (!target.exists()) {
            target.mkdirs();
        }
        return target;
    }

    public static void setProjectsDirectory(File dir) {
        if (dir != null && dir.exists()) {
            prefs.put(PREF_PROJECTS_DIR, dir.getAbsolutePath());
        }
    }

    /**
     * Returns the default export directory (~/Videos/MMediatar/Exports),
     * creating it if necessary.
     */
    public static File getExportDirectory() {
        String saved = prefs.get(PREF_EXPORT_DIR, null);
        if (saved != null) {
            File dir = new File(saved);
            if (dir.exists() || dir.mkdirs()) {
                return dir;
            }
        }

        String userHome = System.getProperty("user.home");
        File videos = new File(userHome, "Videos");
        File target = videos.exists()
                ? new File(videos, "MMediatar" + File.separator + "Exports")
                : new File(userHome, "MMediatar" + File.separator + "Exports");

        if (!target.exists()) {
            target.mkdirs();
        }
        return target;
    }

    public static void setExportDirectory(File dir) {
        if (dir != null && dir.exists()) {
            prefs.put(PREF_EXPORT_DIR, dir.getAbsolutePath());
        }
    }

    /** Returns the last directory from which media was imported. */
    public static File getLastImportDirectory() {
        String saved = prefs.get(PREF_IMPORT_DIR, null);
        if (saved != null) {
            File dir = new File(saved);
            if (dir.exists()) {
                return dir;
            }
        }

        String userHome = System.getProperty("user.home");
        File videos = new File(userHome, "Videos");
        if (videos.exists()) return videos;
        return new File(userHome);
    }

    public static void setLastImportDirectory(File dir) {
        if (dir != null && dir.exists()) {
            prefs.put(PREF_IMPORT_DIR, dir.getAbsolutePath());
        }
    }

    /** Returns the persisted theme name or default (SOLARIZED). */
    public static String getSavedTheme() {
        return prefs.get(PREF_THEME, "SOLARIZED");
    }

    public static void saveTheme(String themeName) {
        if (themeName != null) {
            prefs.put(PREF_THEME, themeName);
        }
    }
}
