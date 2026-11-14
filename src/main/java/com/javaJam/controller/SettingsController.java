package com.javaJam.controller;

import com.javaJam.view.MusicView;
import com.javaJam.view.SettingsView;

import java.awt.*;

public class SettingsController {

    private SettingsView settingsView;
    private MusicView musicView;


    public SettingsController(MusicView musicView, SettingsView settingsView) {
        this.musicView = musicView;
        this.settingsView = settingsView;
        startUp();
    }

    private void startUp() {
        if (settingsView.getBackButton() != null) {
            System.out.println("Back button successfully bound!");
            settingsView.getBackButton().addActionListener(e -> onBack());
        } else {
            System.err.println("ERROR: Back button is NULL!");
        }
    }


    private void onBack()
    {
        System.out.println("back pressed");
        musicView.showMainView();
    }

}
