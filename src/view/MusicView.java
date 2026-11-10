package view;

import model.Music;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import java.awt.*;
import javafx.application.Platform;
import com.sun.net.httpserver.HttpServer;

import java.awt.event.KeyEvent;
import java.net.InetSocketAddress;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.handler.CefMessageRouterHandler;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.callback.CefQueryCallback;
import org.json.JSONObject;

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;

public class MusicView extends JFrame {

    private CefApp cefApp;
    private CefClient cefClient;
    private CefBrowser cefBrowser;
    private CefMessageRouter messageRouter;

    private volatile double currentVideoTime = 0;
    private volatile double videoDuration = 0;

    private JPanel browserPanel;

    private DefaultListModel<Music> listModel;
    private JList<Music> musicListUI;
    private JButton playPauseButton;
    private JButton repeatButton;

    private JSlider seekSlider;
    private JLabel currentTimeLabel;
    private JLabel durationLabel;
    private JLabel nowPlayingLabel;


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


    private CefMessageRouterHandler progressHandler;



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

        searchField.setFocusable(true);
        searchField.setEditable(true);

        enableGlobalSearchTyping();

        searchField.addFocusListener(new java.awt.event.FocusAdapter() {

            @Override
            public void focusGained(java.awt.event.FocusEvent e) {

                SwingUtilities.invokeLater(() -> {
                    searchField.requestFocusInWindow();
                    searchField.repaint();
                });
            }
        });

