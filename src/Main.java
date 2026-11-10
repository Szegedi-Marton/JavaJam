import controller.MainController;
import controller.MusicController;
import view.MusicView;

import javax.swing.*;

void main() {


    SwingUtilities.invokeLater(() -> {


        MusicView view = new MusicView();
        MusicController controller = new MusicController(view);
        view.setVisible(true);


    });


}
