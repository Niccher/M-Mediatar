/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.nicch.mediatar;

/**
 *
 * @author domino
 */

import javafx.scene.layout.Region;

/**
 * Centralized theme state to ensure consistency across the Loader and Workspace.
 */
public class ThemeManager {
    public enum Theme { SOLARIZED, DARK, LIGHT }
    
    // Default theme set to Solarized as requested
    public static Theme currentTheme = Theme.SOLARIZED;

    public static void applyTheme(Region pane) {
        if (pane == null) return;
        
        switch (currentTheme) {
            case SOLARIZED:
                pane.setStyle("-fx-base: #eee8d5; -fx-background: #fdf6e3; -fx-control-inner-background: #eee8d5;");
                break;
            case DARK:
                pane.setStyle("-fx-base: #2d2d2d; -fx-background: #1e1e1e; -fx-control-inner-background: #2d2d2d;");
                break;
            case LIGHT:
                pane.setStyle("-fx-base: #ffffff; -fx-background: #f4f4f4; -fx-control-inner-background: #ffffff;");
                break;
        }
    }
}