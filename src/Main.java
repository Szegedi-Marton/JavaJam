import controller.MainController;
import controller.MusicController;
import view.MusicView;

import javax.swing.*;

void main() {


    SwingUtilities.invokeLater(() -> {
        MainController controller = new MainController();
        controller.start();
    });


}
