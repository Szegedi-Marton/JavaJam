package com.javaJam.view;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.javaJam.controller.MusicController;
import com.javaJam.model.Music;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MusicView extends JFrame {
    private JPanel browserPanel;
    private MusicController musicController;
    private DefaultListModel<Music> listModel;
    private JList<Music> musicListUI;

    private JButton playPauseButton;
    private JButton repeatButton;
    private JSlider seekSlider;
    private JLabel currentTimeLabel;
    private JLabel durationLabel;
    private JLabel nowPlayingLabel;
    private JButton settingsButton;
    private JTextField searchField;
    private JButton profileButton;
    private JButton prevButton;
    private JButton stopButton;
    private JButton nextButton;
    private JButton shuffleButton;
    private JButton jamButton;
    private JSlider volumeSlider;

    public final Color bgDark = new Color(0, 0, 0);
    public final Color panelDark = new Color(0, 0, 0);
    public final Color textLight = new Color(220, 220, 220);
    public final Color buttonDark = new Color(30, 30, 30);
    public final Color accentColor = new Color(75, 110, 175);


    public MusicView() {
        setTitle("JavaJam");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(bgDark);

        //TOP NAV
        JPanel topNavPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        topNavPanel.setBackground(panelDark);

        settingsButton = createStyledButton("icons/settings.svg", buttonDark, textLight);
        searchField = new JTextField(20);
        searchField.setBackground(bgDark);
        searchField.setForeground(textLight);
        searchField.setCaretColor(textLight);

        searchField.setFocusable(true);
        searchField.setRequestFocusEnabled(true);

        searchField.addMouseListener(new java.awt.event.MouseAdapter() {

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {

                SwingUtilities.invokeLater(() -> {

                    if (musicController != null) {
                        musicController.releaseBrowserFocus();
                    }

                    searchField.requestFocusInWindow();
                    searchField.grabFocus();

                    System.out.println(
                            "[SWING] Search focus: " +
                                    searchField.isFocusOwner()
                    );

                });
            }
        });

        profileButton = createStyledButton("icons/profile.svg", buttonDark, textLight);

        topNavPanel.add(settingsButton);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(textLight);
        topNavPanel.add(searchLabel);
        topNavPanel.add(searchField);
        setupFocusHandling();

        topNavPanel.add(profileButton);
        add(topNavPanel, BorderLayout.NORTH);

        //List view n Web player
        listModel = new DefaultListModel<>();
        musicListUI = new JList<>(listModel);
        musicListUI.setBackground(bgDark);
        musicListUI.setForeground(textLight);
        musicListUI.setSelectionBackground(accentColor);
        musicListUI.setSelectionForeground(Color.WHITE);

        musicListUI.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus) {

                super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus
                );

                if (value instanceof Music) {
                    setText(((Music) value).getName());
                }

                return this;
            }
        });

        browserPanel = new JPanel(new BorderLayout());
        browserPanel.setBackground(Color.BLACK);

        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.add(browserPanel, BorderLayout.CENTER);
        rightContainer.setBackground(bgDark);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(musicListUI),
                rightContainer
        );
        splitPane.setDividerLocation(300);
        splitPane.setBackground(bgDark);
        add(splitPane, BorderLayout.CENTER);


        //BOTTOM MUSIC PLAYER
        JPanel bottomBar = new JPanel(new BorderLayout(10, 5));
        bottomBar.setBackground(panelDark);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        nowPlayingLabel = new JLabel("Nothing playing");
        nowPlayingLabel.setForeground(textLight);
        nowPlayingLabel.setHorizontalAlignment(SwingConstants.CENTER);

        bottomBar.add(nowPlayingLabel, BorderLayout.NORTH);

        JPanel controlsPanel = new JPanel(new BorderLayout(10, 5));
        controlsPanel.setBackground(panelDark);

        JPanel seekPanel = new JPanel(new BorderLayout(8, 0));
        seekPanel.setBackground(panelDark);

        currentTimeLabel = new JLabel("00:00");
        currentTimeLabel.setForeground(textLight);

        durationLabel = new JLabel("00:00");
        durationLabel.setForeground(textLight);

        seekSlider = new JSlider(0, 1000, 0);
        seekSlider.setBackground(panelDark);
        seekSlider.setForeground(accentColor);
        seekSlider.setPreferredSize(new Dimension(400, 25));

        seekPanel.add(currentTimeLabel, BorderLayout.WEST);
        seekPanel.add(seekSlider, BorderLayout.CENTER);
        seekPanel.add(durationLabel, BorderLayout.EAST);

        controlsPanel.add(seekPanel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 12, 5)
        );
        buttonsPanel.setBackground(panelDark);

        shuffleButton = createStyledButton("icons/shuffle.svg", buttonDark, textLight);
        prevButton = createStyledButton("icons/prev.svg", buttonDark, textLight);
        playPauseButton = createStyledButton("icons/play.svg", accentColor, Color.WHITE);
        stopButton = createStyledButton("icons/stop.svg", buttonDark, textLight);
        nextButton = createStyledButton("icons/next.svg", buttonDark, textLight);
        repeatButton = createStyledButton("icons/repeat.svg", buttonDark, textLight);
        jamButton = createStyledButton("icons/jam.svg", buttonDark, textLight);

        buttonsPanel.add(shuffleButton);
        buttonsPanel.add(prevButton);
        buttonsPanel.add(playPauseButton);
      //  buttonsPanel.add(stopButton);
        buttonsPanel.add(nextButton);
        buttonsPanel.add(repeatButton);
        buttonsPanel.add(jamButton);

        JPanel volumePanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 16, 8)
        );
        volumePanel.setBackground(panelDark);

        JLabel volumeLabel = new JLabel(new FlatSVGIcon("icons/volume.svg", 18, 18));
        volumeLabel.setForeground(textLight);

        volumeSlider = new JSlider(0, 100, 50);
        volumeSlider.setBackground(panelDark);
        volumeSlider.setPreferredSize(new Dimension(120, 25));

        volumePanel.add(volumeLabel);
        volumePanel.add(volumeSlider);


        controlsPanel.add(buttonsPanel, BorderLayout.CENTER);
        controlsPanel.add(volumePanel, BorderLayout.EAST);
        bottomBar.add(controlsPanel, BorderLayout.CENTER);
        add(bottomBar, BorderLayout.SOUTH);


        //DATA LISTENER
        listModel.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                //System.out.println("Music item(s) added to the list!");
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                //System.out.println("Music item(s) removed from the list!");
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                //System.out.println("Music list contents changed/updated!");
            }
        });

        setupSearchFocus();
    }


    private void setupSearchFocus() {

        searchField.setFocusable(true);
        searchField.setEditable(true);
        searchField.setEnabled(true);

        searchField.addMouseListener(new java.awt.event.MouseAdapter() {

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {

                SwingUtilities.invokeLater(() -> {

                    // Request focus for the JFrame.
                    MusicView.this.requestFocus();

                    // Then transfer focus to the search field.
                    searchField.requestFocus();
                    searchField.grabFocus();

                    System.out.println(
                            "[SWING] Search focus requested: " +
                                    searchField.isFocusOwner()
                    );
                });
            }
        });
    }


    // --- Helper Method ---
    private JButton createStyledButton(String iconPath, Color bg, Color fg) {
        // Load the SVG icon and set a standard size (e.g., 18x18 pixels)
        FlatSVGIcon icon = new FlatSVGIcon(iconPath, 18, 18);

        // Optional: If your SVGs support color filtering/tinting to match the foreground color:
        // icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> fg));

        JButton button = new JButton(icon);

        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("JComponent.arc", 16);

        if (bg != null) {
            button.setBackground(bg);
        }

        if (fg != null) {
            button.setForeground(fg);
        }

        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setContentAreaFilled(true);

        // Padding adjusted slightly for square/icon-centric buttons
        button.setMargin(new Insets(8, 8, 8, 8));

        return button;
    }


    private void setupFocusHandling() {

        setFocusable(true);

        // Explicitly request focus
        searchField.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                SwingUtilities.invokeLater(() -> {
                    searchField.requestFocus();
                    searchField.grabFocus();
                });
            }
        });

        // Global Swing keyboard shortcuts.
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(event -> {

                    if (!isActive()) {
                        return false;
                    }

                    if (event.getID() != KeyEvent.KEY_PRESSED) {
                        return false;
                    }

                    return false;
                });
    }


    // GETTERS
    public JButton getSettingsButton() {
        return settingsButton;
    }

    public JTextField getSearchField() {
        return searchField;
    }

    public JButton getProfileButton() {
        return profileButton;
    }

    public JButton getPrevButton() {
        return prevButton;
    }

    public JButton getStopButton() {
        return stopButton;
    }

    public JButton getNextButton() {
        return nextButton;
    }

    public JButton getShuffleButton() {
        return shuffleButton;
    }

    public JButton getJamButton() {
        return jamButton;
    }

    public JSlider getVolumeSlider() {
        return volumeSlider;
    }

    public JList<Music> getMusicListUI() {
        return musicListUI;
    }

    public DefaultListModel<Music> getListModel() {
        return listModel;
    }

    public JButton getPlayPauseButton() {
        return playPauseButton;
    }

    public JButton getRepeatButton() {
        return repeatButton;
    }

    public JSlider getSeekSlider() {
        return seekSlider;
    }

    public void setNowPlaying(String name) {
        SwingUtilities.invokeLater(() ->
                nowPlayingLabel.setText(name)
        );
    }

    public void focusSearchField() {
        SwingUtilities.invokeLater(() -> {
            searchField.requestFocusInWindow();
        });
    }

    public void setMusicController(MusicController controller) {
        this.musicController = controller;
    }

    public JLabel getCurrentTimeLabel() {
        return currentTimeLabel;
    }

    public JPanel getBrowserPanel() {
        return browserPanel;
    }

    public JLabel getDurationLabel() {
        return durationLabel;
    }
}