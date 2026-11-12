package com.javaJam.controller;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.impl.progress.ConsoleProgressHandler;
import com.javaJam.model.Music;
import com.javaJam.network.JamManager;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandler;
import com.javaJam.service.YoutubeScraperService;
import com.javaJam.view.MusicView;

import javax.swing.*;
import java.awt.*;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MusicController {

    private CefApp cefApp;
    private CefClient cefClient;
    private CefBrowser cefBrowser;
    private CefMessageRouter messageRouter;
    private boolean trackStarted = false;

    private volatile double currentVideoTime = 0;
    private volatile double videoDuration = 0;

    private boolean playerPageLoaded = false;
    private CefMessageRouterHandler progressHandler;

    private HttpServer playerServer;
    private int playerServerPort;

    private final MusicView view;
    private final YoutubeScraperService scraper;
    private JamManager jamManager;
    // Add this field at the top of MusicController with your other variables
    private String loadedVideoId = null;
    private boolean isAdvancingTrack = false;
    private boolean progressRequestPending = false;
    private int playbackGeneration = 0;

    private List<Music> tracks;
    private int currentTrackIndex = -1;

    private boolean isPlaying = false;
    private boolean repeatEnabled = false;
    private boolean isSeeking = false;
    private Timer playbackTimer;
    private double cachedDuration = 0;
    private String currentVideoId = null;
    private String currentTrackName = "Nothing playing";
    private boolean syncingFromNetwork = false;

    public MusicController(MusicView view) {
        this.view = view;
        this.scraper = new YoutubeScraperService();
        this.jamManager = new JamManager(this);

        startPlayerServer();
        initCefPlayer();
        initController();
        startPlaybackTimer();
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

                builder.addJcefArgs(
                        "--autoplay-policy=no-user-gesture-required"
                );

                cefApp = builder.build();

                cefClient = cefApp.createClient();

                //Create browser
                cefBrowser = cefClient.createBrowser(
                        "about:blank",
                        false,
                        false
                );

                //Create message router
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

                        //System.out.println("[JCEF QUERY] " + request);

                        try {
                            org.json.JSONObject json =
                                    new org.json.JSONObject(request);

                            if ("playing".equals(json.optString("type"))) {

                                SwingUtilities.invokeLater(() -> {
                                    trackStarted = true;
                                });
                            }

                            if ("progress".equals(json.optString("type"))) {

                                double current = json.getDouble("current");
                                double duration = json.getDouble("duration");

                                currentVideoTime = current;
                                videoDuration = duration;

                              //  System.out.println("[JAVA PROGRESS] " + current + " / " + duration);
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

                // Inside your JCEF initialization code (where cefClient and cefBrowser are created)
                cefClient.addLoadHandler(new CefLoadHandlerAdapter() {
                    @Override
                    public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                        // Ensure it's our main frame and the page successfully loaded
                        if (frame.isMain() && httpStatusCode == 200) {
                            String currentUrl = browser.getURL();
                            if (currentUrl != null && currentUrl.contains("/player/")) {
                                System.out.println("[JCEF] Player page load complete. Sending initial JS start command...");

                                // Small delay or direct execution to ensure DOM/YouTube IFrame API is fully ready
                                SwingUtilities.invokeLater(() -> {
                                    if (isPlaying) {
                                        executePlayerCommand("if(typeof playVideo === 'function') { playVideo(); }");
                                    }
                                });
                            }
                        }
                    }
                });

                cefClient.addDisplayHandler(new CefDisplayHandlerAdapter() {

                    @Override
                    public boolean onConsoleMessage(
                            CefBrowser browser,
                            CefSettings.LogSeverity level,
                            String message,
                            String source,
                            int line) {

                        return false;
                    }
                });

                System.out.println("[JCEF] Message router initialized");

                SwingUtilities.invokeLater(() -> {

                    view.getBrowserPanel().add(
                            cefBrowser.getUIComponent(),
                            BorderLayout.CENTER
                    );

                    view.getBrowserPanel().revalidate();
                    view.getBrowserPanel().repaint();

                });

            } catch (Throwable e) {
                e.printStackTrace();
            }
        }, "JCEF-Initialization");

        cefThread.start();
    }

    private void startPlayerServer() {
        try {
            playerServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

            playerServerPort = playerServer.getAddress().getPort();

            playerServer.createContext("/player/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String videoId = path.substring("/player/".length());

                if (!videoId.matches("[a-zA-Z0-9_-]{11}")) {
                    exchange.sendResponseHeaders(400, -1);
                    exchange.close();
                    return;
                }

                String origin = "http://localhost:" + playerServerPort;

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
                
                <div id="player" referrerpolicy="strict-origin-when-cross-origin"></div>
                
                <script>
                window.player = null;
                window.isReady = false;
                window.repeatEnabled = false;
                window.pendingVideoId = 'VIDEO_ID';
                window.pendingCommands = [];

                function debug(message) {
                    console.log("[JS] " + message);
                }
                
                window.startInitialPlayback = function() {
                
                    if (!window.isPlayerReady()) {
                        debug("Player not ready yet");
                        return;
                    }
                
                    debug("Starting initial playback");
                
                    window.player.mute();
                    window.player.playVideo();
                };

                //
                //PLAYER READY CHECK
                //
                window.isPlayerReady = function() {
                    return window.isReady &&
                           window.player !== null;
                };
                
                //
                // LOAD YOUTUBE API
                //
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
                        origin: '__PLAYER_ORIGIN__'
                    },

                    events: {
                   onReady: function(event) {
                       window.isReady = true;
            
                       debug("Player READY");
            
                       while (window.pendingCommands.length > 0) {
                           window.pendingCommands.shift()();
                       }
            
                       window.startInitialPlayback();
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
                        
                                     debug("Playback started");
                                     
                                     window.player.unMute();
                        
                                     window.cefQuery({
                                         request: JSON.stringify({
                                             type: "playing"
                                         }),
                                         persistent: false,
                                         onSuccess: function(response) {},
                                         onFailure: function(code, message) {
                                             console.error("[JCEF]", message);
                                         }
                                     });
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
                        
                                 const errors = {
                                     2: "Invalid video ID or parameter",
                                     5: "HTML5 player error",
                                     100: "Video unavailable or private",
                                     101: "Embedding disabled by video owner",
                                     150: "Embedding disabled by video owner",
                                     153: "Missing HTTP Referer or client identification"
                                 };
                        
                                 console.error(
                                     "[YOUTUBE ERROR]",
                                     event.data,
                                     errors[event.data] || "Unknown error"
                                 );
                        
                                 console.log("[PLAYER URL]", window.location.href);
                                 console.log("[PLAYER ORIGIN]", window.location.origin);
                             }
                    }
                });
                };

                //
                //COMMAND QUEUE
                //
                function executeWhenReady(callback) {

                    if (window.isPlayerReady()) {
                        callback();
                    } else {
                        window.pendingCommands.push(callback);
                    }
                }

                //
                //VIDEO CONTROLS
                //
                window.loadNewVideo = function(videoId) {
                
                       window.pendingVideoId = videoId;
                
                       executeWhenReady(function() {
                
                           debug("Loading video: " + videoId);
                
                           window.player.loadVideoById({
                               videoId: videoId,
                               startSeconds: 0
                           });
                
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

                //
                // PLAYBACK INFORMATION TO JAVA
                //
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

                //
                // SEEK
                //
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

                //
                //VOLUME AND REPEAT
                //
                window.setVolume = function(value) {

                    executeWhenReady(function() {
                        window.player.setVolume(value);
                    });
                };

                window.setRepeatEnabled = function(enabled) {
                    window.repeatEnabled = enabled;
                    debug("Repeat: " + enabled);
                };

                //
                //TIME INFORMATION
                //
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

                html = html
                        .replace("VIDEO_ID", videoId)
                        .replace("__PLAYER_ORIGIN__", origin);

                byte[] response = html.getBytes(StandardCharsets.UTF_8);

                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "text/html; charset=UTF-8");
                headers.set("Access-Control-Allow-Origin", "*");
                headers.set(
                        "Referrer-Policy",
                        "strict-origin-when-cross-origin"
                );
                headers.set("Cache-Control", "no-store");
                headers.set("X-Content-Type-Options", "nosniff");

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


    private void initController() {
        // Search
        view.getSearchField().addActionListener(e -> {
            String query = view.getSearchField().getText().trim();
            if (!query.isEmpty()) {
                searchTracks(query);
            }
        });

        // Select a track from list
        view.getMusicListUI().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || syncingFromNetwork) return;

            Music selected = view.getMusicListUI().getSelectedValue();
            if (selected == null) return;

            currentTrackIndex = view.getMusicListUI().getSelectedIndex();

            if (jamManager.isConnected() && !jamManager.isHost()) {
                String videoId = extractVideoId(selected.getUrl());
                if (videoId != null) {
                    jamManager.sendSelectTrackCommand(videoId, selected.getName());
                }
                return;
            }

            handleTrackSelection(selected);
        });

        // Play
        view.getPlayPauseButton().addActionListener(e -> togglePlayPause());

        // Stop
        view.getStopButton().addActionListener(e -> {

            if (jamManager.isConnected() && !jamManager.isHost()) {
                jamManager.sendControlCommand("STOP", 0);
            } else {
                stopPlayback();
            }
        });

        // Previous
        view.getPrevButton().addActionListener(e -> {

            if (jamManager.isConnected() && !jamManager.isHost()) {
                jamManager.sendControlCommand("PREVIOUS", 0);
            } else {
                playPreviousTrack();
            }
        });


        // Next
        view.getNextButton().addActionListener(e -> {

            if (jamManager.isConnected() && !jamManager.isHost()) {
                jamManager.sendControlCommand("NEXT", 0);
            } else {
                playNextTrack();
            }
        });

        // Shuffle
        view.getShuffleButton().addActionListener(e -> {

            if (jamManager.isConnected() && !jamManager.isHost()) {
                jamManager.sendControlCommand("SHUFFLE", 0);
            } else {
                shuffleTrack();
            }
        });

        // Repeat
        view.getRepeatButton().addActionListener(e -> toggleRepeat());

        view.getJamButton().addActionListener(e -> handleJamAction());

        // Volume
        view.getVolumeSlider().addChangeListener(e -> {
            if (!view.getVolumeSlider().getValueIsAdjusting()) {
                setPlayerVolume(view.getVolumeSlider().getValue());
            }
        });

        view.getSeekSlider().addChangeListener(e -> {

            JSlider slider = view.getSeekSlider();

            if (slider.getValueIsAdjusting()) {

                isSeeking = true;

                double duration = cachedDuration;

                if (duration > 0) {
                    double target = duration * slider.getValue() / 1000.0;

                    view.getCurrentTimeLabel().setText(formatTime(target));
                }

            } else if (isSeeking) {

                isSeeking = false;

                double target = cachedDuration *
                        (slider.getValue() / 1000.0);

                if (jamManager.isConnected() && !jamManager.isHost()) {

                    jamManager.sendControlCommand("SEEK", target);

                } else {

                    seekVideo(target);
                    currentVideoTime = target;

                    broadcastCurrentState();
                }
            }
        });


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

    public void setRepeatEnabledInJs(boolean enabled) {
        executePlayerCommand("setRepeatEnabled(" + enabled + ")");
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

    public void loadVideoInApp(String videoId) {

        if (videoId == null ||
                !videoId.matches("[a-zA-Z0-9_-]{11}")) {
            return;
        }

        if (cefBrowser == null) {
            System.err.println("[JCEF] Browser not initialized");
            return;
        }

        loadedVideoId = videoId;
        currentVideoId = videoId;

        if (!playerPageLoaded) {

            String url = "http://localhost:"
                    + playerServerPort
                    + "/player/"
                    + videoId;

            System.out.println("[JCEF] Initial loading: " + url);

            playerPageLoaded = true;
            cefBrowser.loadURL(url);

        } else {

            System.out.println("[JCEF] Switching video: " + videoId);

            executePlayerCommand(
                    "window.loadNewVideo('" + videoId + "')"
            );
        }

        // Start playback after the YouTube player is ready.
        executePlayerCommand(
                "window.playVideo()"
        );
    }

    private void handleJamAction() {
        String[] options = {"Host Session", "Join Session", "Cancel"};
        int choice = JOptionPane.showOptionDialog(view, "Choose Jam Mode:", "Jam Session", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (choice == 0) {
            // HOST MODE
            String portStr = JOptionPane.showInputDialog(view, "Enter port to host on:", "8888");
            if (portStr != null && !portStr.trim().isEmpty()) {
                try {
                    int port = Integer.parseInt(portStr.trim());
                    jamManager.startHosting(port);

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(view, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else if (choice == 1) {
            // CLIENT MODE
            String input = JOptionPane.showInputDialog(view, "Enter Host IP:Port (e.g. 192.168.1.50:8888):", "127.0.0.1:8888");
            if (input != null && input.contains(":")) {
                String[] parts = input.split(":");
                String ip = parts[0].trim();
                try {
                    int port = Integer.parseInt(parts[1].trim());
                    jamManager.joinHost(ip, port);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(view, "Invalid IP or Port format.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void startPlaybackTimer() {

        playbackTimer = new Timer(500, e -> {

            if (isSeeking ||
                    isAdvancingTrack ||
                    progressRequestPending ||
                    view.getSeekSlider().getValueIsAdjusting()) {
                return;
            }

            int generation = playbackGeneration;

            progressRequestPending = true;

            getVideoInformation((currentTime, duration) -> {

                progressRequestPending = false;

                // Ignore results belonging to an older track.
                if (generation != playbackGeneration) {
                    return;
                }

                if (duration <= 0 || !isPlaying || isAdvancingTrack) {
                    return;
                }

                cachedDuration = duration;

                view.getCurrentTimeLabel().setText(formatTime(currentTime));
                view.getDurationLabel().setText(formatTime(duration));

                if (currentVideoId != null && isPlaying && !syncingFromNetwork) {

                    broadcastCurrentState();
                }

                if (isSeeking || view.getSeekSlider().getValueIsAdjusting()) {
                    return;
                }

                // Only advance after the current video has actually started.
                if (trackStarted &&
                        currentTime > 0 &&
                        duration - currentTime <= 2.0 &&
                        tracks != null &&
                        !tracks.isEmpty()) {

                    isAdvancingTrack = true;
                    isPlaying = false;
                    trackStarted = false;

                    System.out.println("[PLAYER] Track finished. Advancing...");

                    SwingUtilities.invokeLater(() -> {

                        view.getSeekSlider().setValue(0);
                        view.getCurrentTimeLabel().setText("00:00");

                        playNextTrack();
                    });

                    return;
                }

                int progress = (int) (currentTime / duration * 1000);

                view.getSeekSlider().setValue(
                        Math.max(0, Math.min(1000, progress))
                );
            });
        });

        playbackTimer.start();
    }

    private String formatTime(double seconds) {

        int totalSeconds = (int) Math.max(0, seconds);

        int minutes = totalSeconds / 60;
        int remainingSeconds = totalSeconds % 60;

        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    private void searchTracks(String query) {
        new SwingWorker<List<Music>, Void>() {
            @Override
            protected List<Music> doInBackground() {
                return scraper.searchTracks(query);
            }

            @Override
            protected void done() {
                try {
                    tracks = get();
                    view.getListModel().clear();

                    for (Music track : tracks) {
                        view.getListModel().addElement(track);
                    }

                    currentTrackIndex = -1;
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(view, "Unable to search YouTube tracks.", "Search Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void handleTrackSelection(Music track) {

        if (syncingFromNetwork) return;

        playbackGeneration++;

        trackStarted = false;
        isAdvancingTrack = false;
        isPlaying = true;
        cachedDuration = 0;
        currentVideoTime = 0;

        currentTrackName = track.getName();

        view.getSeekSlider().setValue(0);
        view.getCurrentTimeLabel().setText("00:00");

        view.setNowPlaying(track.getName());
        view.getPlayPauseButton().setText("Ⅱ");

        String videoId = extractVideoId(track.getUrl());

        if (videoId != null) {
            currentVideoId = videoId;

            loadVideoInApp(videoId);
            broadcastCurrentState();
        }
    }

    public void syncFromHost(
            String videoId,
            double position,
            boolean playing,
            String trackName
    ) {
        syncingFromNetwork = true;
        try {
            playbackGeneration++;
            trackStarted = false;
            isAdvancingTrack = false;
            currentTrackName = trackName;

            view.setNowPlaying(trackName);
            view.getPlayPauseButton().setText(playing ? "Ⅱ" : "▶");

            if (videoId != null && (!videoId.equals(currentVideoId) || !playerPageLoaded || loadedVideoId == null)) {
                //System.out.println("[JAM SYNC] Initializing/Switching to track from host: " + videoId);
                loadVideoInApp(videoId);
            }

            double timeDiff = Math.abs(currentVideoTime - position);
            if (timeDiff > 2.0) {
                //System.out.println("[JAM SYNC] Drift detected (" + timeDiff + "s), seeking to: " + position);
                seekVideo(position);
            }

            currentVideoTime = position;
            isPlaying = playing;

            if (playing) {
                playVideo();
            } else {
                pauseVideo();
            }
        } finally {
            syncingFromNetwork = false;
        }
    }

    private void broadcastCurrentState() {
        if (currentVideoId == null) return;

        //host should broadcast states
        if (jamManager.isConnected() && jamManager.isHost()) {
            jamManager.broadcastState(
                    currentVideoId,
                    currentVideoTime,
                    isPlaying,
                    currentTrackName
            );
        }
    }


    private void togglePlayPause() {
        if (jamManager.isConnected() && !jamManager.isHost()) {
            if (isPlaying) {
                jamManager.sendControlCommand("PAUSE", currentVideoTime);
            } else {
                jamManager.sendControlCommand("PLAY", currentVideoTime);
            }
            return;
        }

        if (isPlaying) {
            pauseVideo();
            isPlaying = false;
            view.getPlayPauseButton().setText("▶");
            jamManager.sendControlCommand("PAUSE", currentVideoTime);
        } else {
            playVideo();
            isPlaying = true;
            view.getPlayPauseButton().setText("Ⅱ");
            jamManager.sendControlCommand("PLAY", currentVideoTime);
        }
    }

    private void stopPlayback() {
        stopVideo();
        isPlaying = false;
        view.getPlayPauseButton().setText("▶");
        view.getSeekSlider().setValue(0);
        view.getCurrentTimeLabel().setText("00:00");
        view.setNowPlaying("Nothing playing");
        if (jamManager.isConnected() && jamManager.isHost()) {
            jamManager.sendControlCommand("STOP", 0);
        }
    }

    private void toggleRepeat() {
        repeatEnabled = !repeatEnabled;
        view.getRepeatButton().setText(repeatEnabled ? "Repeat ON" : "Repeat");
        setRepeatEnabledInJs(repeatEnabled);
    }

    private void playNextTrack() {
        int size = view.getListModel().size();
        if (size == 0) {
            isAdvancingTrack = false;
            return;
        }

        currentTrackIndex++;
        if (currentTrackIndex >= size) {
            currentTrackIndex = 0;
        }
        selectTrackAt(currentTrackIndex);
    }

    private void playPreviousTrack() {
        int size = view.getListModel().size();
        if (size == 0) return;

        currentTrackIndex--;
        if (currentTrackIndex < 0) {
            currentTrackIndex = size - 1;
        }
        selectTrackAt(currentTrackIndex);
    }

    private void shuffleTrack() {
        int size = view.getListModel().size();
        if (size <= 1) return;

        int randomIndex;
        do {
            randomIndex = (int) (Math.random() * size);
        } while (randomIndex == currentTrackIndex);

        selectTrackAt(randomIndex);
    }

    private void selectTrackAt(int index) {
        view.getMusicListUI().setSelectedIndex(index);
        view.getMusicListUI().ensureIndexIsVisible(index);
        Music track = view.getListModel().getElementAt(index);
        if (track != null) {
            String videoId = extractVideoId(track.getUrl());
            if (videoId != null) {
                if (jamManager.isConnected() && !jamManager.isHost()) {
                    jamManager.sendSelectTrackCommand(videoId, track.getName());
                } else {
                    handleTrackSelection(track);
                    if (jamManager.isConnected() && jamManager.isHost()) {
                        jamManager.sendSelectTrackCommand(videoId, track.getName());
                    }
                }
            }
        }
        isAdvancingTrack = false;
    }

    private String extractVideoId(String url) {
        if (url == null) return null;

        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host == null) return null;

            String id = null;
            if (host.equals("youtu.be")) {
                id = uri.getPath().substring(1);
            } else if (host.endsWith("youtube.com")) {
                String query = uri.getRawQuery();
                if (query != null) {
                    for (String parameter : query.split("&")) {
                        if (parameter.startsWith("v=")) {
                            id = parameter.substring(2);
                            break;
                        }
                    }
                }
            }

            if (id != null && id.matches("[a-zA-Z0-9_-]{11}")) {
                return id;
            }
        } catch (Exception e) {
            System.err.println("Invalid YouTube URL: " + url);
        }

        return null;
    }


    public void remotePlay() {
        isPlaying = true;
        playVideo();
        view.getPlayPauseButton().setText("Ⅱ");
    }

    public void remotePause() {
        isPlaying = false;
        pauseVideo();
        view.getPlayPauseButton().setText("▶");
    }

    public void remoteSeek(double position) {
        seekVideo(position);
        currentVideoTime = position;
    }

    public void remoteNext() {
        playNextTrack();
    }

    public void remotePrevious() {
        playPreviousTrack();
    }

    public void remoteShuffle() {
        shuffleTrack();
    }

    public void remoteStop() {
        stopVideo();
        isPlaying = false;
        view.getPlayPauseButton().setText("▶");
        view.getSeekSlider().setValue(0);
        view.getCurrentTimeLabel().setText("00:00");
        view.setNowPlaying("Nothing playing");
    }

    public void remoteSelectTrack(String videoId, String trackName) {
        syncingFromNetwork = true;
        try {
            currentTrackName = trackName;
            currentVideoId = videoId;
            isPlaying = true;
            view.setNowPlaying(trackName);
            view.getPlayPauseButton().setText("Ⅱ");
            loadVideoInApp(videoId);
        } finally {
            syncingFromNetwork = false;
        }
    }

    public MusicView getView() {
        return view;
    }


    public void shutdown() {

        if (playerServer != null) {
            playerServer.stop(0);
        }

        if (cefBrowser != null) {
            cefBrowser.close(true);
        }

        if (cefClient != null) {
            cefClient.dispose();
        }

        if (cefApp != null) {
            cefApp.dispose();
        }
    }
}