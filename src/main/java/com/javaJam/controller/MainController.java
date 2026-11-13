package com.javaJam.controller;

import com.formdev.flatlaf.FlatDarkLaf;
import com.javaJam.view.MusicView;

import javax.swing.*;


public class MainController {


    private MusicView view;
    private MusicController musicController;


    public MainController() {

        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);

        try {
            FlatDarkLaf.setup();
        } catch (Exception ex) {
            System.err.println("Failed to initialize FlatLaf");
        }

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