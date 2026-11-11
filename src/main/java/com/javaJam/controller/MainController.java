package com.javaJam.controller;

import com.javaJam.view.MusicView;

public class MainController {

    private MusicView view;
    private MusicController musicController;

    public MainController() {
        view = new MusicView();
        musicController = new MusicController(view);
    }

    public void start() {
        view.setVisible(true);
    }

    public void shutdown() {
        musicController.shutdown();
    }
}