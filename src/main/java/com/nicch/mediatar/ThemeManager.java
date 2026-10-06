/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.nicch.mediatar;

/**
 *
 * @author domino
 */

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Centralized theme state, high-contrast palette definitions, dialog styling,
 * and theme persistence across the application.
 */
public class ThemeManager {
    public enum Theme { SOLARIZED, DARK, LIGHT }
    
    public static Theme currentTheme = loadSavedTheme();
    private static final List<Consumer<Theme>> themeListeners = new ArrayList<>();

    private static Theme loadSavedTheme() {
        try {
            String saved = WorkspaceStorage.getSavedTheme();
            return Theme.valueOf(saved.toUpperCase());
        } catch (Exception e) {
            return Theme.SOLARIZED;
        }
    }

    public static void registerThemeChangeListener(Consumer<Theme> listener) {
        if (listener != null && !themeListeners.contains(listener)) {
            themeListeners.add(listener);
        }
    }

    public static void setTheme(Theme theme) {
        if (theme == null) return;
        currentTheme = theme;
        WorkspaceStorage.saveTheme(theme.name());
        for (Consumer<Theme> listener : themeListeners) {
            try {
                listener.accept(theme);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String getThemeStyle() {
        switch (currentTheme) {
            case SOLARIZED:
                return "-fx-base: #eee8d5; " +
                       "-fx-background: #fdf6e3; " +
                       "-fx-control-inner-background: #fdf6e3; " +
                       "-fx-text-base-color: #073642; " +
                       "-fx-text-background-color: #073642; " +
                       "-fx-text-fill: #073642; " +
                       "-fx-text-inner-color: #073642; " +
                       "-fx-accent: #2aa198; " +
                       "-fx-focus-color: #2aa198; " +
                       "-fx-faint-focus-color: #2aa19833; " +
                       "-fx-selection-bar: #2aa198; " +
                       "-fx-selection-bar-text: #fdf6e3;";
            case LIGHT:
                return "-fx-base: #f1f5f9; " +
                       "-fx-background: #f8fafc; " +
                       "-fx-control-inner-background: #ffffff; " +
                       "-fx-text-base-color: #0f172a; " +
                       "-fx-text-background-color: #0f172a; " +
                       "-fx-text-fill: #0f172a; " +
                       "-fx-text-inner-color: #0f172a; " +
                       "-fx-accent: #0284c7; " +
                       "-fx-focus-color: #0284c7; " +
                       "-fx-faint-focus-color: #0284c733; " +
                       "-fx-selection-bar: #0284c7; " +
                       "-fx-selection-bar-text: #ffffff;";
            case DARK:
            default:
                return "-fx-base: #24292e; " +
                       "-fx-background: #181b1d; " +
                       "-fx-control-inner-background: #1e2227; " +
                       "-fx-text-base-color: #f0f6fc; " +
                       "-fx-text-background-color: #f0f6fc; " +
                       "-fx-text-fill: #f0f6fc; " +
                       "-fx-text-inner-color: #f0f6fc; " +
                       "-fx-accent: #38bdf8; " +
                       "-fx-focus-color: #38bdf8; " +
                       "-fx-faint-focus-color: #38bdf833; " +
                       "-fx-selection-bar: #38bdf8; " +
                       "-fx-selection-bar-text: #000000;";
        }
    }

    public static void applyTheme(Region pane) {
        if (pane == null) return;
        pane.setStyle(getThemeStyle());
    }

    public static void applyTheme(Parent root) {
        if (root == null) return;
        root.setStyle(getThemeStyle());
    }

    public static void applyTheme(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        applyTheme(scene.getRoot());
    }

    public static void applyTheme(Stage stage) {
        if (stage == null || stage.getScene() == null) return;
        applyTheme(stage.getScene());
    }

    /**
     * Applies the current theme to a JavaFX Dialog or Alert so that popups
     * match the parent window background, text colors, and button styling.
     */
    public static void applyTheme(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        if (pane != null) {
            pane.setStyle(getThemeStyle());
        }
    }
}