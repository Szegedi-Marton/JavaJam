package controller;

import model.Music;
import network.JamManager;
import service.YoutubeScraperService;
import view.MusicView;

import javax.swing.*;
import java.util.List;

public class MusicController {

    private final MusicView view;
    private final YoutubeScraperService scraper;
    private JamManager jamManager;

    private List<Music> tracks;
    private int currentTrackIndex = -1;

    private boolean isPlaying = false;
    private boolean repeatEnabled = false;
    private boolean isSeeking = false;
    private Timer playbackTimer;

    public MusicController(MusicView view) {
        this.view = view;
        this.scraper = new YoutubeScraperService();
        this.jamManager = new JamManager(view);

        initController();
        startPlaybackTimer();
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
            if (!e.getValueIsAdjusting()) {
                Music selected = view.getMusicListUI().getSelectedValue();
                if (selected != null) {
                    currentTrackIndex = view.getMusicListUI().getSelectedIndex();
                    handleTrackSelection(selected);
                }
            }
        });

        // Play / Pause
        view.getPlayPauseButton().addActionListener(e -> togglePlayPause());

        // Stop
        view.getStopButton().addActionListener(e -> {

            view.stopVideo();

            isPlaying = false;

            view.getPlayPauseButton().setText("▶");

            view.getSeekSlider().setValue(0);

            view.getCurrentTimeLabel().setText("00:00");

            view.setNowPlaying("Nothing playing");
        });

        // Previous
        view.getPrevButton().addActionListener(e -> playPreviousTrack());

        // Next
        view.getNextButton().addActionListener(e -> playNextTrack());

        // Shuffle
        view.getShuffleButton().addActionListener(e -> shuffleTrack());

        // Repeat
        view.getRepeatButton().addActionListener(e -> toggleRepeat());

        view.getJamButton().addActionListener(e -> handleJamAction());

        // Volume
        view.getVolumeSlider().addChangeListener(e -> {
            if (!view.getVolumeSlider().getValueIsAdjusting()) {
                view.setPlayerVolume(view.getVolumeSlider().getValue());
            }
        });

        view.getSeekSlider().addChangeListener(e -> {

            JSlider slider = view.getSeekSlider();

            if (slider.getValueIsAdjusting()) {
                isSeeking = true;
                return;
            }

            if (isSeeking) {
                isSeeking = false;

                double duration = view.getCachedDuration();

                if (duration > 0) {
                    double target =
                            duration * slider.getValue() / 1000.0;

                    view.seekVideo(target);
                    jamManager.broadcast("SEEK:" + target);
                }
            }
        });


    }

    private void handleJamAction() {
        String[] options = {"Host Session", "Join Session", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                view,
                "Choose Jam Mode:",
                "Jam Session",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            // HOST MODE
            String portStr = JOptionPane.showInputDialog(view, "Enter port to host on:", "8888");
            if (portStr != null && !portStr.trim().isEmpty()) {
                try {
                    int port = Integer.parseInt(portStr.trim());
                    jamManager.startHosting(port);
                    view.getDetailsArea().append("\n[Hosting Jam on port " + port + "]\n");
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(view, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        } else if (choice == 1) {
            // CLIENT / JOIN MODE
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

        if (playbackTimer != null) {
            playbackTimer.stop();
        }

        playbackTimer = new Timer(500, e -> {

            view.getVideoInformation((currentTime, totalDuration) -> {

                view.getCurrentTimeLabel().setText(
                        formatTime(currentTime)
                );

                view.getDurationLabel().setText(
                        formatTime(totalDuration)
                );

                if (!view.getSeekSlider().getValueIsAdjusting()
                        && totalDuration > 0) {

                    int progress = (int) (
                            (currentTime / totalDuration) * 1000.0
                    );

                    view.getSeekSlider().setValue(
                            Math.max(0, Math.min(1000, progress))
                    );
                }
            });
        });

        playbackTimer.start();
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
                    JOptionPane.showMessageDialog(
                            view,
                            "Unable to search YouTube tracks.",
                            "Search Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private void handleTrackSelection(Music track) {
        view.getDetailsArea().setText(
                "Now Playing / Selected:\n" +
                        "Title: " + track.getName() + "\n" +
                        "URL: " + track.getUrl()
        );

        view.setNowPlaying(track.getName());
        isPlaying = true;
        view.getPlayPauseButton().setText("Ⅱ");

        System.out.println("Playing in-app: " + track);

        String videoId = extractVideoId(track.getUrl());
        if (videoId != null) {
            view.loadVideoInApp(videoId);

            // Broadcast to clients if we are the host!
            jamManager.broadcast("PLAY_VIDEO:" + videoId);
        }
    }

    private void togglePlayPause() {
        if (isPlaying) {
            view.pauseVideo();
            isPlaying = false;
            view.getPlayPauseButton().setText("▶");
            jamManager.broadcast("PAUSE");
        } else {
            view.playVideo();
            isPlaying = true;
            view.getPlayPauseButton().setText("Ⅱ");
            jamManager.broadcast("PLAY");
        }
    }

    private void toggleRepeat() {
        repeatEnabled = !repeatEnabled;
        view.getRepeatButton().setText(repeatEnabled ? "Repeat ON" : "Repeat");
        view.setRepeatEnabledInJs(repeatEnabled);
    }

    private void playNextTrack() {
        int size = view.getListModel().size();
        if (size == 0) return;

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
            handleTrackSelection(track);
        }
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
}