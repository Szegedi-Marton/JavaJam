package com.javaJam.controller;

import com.formdev.flatlaf.FlatDarkLaf;
import com.javaJam.service.FontLoader;
import com.javaJam.view.MusicView;

import javax.swing.*;
import java.awt.*;


public class MainController {


    private MusicView view;
    private MusicController musicController;


    public MainController() {

        Font customAppFont = FontLoader.loadResourceFont("/fonts/DynaPuff.ttf", 13f);

        UIManager.put("defaultFont", customAppFont);
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