        searchField.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                SwingUtilities.invokeLater(() -> {
                    searchField.requestFocusInWindow();
                });
            }
        });

        searchField.addKeyListener(new java.awt.event.KeyAdapter() {

            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                System.out.println("[SEARCH] Key pressed: " + e.getKeyChar());
            }

            @Override
            public void keyTyped(java.awt.event.KeyEvent e) {
                System.out.println("[SEARCH] Key typed: " + e.getKeyChar());
            }
        });

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

        // Initialize JavaFX Panel for the YouTube player
        browserPanel = new JPanel(new BorderLayout());
        browserPanel.setBackground(Color.BLACK);

        startPlayerServer();
        initCefPlayer();

        // Create a sub-panel for the right side holding both details text and the media player
        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.add(browserPanel, BorderLayout.CENTER);
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


    private void initCefPlayer() {

        Thread cefThread = new Thread(() -> {

            try {
                CefAppBuilder builder = new CefAppBuilder();

                builder.setInstallDir(
                        new java.io.File("jcef-bundle")
                );

                builder.setProgressHandler(
                        new ConsoleProgressHandler()
                );

                builder.getCefSettings()
                        .windowless_rendering_enabled = false;

                cefApp = builder.build();

                cefClient = cefApp.createClient();

                // Create browser
                cefBrowser = cefClient.createBrowser(
                        "about:blank",
                        false,
                        false
                );

                // Create message router
                messageRouter = CefMessageRouter.create(
                        new CefMessageRouter.CefMessageRouterConfig(
                                "cefQuery",
                                "cefQueryCancel"
                        )
                );

                if (messageRouter == null) {
                    throw new IllegalStateException(
                            "Failed to create CefMessageRouter"
                    );
                }


                progressHandler = new CefMessageRouterHandler() {

                    @Override
                    public void setNativeRef(String identifer, long nativeRef) {

                    }

                    @Override
                    public long getNativeRef(String identifer) {
                        return 0;
                    }

                    @Override
                    public boolean onQuery(
                            CefBrowser browser,
                            CefFrame frame,
                            long queryId,
                            String request,
                            boolean persistent,
                            CefQueryCallback callback) {

                        System.out.println("[JCEF QUERY] " + request);

                        try {
                            org.json.JSONObject json =
                                    new org.json.JSONObject(request);

                            if ("progress".equals(json.optString("type"))) {

                                double current = json.getDouble("current");
                                double duration = json.getDouble("duration");

                                currentVideoTime = current;
                                videoDuration = duration;

                                System.out.println(
                                        "[JAVA PROGRESS] " +
                                                current + " / " + duration
                                );
                            }

                            callback.success("OK");
                            return true;

                        } catch (Exception e) {
                            e.printStackTrace();
                            callback.failure(1, e.getMessage());
                            return true;
                        }
                    }

                    @Override
                    public void onQueryCanceled(
                            CefBrowser browser,
                            CefFrame frame,
                            long queryId) {

                        System.out.println("[JCEF] Query canceled: " + queryId);
                    }
                };

                messageRouter.addHandler(progressHandler, true);

                cefClient.addMessageRouter(messageRouter);

                System.out.println("[JCEF] Message router initialized");

                SwingUtilities.invokeLater(() -> {

                    browserPanel.add(
                            cefBrowser.getUIComponent(),
                            BorderLayout.CENTER
                    );

                    browserPanel.revalidate();
                    browserPanel.repaint();

                });

            } catch (Throwable e) {
                e.printStackTrace();
            }

        }, "JCEF-Initialization");

        cefThread.start();
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
                <meta name="referrer" content="strict-origin-when-cross-origin">
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
                                     position: absolute;
                                     inset: 0;
                                     width: 100%;
                                     height: 100%;
                                 }
                        
                                 #player iframe {
                                     width: 100% !important;
                                     height: 100% !important;
                                     border: 0;
                                 }
                </style>
            </head>
            <body>

            <div id="player"></div>

            <script>
                        
                        
                                        window.player = null;
                                        window.isReady = false;
                                        window.repeatEnabled = false;
                                        window.pendingVideoId = 'VIDEO_ID';
                                        window.pendingCommands = [];
                        
                                        function debug(message) {
                                            console.log("[JS] " + message);
                                        }
                        
                                        // =====================================
                                        // PLAYER READY CHECK
                                        // =====================================
                        
                                        window.isPlayerReady = function() {
                                            return window.isReady &&
                                                   window.player !== null;
                                        };
                        
                                        // =====================================
                                        // LOAD YOUTUBE API
                                        // =====================================
                        
                                        var tag = document.createElement('script');
                                        tag.src = "https://www.youtube.com/iframe_api";
                        
                                        document.head.appendChild(tag);
                        
                                        window.onYouTubeIframeAPIReady = function() {
                        
                                            debug("YouTube API loaded");
                        
                                            window.player = new YT.Player('player', {
                        
                                                width: '100%',
                                                height: '100%',
                        
                                                videoId: window.pendingVideoId,
                        
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
                        
                                                        debug("Player READY");
                        
                                                        while (window.pendingCommands.length > 0) {
                                                            window.pendingCommands.shift()();
                                                        }
                                                    },
                        
                                                    onStateChange: function(event) {
                        
                                                        var states = {
                                                            "-1": "UNSTARTED",
                                                            "0": "ENDED",
                                                            "1": "PLAYING",
                                                            "2": "PAUSED",
                                                            "3": "BUFFERING",
                                                            "5": "CUED"
                                                        };
                        
                                                        debug("Player state: " + states[event.data]);
                        
                                                        if (event.data === YT.PlayerState.BUFFERING) {
                                                            debug("Started buffering");
                                                        }
                        
                                                        if (event.data === YT.PlayerState.PLAYING) {
                                                            debug("Playback resumed at: " +
                                                                window.player.getCurrentTime());
                                                        }
                        
                                                        if (event.data === YT.PlayerState.PAUSED) {
                                                            debug("Player paused");
                                                        }
                        
                                                        if (event.data === YT.PlayerState.ENDED &&
                                                            window.repeatEnabled) {
                        
                                                            debug("Repeating video");
                                                            window.player.playVideo();
                                                        }
                                                    },
                        
                                                    onError: function(event) {
                                                        console.error("[ERROR] YouTube error:", event.data);
                                                    }
                                                }
                                            });
                                        };
                        
                                        // =====================================
                                        // COMMAND QUEUE
                                        // =====================================
                        
                                        function executeWhenReady(callback) {
                        
                                            if (window.isPlayerReady()) {
                                                callback();
                                            } else {
                                                window.pendingCommands.push(callback);
                                            }
                                        }
                        
                                        // =====================================
                                        // VIDEO CONTROLS
                                        // =====================================
                        
                                        window.loadNewVideo = function(videoId) {
                        
                                            if (!window.isPlayerReady()) {
                                                window.pendingVideoId = videoId;
                                                return;
                                            }
                        
                                            window.pendingVideoId = videoId;
                        
                                            debug("Loading video: " + videoId);
                        
                                            window.player.loadVideoById({
                                                videoId: videoId,
                                                startSeconds: 0
                                            });
                                        };
                        
                                        window.playVideo = function() {
                                            executeWhenReady(function() {
                                                debug("Play requested");
                                                window.player.playVideo();
                                            });
                                        };
                        
                                        window.pauseVideo = function() {
                                            executeWhenReady(function() {
                                                debug("Pause requested");
                                                window.player.pauseVideo();
                                            });
                                        };
                        
                                        window.stopVideo = function() {
                                            executeWhenReady(function() {
                                                debug("Stop requested");
                                                window.player.stopVideo();
                                            });
                                        };
                        
                                        // =====================================
                                        // PLAYBACK INFORMATION TO JAVA
                                        // =====================================
                        
                                        window.lastProgress = {
                                                                current: 0,
                                                                duration: 0
                                                            };
                        
                                                            setInterval(function() {
                        
                                                                if (!window.isPlayerReady()) return;
                        
                                                                const current = window.player.getCurrentTime();
                                                                const duration = window.player.getDuration();
                        
                                                                if (duration <= 0) return;
                        
                                                                window.lastProgress.current = current;
                                                                window.lastProgress.duration = duration;
                        
                                                                if (typeof window.cefQuery !== "function") {
                                                                    console.error("[JCEF] cefQuery is unavailable");
                                                                    return;
                                                                }
                        
                                                                window.cefQuery({
                                                                    request: JSON.stringify({
                                                                        type: "progress",
                                                                        current: current,
                                                                        duration: duration
                                                                    }),
                        
                                                                    persistent: false,
                        
                                                                    onSuccess: function(response) {
                                                                        console.log("[JCEF] Progress received by Java");
                                                                    },
                        
                                                                    onFailure: function(code, message) {
                                                                        console.error("[JCEF] Query failed:", code, message);
                                                                    }
                                                                });
                        
                                                            }, 500);
                        
                                        // =====================================
                                        // SEEK
                                        // =====================================
                        
                                        window.seekVideo = function(seconds) {
                        
                                            if (!window.isPlayerReady()) {
                                                debug("Player not ready");
                                                return;
                                            }
                        
                                            const duration = window.player.getDuration();
                        
                                            if (!duration || duration <= 0) {
                                                debug("Invalid duration");
                                                return;
                                            }
                        
                                            const target = Math.max(
                                                0,
                                                Math.min(Number(seconds), duration)
                                            );
                        
                                            debug("Seeking to: " + target);
                        
                                            window.player.seekTo(target, true);
                        
                                            debug("Current time after seek: " +
                                                window.player.getCurrentTime());
                                        };
                        
                                        // =====================================
                                        // VOLUME AND REPEAT
                                        // =====================================
                        
                                        window.setVolume = function(value) {
                        
                                            executeWhenReady(function() {
                                                window.player.setVolume(value);
                                            });
                                        };
                        
                                        window.setRepeatEnabled = function(enabled) {
                                            window.repeatEnabled = enabled;
                                            debug("Repeat: " + enabled);
                                        };
                        
                                        // =====================================
                                        // TIME INFORMATION
                                        // =====================================
                        
                                        window.getCurrentTime = function() {
                        
                                            if (!window.isPlayerReady()) return 0;
                        
                                            return window.player.getCurrentTime();
                                        };
                        
                                        window.getDuration = function() {
                        
                                            if (!window.isPlayerReady()) return 0;
                        
                                            return window.player.getDuration();
                                        };
                        
                        
            </script>

            </body>
            </html>
            """;

                html = html.replace("VIDEO_ID", videoId)
                        .replace("ORIGIN", origin);

                byte[] response = html.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");

                exchange.sendResponseHeaders(200, response.length);

                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(response);
                }
            });

            playerServer.start();
            System.out.println("YouTube player server started: " + playerServerPort);

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

        if (cefBrowser == null) {
            System.err.println("[JCEF] Browser unavailable");
            return;
        }

        cefBrowser.executeJavaScript(
                javascript,
                cefBrowser.getURL(),
                0
        );
    }

    public void getVideoInformation(
            java.util.function.BiConsumer<Double, Double> callback) {

        double current = currentVideoTime;
        double duration = videoDuration;

        SwingUtilities.invokeLater(() ->
                callback.accept(current, duration)
        );
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

    private boolean playerPageLoaded = false;

    public void loadVideoInApp(String videoId) {

        if (videoId == null ||
                !videoId.matches("[a-zA-Z0-9_-]{11}")) {
            return;
        }

        if (cefBrowser == null) {
            System.err.println("[JCEF] Browser not initialized");
            return;
        }

        if (!playerPageLoaded) {

            String url = "http://127.0.0.1:"
                    + playerServerPort
                    + "/player/"
                    + videoId;

            System.out.println("[JCEF] Initial loading: " + url);

            cefBrowser.loadURL(url);
            playerPageLoaded = true;

        } else {

            System.out.println("[JCEF] Switching video: " + videoId);

            executePlayerCommand(
                    "window.loadNewVideo('" + videoId + "')"
            );
        }
    }

    private void enableGlobalSearchTyping() {

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(event -> {

                    // Only handle key presses
                    if (event.getID() != KeyEvent.KEY_PRESSED) {
                        return false;
                    }

                    // Ignore shortcuts such as Ctrl+C, Ctrl+V, Alt+Tab
                    if (event.isControlDown() ||
                            event.isAltDown() ||
                            event.isMetaDown()) {
                        return false;
                    }

                    // Ignore special keys
                    if (event.getKeyCode() == KeyEvent.VK_ESCAPE ||
                            event.getKeyCode() == KeyEvent.VK_ENTER ||
                            event.getKeyCode() == KeyEvent.VK_TAB ||
                            event.getKeyCode() == KeyEvent.VK_BACK_SPACE ||
                            event.getKeyCode() == KeyEvent.VK_DELETE) {
                        return false;
                    }

                    // Only process keys when this window is active
                    if (!isActive()) {
                        return false;
                    }

                    char c = event.getKeyChar();

                    if (!Character.isLetterOrDigit(c) &&
                            !Character.isWhitespace(c) &&
                            !Character.isDefined(c)) {
                        return false;
                    }

                    // Redirect focus to search field
                    if (!searchField.isFocusOwner()) {
                        searchField.requestFocusInWindow();
                    }

                    return false;
                });
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

    public JButton getPlayPauseButton() {
        return playPauseButton;
    }

    public JButton getRepeatButton() {
        return repeatButton;
    }



    public JSlider getSeekSlider() {
        return seekSlider;
    }

    public double getCurrentVideoTime() {
        return currentVideoTime;
    }

    public double getVideoDuration() {
        return videoDuration;
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