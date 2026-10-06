package com.nicch.mediatar;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import javafx.scene.image.Image;

/**
 * Handles workspace initialization and boots the main FXML application window.
 */
public class MMediatar extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) {
        stage.setTitle("MMediatar");
        
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/com/nicch/mediatar/logo.png");
            if (is != null) {
                Image icon = new Image(is);
                stage.getIcons().add(icon);
            } else {
                System.err.println("Icon not found at /com/nicch/mediatar/logo.png");
            }
        } catch (Exception e) {
            System.err.println("Logo icon load failed: " + e.getMessage());
        }

        try {
            Parent root = loadFXML("mainworkhorse");
            javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            double initialWidth = Math.min(1440, Math.max(960, bounds.getWidth() - 40));
            double initialHeight = Math.min(900, Math.max(600, bounds.getHeight() - 60));
            
            scene = new Scene(root, initialWidth, initialHeight);
            stage.setScene(scene);
            stage.setMinWidth(960);
            stage.setMinHeight(600);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Workspace layout launch failed: " + e.getMessage());
            Platform.exit();
        }
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        String resourcePath = "/com/nicch/mediatar/" + fxml + ".fxml";
        FXMLLoader fxmlLoader = new FXMLLoader(MMediatar.class.getResource(resourcePath));
        Parent root = fxmlLoader.load();
        if (root == null) {
            throw new IOException("Critical Error: Resource stream resolved null root tree: " + resourcePath);
        }
        return root;
    }
}