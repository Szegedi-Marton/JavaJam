package com.javaJam.view;

import com.javaJam.model.Music;
import javax.swing.*;
import javax.swing.event.ListDataEvent;
import java.awt.*;


public class MusicView extends JFrame {
    private JPanel browserPanel;

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
    private Timer progressTimer;


    public MusicView() {
        setTitle("JavaJam");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());


        //Colors
        Color bgDark = new Color(0, 0, 0);       //background
        Color panelDark = new Color(0, 0, 0);
        Color textLight = new Color(220, 220, 220); //white text
        Color buttonDark = new Color(30, 30, 30);   //button background
        Color accentColor = new Color(75, 110, 175);

        getContentPane().setBackground(bgDark);


        //TOP NAV
        JPanel topNavPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        topNavPanel.setBackground(panelDark);

        settingsButton = createStyledButton("Settings", buttonDark, textLight);
        searchField = new JTextField(20);
        searchField.setBackground(bgDark);
        searchField.setForeground(textLight);
        searchField.setCaretColor(textLight);

        profileButton = createStyledButton("Profile", buttonDark, textLight);

        topNavPanel.add(settingsButton);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(textLight);
        topNavPanel.add(searchLabel);
        topNavPanel.add(searchField);
        //enableGlobalSearchTyping();


        searchField.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {

                    public void insertUpdate(
                            javax.swing.event.DocumentEvent e) {
                        System.out.println("Inserted: " + searchField.getText());
                    }

                    public void removeUpdate(
                            javax.swing.event.DocumentEvent e) {}

                    public void changedUpdate(
                            javax.swing.event.DocumentEvent e) {}
                }
        );



        topNavPanel.add(profileButton);

        add(topNavPanel, BorderLayout.NORTH);


        //List com.javaJam.view n Web player
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

        shuffleButton = createStyledButton("Shuffle", buttonDark, textLight);
        prevButton = createStyledButton("⏮", buttonDark, textLight);
        playPauseButton = createStyledButton("▶", accentColor, Color.WHITE);
        stopButton = createStyledButton("■", buttonDark, textLight);
        nextButton = createStyledButton("⏭", buttonDark, textLight);
        repeatButton = createStyledButton("Repeat", buttonDark, textLight);
        jamButton = createStyledButton("Jam", buttonDark, textLight);

        buttonsPanel.add(shuffleButton);
        buttonsPanel.add(prevButton);
        buttonsPanel.add(playPauseButton);
        buttonsPanel.add(stopButton);
        buttonsPanel.add(nextButton);
        buttonsPanel.add(repeatButton);
        buttonsPanel.add(jamButton);

        controlsPanel.add(buttonsPanel, BorderLayout.CENTER);

        bottomBar.add(controlsPanel, BorderLayout.CENTER);

        JPanel volumePanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 8, 0)
        );
        volumePanel.setBackground(panelDark);

        JLabel volumeLabel = new JLabel("Volume");
        volumeLabel.setForeground(textLight);

        volumeSlider = new JSlider(0, 100, 50);
        volumeSlider.setBackground(panelDark);
        volumeSlider.setPreferredSize(new Dimension(120, 25));

        volumePanel.add(volumeLabel);
        volumePanel.add(volumeSlider);

        bottomBar.add(volumePanel, BorderLayout.SOUTH);

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
    }


    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        return button;
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