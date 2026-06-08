module com.nicch.mediatar {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;     // ← Add this line
    requires java.xml;

    opens com.nicch.mediatar to javafx.fxml;
    exports com.nicch.mediatar;
}
