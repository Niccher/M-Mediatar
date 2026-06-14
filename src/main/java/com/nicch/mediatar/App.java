package com.nicch.mediatar;


import javafx.application.Application;

/**
 * Standard Vanilla Java entry point.
 * This class bypasses JavaFX classpath checks and safely boots the application.
 */
public class App {
    
    public static void main(String[] args) {
        System.out.println("Booting Media Workspace Launcher...");
        
        // Direct vanilla call to launch your Loader class
        Application.launch(MMediatar.class, args);
    }
}

