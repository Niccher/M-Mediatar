package com.nicch.mediatar;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.IOException;
import javafx.scene.image.Image;

/**
 * Handles the initial presentation layer, system diagnostics, and transitions into FXML assets.
 */
public class Loader extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) {
        stage.setTitle("MMediatar - Workspace Initialization");
        
        Image icon = new Image(getClass().getResourceAsStream("logo.png"));
        stage.getIcons().add(icon);

        Parent loaderLayout = createLoaderLayout(stage);

        javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        double initialWidth = Math.min(1440, bounds.getWidth() - 40);
        double initialHeight = Math.min(900, bounds.getHeight() - 40);
        
        scene = new Scene(loaderLayout, initialWidth, initialHeight);
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    private Parent createLoaderLayout(Stage currentStage) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(40));
        // Matches the base Solarized Light canvas tone perfectly
        mainLayout.setStyle("-fx-background-color: #fdf6e3;");

        // --- TOP SECTION ---
        VBox topPanel = new VBox(6);
        topPanel.setAlignment(Pos.CENTER);

        Label lblAppName = new Label("MMediatar");
        lblAppName.setFont(Font.font("System", FontWeight.BOLD, 38));
        lblAppName.setStyle("-fx-text-fill: #586e75;"); // Solarized dark gray text

        Label lblWelcome = new Label("Welcome to Your Media Workspace");
        lblWelcome.setFont(Font.font("System", FontWeight.BOLD, 20));
        lblWelcome.setStyle("-fx-text-fill: #657b83;"); 

        Label lblSubtitle = new Label("Initialize your environment to begin high-performance editing");
        lblSubtitle.setFont(Font.font("System", FontPosture.ITALIC, 13));
        lblSubtitle.setStyle("-fx-text-fill: #93a1a1;");

        topPanel.getChildren().addAll(lblAppName, lblWelcome, lblSubtitle);
        mainLayout.setTop(topPanel);

        // --- CENTER SECTION ---
        VBox centerPanel = new VBox(20);
        centerPanel.setPadding(new Insets(25, 40, 25, 40));
        centerPanel.setAlignment(Pos.CENTER);

        // 1. Features
        VBox featuresBox = new VBox(10);
        featuresBox.setMaxWidth(800);
        featuresBox.setStyle("-fx-border-color: #93a1a1; -fx-border-width: 1; -fx-border-radius: 6; -fx-padding: 16; -fx-background-color: #eee8d5;");

        Label lblSectionTitle = new Label("Available Tools & Capabilities");
        lblSectionTitle.setFont(Font.font("System", FontWeight.BOLD, 15));
        lblSectionTitle.setStyle("-fx-text-fill: #859900;"); // Solarized Green

        String[] features = {
            "• Media Editor Core: Supports raw non-linear timeline sequencing and multi-track audio/video layering.",
            "• Advanced Media Trimmer: Precision lossless clipping and high-speed intra-frame splitting.",
            "• Unified Audio/Video Merger: Handles multi-format batch stitching and container wrapping.",
            "• Format Transcoding Core: Inter-convert digital formats across major video/audio wrappers.",
            "• Stream Manipulation Matrix: Controls resolution scaling and hardware filters."
        };

        featuresBox.getChildren().add(lblSectionTitle);
        for (String feat : features) {
            Label lbl = new Label(feat);
            lbl.setFont(Font.font("System", 13));
            lbl.setWrapText(true);
            lbl.setStyle("-fx-text-fill: #586e75;");
            featuresBox.getChildren().add(lbl);
        }

        // 2. Dependencies
        VBox dependenciesBox = new VBox(8);
        dependenciesBox.setMaxWidth(800);
        dependenciesBox.setStyle("-fx-border-color: #dc322f; -fx-border-width: 1; -fx-border-radius: 6; -fx-padding: 16; -fx-background-color: #fdf2f2;");

        Label lblDepTitle = new Label("⚙ Required Native Core Dependencies");
        lblDepTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        lblDepTitle.setStyle("-fx-text-fill: #dc322f;"); // Solarized Crimson Red

        boolean hasFFmpeg = isCommandAvailable("ffmpeg");
        boolean hasFFprobe = isCommandAvailable("ffprobe");
        boolean hasFileManager = checkFileManager();

        String[] deps = {
            (hasFFmpeg ? "✔" : "❌") + " ffmpeg — System-wide multimedia translation, codec routing, and stream mapping.",
            (hasFFprobe ? "✔" : "❌") + " ffprobe — Structural stream analyzer, track configuration reader, and metadata validator.",
            (hasFileManager ? "✔" : "❌") + " file manager — System file explorer integration (nautilus, dolphin, xdg-open, etc)."
        };

        dependenciesBox.getChildren().add(lblDepTitle);
        for (String dep : deps) {
            Label lbl = new Label(dep);
            lbl.setFont(Font.font("System", 13));
            lbl.setWrapText(true);
            lbl.setStyle("-fx-text-fill: #586e75;");
            dependenciesBox.getChildren().add(lbl);
        }

        // 3. Metadata Info
        VBox metadataBox = new VBox(6);
        metadataBox.setMaxWidth(800);
        metadataBox.setStyle("-fx-border-color: #93a1a1; -fx-border-width: 1; -fx-border-radius: 6; -fx-padding: 14; -fx-background-color: #eee8d5;");

        Label lblMetaTitle = new Label("📋 System Release Information");
        lblMetaTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
        lblMetaTitle.setStyle("-fx-text-fill: #268bd2;"); // Solarized Blue

        Label lblMetaDetails = new Label("App Version:   2.4.0-STABLE\nBuild Number:  #2026.05.21-REV4\nRelease Date:  May 21, 2026");
        lblMetaDetails.setFont(Font.font("Consolas", 13));
        lblMetaDetails.setStyle("-fx-text-fill: #657b83;");

        metadataBox.getChildren().addAll(lblMetaTitle, lblMetaDetails);
        centerPanel.getChildren().addAll(featuresBox, dependenciesBox, metadataBox);
        mainLayout.setCenter(centerPanel);

        // --- BOTTOM SECTION ---
        HBox bottomPanel = new HBox(15);
        bottomPanel.setAlignment(Pos.BOTTOM_RIGHT);
        bottomPanel.setMaxWidth(800);

        Button btnCancel = new Button("Cancel");
        btnCancel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));
        btnCancel.setStyle("-fx-background-color: #93a1a1; -fx-text-fill: #fdf6e3; -fx-cursor: hand; -fx-padding: 10 24 10 24; -fx-background-radius: 4;");
        btnCancel.setOnAction(e -> { Platform.exit(); System.exit(0); });

        Button btnProceed = new Button("Proceed  →");
        btnProceed.setFont(Font.font("System", FontWeight.BOLD, 13));
        btnProceed.setStyle("-fx-background-color: #859900; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 10 24 10 24; -fx-background-radius: 4;");
        btnProceed.setOnAction(e -> {
            try {
                setRoot("mainworkhorse");
            } catch (IOException ex) {
                System.err.println("Failed to swap views: " + ex.getMessage());
            }
        });

        bottomPanel.getChildren().addAll(btnCancel, btnProceed);
        HBox bottomWrapper = new HBox(bottomPanel);
        bottomWrapper.setAlignment(Pos.CENTER);
        mainLayout.setBottom(bottomWrapper);

        return mainLayout;
    }

    /**
     * Replaces root pane utilizing safe absolute path routing resources strings
     */
    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        String resourcePath = "/com/nicch/mediatar/" + fxml + ".fxml";
        FXMLLoader fxmlLoader = new FXMLLoader(Loader.class.getResource(resourcePath));
        Parent root = fxmlLoader.load();
        if (root == null) {
            throw new IOException("Critical Error: Core resource path resolved null root tree stream: " + resourcePath);
        }
        return root;
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"which", cmd});
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkFileManager() {
        String[] managers = {"nautilus", "nemo", "dolphin", "krusader", "thunar", "pcmanfm", "xdg-open"};
        for (String fm : managers) {
            if (isCommandAvailable(fm)) return true;
        }
        return false;
    }
}