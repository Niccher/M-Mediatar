import javafx.application.Platform;
import javafx.scene.media.Media;

public class TestMedia {
    public static void main(String[] args) {
        Platform.startup(() -> {
            try {
                Media m = new Media("file:///home/domino/NetBeansProjects/Mediatar/test.aac");
                System.out.println("Success");
            } catch (Exception e) {
                e.printStackTrace();
            }
            Platform.exit();
        });
    }
}
