package view;

import model.Music;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import java.awt.*;
import javafx.embed.swing.JFXPanel;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MusicView extends JFrame {
    private DefaultListModel<Music> listModel;
    private JList<Music> musicListUI;
    private JTextArea detailsArea;
    private JFXPanel fxPanel; // Holds the in-app browser player
    private WebView webView;
    private JButton playPauseButton;
    private JButton repeatButton;

    private JSlider seekSlider;
    private JLabel currentTimeLabel;
    private JLabel durationLabel;
    private JLabel nowPlayingLabel;

    private boolean isSeeking = false;
    private boolean isPlaying = false;
    private boolean repeatEnabled = false;
    private volatile double videoDurationSeconds = 0;



    // Top Nav Components
    private JButton settingsButton;
    private JTextField searchField;
    private JButton profileButton;

    // Bottom Playback Components
    private JButton prevButton;
    private JButton stopButton;
    private JButton nextButton;
    private JButton shuffleButton;
    private JButton jamButton;
    private JSlider volumeSlider;
    private Timer progressTimer;

    private HttpServer playerServer;
    private int playerServerPort;

    public MusicView() {
        setTitle("Music Dashboard - Dark Mode");
        setSize(900, 600); // Made slightly larger to fit the web player comfortably
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ==========================================
        // DARK THEME COLORS
        // ==========================================
        Color bgDark = new Color(0, 0, 0);       // Dark charcoal background
        Color panelDark = new Color(0, 0, 0);    // Slightly lighter dark for panels
        Color textLight = new Color(220, 220, 220); // Off-white text
        Color buttonDark = new Color(85, 85, 85);   // Button background
        Color accentColor = new Color(75, 110, 175);

        // Apply dark background to the main content pane
        getContentPane().setBackground(bgDark);

        // ==========================================
        // 1. TOP NAV BAR (North)
        // ==========================================
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
        topNavPanel.add(profileButton);

        add(topNavPanel, BorderLayout.NORTH);

        // ==========================================
        // 2. MAIN LIST VIEW & WEB PLAYER (Center)
        // ==========================================
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

        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        detailsArea.setBackground(bgDark);
        detailsArea.setForeground(bgDark);
        detailsArea.setCaretColor(textLight);

        // Initialize JavaFX Panel for the YouTube player
        fxPanel = new JFXPanel();
        startPlayerServer();
        initFXPlayer();

        // Create a sub-panel for the right side holding both details text and the media player
        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.add(new JScrollPane(detailsArea), BorderLayout.NORTH);
        rightContainer.add(fxPanel, BorderLayout.CENTER); // Embed the YouTube player here!
        rightContainer.setBackground(bgDark);

        // Split pane to keep list on left and rightContainer on the right
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(musicListUI),
                rightContainer
        );
        splitPane.setDividerLocation(300);
        splitPane.setBackground(bgDark);
        add(splitPane, BorderLayout.CENTER);


// ==========================================
// 3. BOTTOM MUSIC PLAYER (South)
// ==========================================

        JPanel bottomBar = new JPanel(new BorderLayout(10, 5));
        bottomBar.setBackground(panelDark);
        bottomBar.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

// NOW PLAYING TITLE
        nowPlayingLabel = new JLabel("Nothing playing");
        nowPlayingLabel.setForeground(textLight);
        nowPlayingLabel.setHorizontalAlignment(SwingConstants.CENTER);

        bottomBar.add(nowPlayingLabel, BorderLayout.NORTH);

// CENTER PLAYER CONTROLS
        JPanel controlsPanel = new JPanel(new BorderLayout(10, 5));
        controlsPanel.setBackground(panelDark);

// SEEK BAR
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

// BUTTONS
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
        jamButton = createStyledButton("Jam",buttonDark, textLight);

        buttonsPanel.add(shuffleButton);
        buttonsPanel.add(prevButton);
        buttonsPanel.add(playPauseButton);
        buttonsPanel.add(stopButton);
        buttonsPanel.add(nextButton);
        buttonsPanel.add(repeatButton);
        buttonsPanel.add(jamButton);

        controlsPanel.add(buttonsPanel, BorderLayout.CENTER);

        bottomBar.add(controlsPanel, BorderLayout.CENTER);

// VOLUME
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




        seekSlider.addChangeListener(e -> {
            if (seekSlider.getValueIsAdjusting()) {
                isSeeking = true;
                double duration = getCachedDuration();
                if (duration > 0) {
                    double target = duration * seekSlider.getValue() / 1000.0;
                    currentTimeLabel.setText(formatTime(target));
                }
            } else if (isSeeking) {
                isSeeking = false;
                double duration = getCachedDuration();
                if (duration > 0) {
                    double target = duration * seekSlider.getValue() / 1000.0;
                    seekVideo(target);
                }
            }
        });

        getStopButton().addActionListener(e -> stopVideo());

        getVolumeSlider().addChangeListener(e ->
                setPlayerVolume(getVolumeSlider().getValue())
        );

        // ==========================================
        // 4. LIST DATA LISTENER
        // ==========================================
        listModel.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                System.out.println("Music item(s) added to the list!");
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                System.out.println("Music item(s) removed from the list!");
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                System.out.println("Music list contents changed/updated!");
            }
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (playerServer != null) {
                    playerServer.stop(0);
                }
            }
        });
    }

    // Helper method to create consistently styled dark buttons
    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton button = new JButton(text);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setFocusPainted(false);
        return button;
    }

    public void startProgressTimer() {

        if (progressTimer != null) {
            progressTimer.stop();
        }

        progressTimer = new Timer(500, e -> {

            getVideoInformation((current, duration) -> {

                currentTimeLabel.setText(formatTime(current));
                durationLabel.setText(formatTime(duration));

                if (duration > 0 &&
                        !seekSlider.getValueIsAdjusting()) {

                    int sliderValue = (int)
                            (current / duration * 1000);

                    seekSlider.setValue(sliderValue);
                }
            });
        });

        progressTimer.start();
    }

    private String formatTime(double seconds) {

        int totalSeconds = (int) Math.max(0, seconds);

        int minutes = totalSeconds / 60;
        int remainingSeconds = totalSeconds % 60;

        return String.format(
                "%02d:%02d",
                minutes,
                remainingSeconds
        );
    }


    private void initFXPlayer() {
        Platform.runLater(() -> {
            webView = new WebView();

            webView.getEngine().setJavaScriptEnabled(true);

            webView.setContextMenuEnabled(false);

            webView.getEngine().getLoadWorker().stateProperty()
                    .addListener((obs, oldState, newState) -> {
                        System.out.println("WebView state: " + newState);
                    });

            webView.getEngine().setOnError(event -> {
                System.err.println("WebView error: " + event.getMessage());
            });

            fxPanel.setScene(new Scene(webView));

            webView.getEngine().load("about:blank");
        });
    }


    private void startPlayerServer() {
        try {
            playerServer = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0
            );

            playerServerPort = playerServer.getAddress().getPort();

            playerServer.createContext("/player/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String videoId = path.substring("/player/".length());

                if (!videoId.matches("[a-zA-Z0-9_-]{11}")) {
                    exchange.sendResponseHeaders(400, -1);
                    exchange.close();
                    return;
                }

                String origin = "http://127.0.0.1:" + playerServerPort;

                String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="referrer"
                          content="strict-origin-when-cross-origin">

                    <style>
                        html, body {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: 100%;
                            background: #000;
                            overflow: hidden;
                        }

                        #player {
                            width: 100%;
                            height: 100%;
                        }
                    </style>
                </head>

                <body>

                <div id="player"></div>

                <script>
                        
                         window.player = null;
                         window.isReady = false;
                         window.repeatEnabled = false;
                         window.pendingVideoId = null;
                        
                         var tag = document.createElement('script');
                         tag.src = "https://www.youtube.com/iframe_api";
                         document.head.appendChild(tag);
                        
                         window.onYouTubeIframeAPIReady = function() {
                        
                             console.log("YouTube API loaded");
                        
                             window.player = new YT.Player('player', {
                        
                                 width: '100%',
                                 height: '100%',
                        
                                 videoId: 'VIDEO_ID',
                        
                                 playerVars: {
                                     autoplay: 1,
                                     controls: 0,
                                     disablekb: 1,
                                     playsinline: 1,
                                     enablejsapi: 1,
                                     origin: 'ORIGIN'
                                 },
                        
                                 events: {
                        
                                     onReady: function(event) {
                        
                                         window.isReady = true;
                        
                                         console.log("YouTube player ready");
                        
                                         if (window.pendingVideoId) {
                                             window.loadNewVideo(window.pendingVideoId);
                                         }
                                     },
                        
                                     onStateChange: function(event) {
                        
                                         console.log("Player state:", event.data);
                        
                                         if (
                                             event.data === YT.PlayerState.ENDED &&
                                             window.repeatEnabled
                                         ) {
                                             window.player.playVideo();
                                         }
                                     },
                        
                                     onError: function(event) {
                                         console.error("YouTube error:", event.data);
                                     }
                                 }
                             });
                         };
                        
                         // ALWAYS register these functions immediately,
                         // not inside onReady or onYouTubeIframeAPIReady.
                        
                         window.isPlayerReady = function() {
                             return window.isReady && window.player !== null;
                         };
                        
                         window.loadNewVideo = function(videoId) {
                        
                             if (!window.isPlayerReady()) {
                                 window.pendingVideoId = videoId;
                                 return false;
                             }
                        
                             window.player.loadVideoById(videoId);
                        
                             return true;
                         };
                        
                         window.playVideo = function() {
                             if (window.isPlayerReady()) {
                                 window.player.playVideo();
                             }
                         };
                        
                         window.pauseVideo = function() {
                             if (window.isPlayerReady()) {
                                 window.player.pauseVideo();
                             }
                         };
                        
                         window.stopVideo = function() {
                             if (window.isPlayerReady()) {
                                 window.player.stopVideo();
                             }
                         };
                        
                         window.seekVideo = function(seconds) {
                             if (window.isPlayerReady()) {
                                 window.player.seekTo(seconds, true);
                                 window.player.playVideo();
                             }
                         };
                        
                         window.setVolume = function(value) {
                             if (window.isPlayerReady()) {
                                 window.player.setVolume(value);
                             }
                         };
                        
                         window.setRepeatEnabled = function(enabled) {
                             window.repeatEnabled = enabled;
                         };
                        
                         window.getCurrentTime = function() {
                             return window.isPlayerReady()
                                 ? window.player.getCurrentTime()
                                 : 0;
                         };
                        
                         window.getDuration = function() {
                             return window.isPlayerReady()
                                 ? window.player.getDuration()
                                 : 0;
                         };
                        
                        
                </script>

                </body>
                </html>
                """;

                html = html.replace("VIDEO_ID", videoId)
                        .replace("ORIGIN", origin);

                byte[] response = html.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set(
                        "Content-Type", "text/html; charset=UTF-8"
                );

                exchange.getResponseHeaders().set(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"
                );

                exchange.getResponseHeaders().set(
                        "Cache-Control", "no-store"
                );

                exchange.sendResponseHeaders(200, response.length);

                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });

            playerServer.start();

            System.out.println(
                    "YouTube player server started: " + playerServerPort
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void playVideo() {
        executePlayerCommand("playVideo()");
    }

    public void pauseVideo() {
        executePlayerCommand("pauseVideo()");
    }

    public void stopVideo() {
        executePlayerCommand("stopVideo()");
    }

    public void seekVideo(double seconds) {
        executePlayerCommand("seekVideo(" + seconds + ")");
    }

    public void setPlayerVolume(int volume) {
        int safeVolume = Math.max(0, Math.min(100, volume));
        executePlayerCommand("setVolume(" + safeVolume + ")");
    }

    private void executePlayerCommand(String javascript) {

        Platform.runLater(() -> {

            if (webView == null) {
                return;
            }

            if (webView.getEngine().getLoadWorker().getState()
                    != javafx.concurrent.Worker.State.SUCCEEDED) {
                return;
            }

            try {
                webView.getEngine().executeScript(javascript);

            } catch (Exception e) {
                System.err.println(
                        "Player command failed: " + e.getMessage()
                );
            }
        });
    }


    private void openYouTube(String videoId) {
        try {
            Desktop.getDesktop().browse(
                    new java.net.URI(
                            "https://www.youtube.com/watch?v=" + videoId
                    )
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadVideoInApp(String videoId) {
        if (videoId == null || !videoId.matches("[a-zA-Z0-9_-]{11}")) {
            return;
        }

        Platform.runLater(() -> {
            if (webView == null) return;

            var engine = webView.getEngine();

            // Always ensure the page is fully loaded before executing scripts
            if (engine.getLoadWorker().getState() != javafx.concurrent.Worker.State.SUCCEEDED) {
                loadPlayerPage(videoId);
                return;
            }

            try {
                Object ready = engine.executeScript(
                        "typeof window.isPlayerReady === 'function' && window.isPlayerReady()"
                );

                if (Boolean.TRUE.equals(ready)) {
                    engine.executeScript("window.loadNewVideo('" + videoId + "')");
                } else {
                    loadPlayerPage(videoId);
                }

            } catch (Exception e) {
                loadPlayerPage(videoId);
            }
        });
    }

    private void loadPlayerPage(String videoId) {

        String url = "http://127.0.0.1:"
                + playerServerPort
                + "/player/"
                + videoId;

        webView.getEngine().load(url);
    }


    public JFXPanel getFxPanel() {
        return fxPanel;
    }

    // ==========================================
    // GETTERS FOR CONTROLLER WIRING
    // ==========================================
    public JButton getSettingsButton() { return settingsButton; }
    public JTextField getSearchField() { return searchField; }
    public JButton getProfileButton() { return profileButton; }

    public JButton getPrevButton() { return prevButton; }
    public JButton getStopButton() { return stopButton; }
    public JButton getNextButton() { return nextButton; }
    public JButton getShuffleButton() { return shuffleButton; }
    public JButton getJamButton() { return jamButton; }
    public JSlider getVolumeSlider() { return volumeSlider; }

    public JList<Music> getMusicListUI() { return musicListUI; }
    public DefaultListModel<Music> getListModel() { return listModel; }
    public JTextArea getDetailsArea() { return detailsArea; }

    public JButton getPlayPauseButton() {
        return playPauseButton;
    }

    public JButton getRepeatButton() {
        return repeatButton;
    }



    public JSlider getSeekSlider() {
        return seekSlider;
    }



    public void getVideoInformation(
            java.util.function.BiConsumer<Double, Double> callback) {

        Platform.runLater(() -> {

            double current = 0;
            double duration = 0;

            try {
                if (webView == null) return;

                Object c = webView.getEngine()
                        .executeScript("getCurrentTime()");

                Object d = webView.getEngine()
                        .executeScript("getDuration()");

                if (c instanceof Number) {
                    current = ((Number) c).doubleValue();
                }

                if (d instanceof Number) {
                    duration = ((Number) d).doubleValue();
                }

                videoDurationSeconds = duration;

            } catch (Exception e) {
                return;
            }

            final double currentTime = current;
            final double totalDuration = duration;

            SwingUtilities.invokeLater(() ->
                    callback.accept(currentTime, totalDuration)
            );
        });
    }

    public double getCachedDuration() {
        return videoDurationSeconds;
    }


    public void setNowPlaying(String name) {
        SwingUtilities.invokeLater(() ->
                nowPlayingLabel.setText(name)
        );
    }

    public JLabel getCurrentTimeLabel() {
        return currentTimeLabel;
    }

    public JLabel getDurationLabel() {
        return durationLabel;
    }

    public void setRepeatEnabledInJs(boolean enabled) {
        executePlayerCommand("setRepeatEnabled(" + enabled + ")");
    }
}