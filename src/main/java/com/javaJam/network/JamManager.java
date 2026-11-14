package com.javaJam.network;

import com.javaJam.controller.MusicController;
import com.javaJam.view.MusicView;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Base64;

public class JamManager {

    private ServerSocket serverSocket;
    private Socket clientSocket;

    private MusicView view;

    private PrintWriter out;
    private BufferedReader in;

    private final List<PrintWriter> clientWriters =
            new CopyOnWriteArrayList<>();

    private volatile boolean isHost = false;
    private volatile boolean isConnected = false;

    private final MusicController musicController;

    private volatile String latestState = null;

    public JamManager(MusicController controller) {
        this.musicController = controller;
        this.view = musicController.getMusicView();
    }


    // HOST MODE
    public void startHosting(int port) {

        isHost = true;

        new Thread(() -> {
            try {

                serverSocket = new ServerSocket(port);
                isConnected = true;

                System.out.println("[JAM HOST] Jam server started successfully on port " + port);

                while (!serverSocket.isClosed()) {

                    Socket socket = serverSocket.accept();
                    System.out.println("[JAM HOST] New client connected from: " + socket.getRemoteSocketAddress());
                    //set btn color
                    view.getJamButton().setBackground(view.accentColor);

                    PrintWriter writer = new PrintWriter(
                            socket.getOutputStream(), true
                    );

                    clientWriters.add(writer);
                    System.out.println("[JAM HOST] Active client connections: " + clientWriters.size());

                    // Send latest state to newly connected client
                    if (latestState != null) {
                        System.out.println("[JAM HOST] Syncing latest state to newly joined client...");
                        writer.println(latestState);
                    }

                    new Thread(
                            new ClientHandler(socket, writer)
                    ).start();
                }

                //set btn color
                view.getJamButton().setBackground(view.buttonDark);

            } catch (IOException e) {
                if (isConnected) {
                    System.err.println("[JAM HOST ERROR] Server socket exception: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "Jam-Host-Thread").start();
    }


    // CLIENT MODE
    public void joinHost(String ipAddress, int port) {

        isHost = false;
        System.out.println("[JAM CLIENT] Attempting to connect to host at " + ipAddress + ":" + port + "...");

        new Thread(() -> {
            try {

                clientSocket = new Socket(ipAddress, port);
                System.out.println("[JAM CLIENT] Successfully connected to host: " + clientSocket.getRemoteSocketAddress());
                //set btn color
                musicController.initCef();
                view.getJamButton().setBackground(view.accentColor);

                out = new PrintWriter(
                        clientSocket.getOutputStream(), true
                );

                in = new BufferedReader(
                        new InputStreamReader(
                                clientSocket.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                isConnected = true;

                String line;

                while ((line = in.readLine()) != null) {
                    System.out.println("[JAM CLIENT] Received from host -> " + line);
                    handleIncomingCommand(line);
                }

            } catch (IOException e) {
                System.err.println("[JAM CLIENT ERROR] Connection dropped or failed: " + e.getMessage());
                isConnected = false;

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                musicController.getMusicView(),
                                "Connection lost: " + e.getMessage()
                        )
                );

            } finally {
                isConnected = false;
                System.out.println("[JAM CLIENT] Disconnected from host session.");
                //set btn color
                view.getJamButton().setBackground(view.buttonDark);
            }
        }, "Jam-Client-Thread").start();
    }

    // SEND COMMAND
    public void sendToHost(String command) {
        if (!isHost && isConnected && out != null) {
            System.out.println("[JAM CLIENT] Sending to host -> " + command);
            out.println(command);
        } else {
            System.out.println("[JAM CLIENT WARNING] Failed to send to host. Connected: " + isConnected + ", Out writer ready: " + (out != null));
        }
    }

    // Send a track change request from client to host (or host broadcast)
    public void sendSelectTrackCommand(String videoId, String trackName) {
        String encodedName = Base64.getEncoder().encodeToString(trackName.getBytes(StandardCharsets.UTF_8));
        String command = "CONTROL|SELECT_TRACK|" + videoId + "|" + encodedName;

        if (isHost) {
            System.out.println("[JAM HOST] Executing local track selection -> " + trackName);
            handleControlCommand(command);
        } else {
            System.out.println("[JAM CLIENT] Transmitting track selection to host -> " + trackName);
            sendToHost(command);
        }
    }

    // Overload control command dispatcher for actions without numeric parameters (like NEXT, PREVIOUS, SHUFFLE, STOP)
    public void sendControlCommand(String action) {
        sendControlCommand(action, 0.0);
    }

    // BROADCAST TO EVERYONE

    public void broadcast(String command) {
        if (!isHost) {
            System.out.println("[JAM HOST WARNING] Non-host tried to broadcast.");
            return;
        }

        System.out.println("[JAM HOST] Broadcasting to " + clientWriters.size() + " clients -> " + command);
        for (PrintWriter writer : clientWriters) {
            writer.println(command);
        }
    }

    // PLAYBACK STATE
    public void broadcastState(
            String videoId,
            double position,
            boolean playing,
            String trackName
    ) {

        String encodedName = Base64.getEncoder()
                .encodeToString(
                        trackName.getBytes(StandardCharsets.UTF_8)
                );

        String state = String.join("|",
                "STATE",
                videoId,
                String.valueOf(position),
                String.valueOf(playing),
                encodedName
        );

        if (isHost) {
            latestState = state;
            System.out.println("[JAM HOST] Broadcasting state update [Video: " + videoId + ", Pos: " + position + ", Playing: " + playing + "]");
            broadcast(state);
        } else {
            System.out.println("[JAM CLIENT] Sending state update to host [Video: " + videoId + ", Pos: " + position + ", Playing: " + playing + "]");
            sendToHost(state);
        }
    }


    // RECEIVE COMMAND
    private void handleIncomingCommand(String cmd) {
        if (cmd.startsWith("STATE|")) {
            String[] parts = cmd.split("\\|", 5);
            if (parts.length != 5) {
                System.out.println("[JAM PARSE WARNING] Malformed STATE command: " + cmd);
                return;
            }

            try {
                String videoId = parts[1];
                double position = Double.parseDouble(parts[2]);
                boolean playing = Boolean.parseBoolean(parts[3]);
                String trackName = new String(
                        Base64.getDecoder().decode(parts[4]),
                        StandardCharsets.UTF_8
                );

               // System.out.println("[JAM SYNC] Processing incoming state -> Track: '" + trackName + "' [ID: " + videoId + "]");

                SwingUtilities.invokeLater(() ->
                        musicController.syncFromHost(
                                videoId,
                                position,
                                playing,
                                trackName
                        )
                );
            } catch (Exception e) {
                System.err.println("[JAM ERROR] Failed to parse state payload: " + e.getMessage());
            }
        } else if (cmd.startsWith("CONTROL|")) {
            // Clients also need to process control commands broadcasted down from the host!
            System.out.println("[JAM CLIENT] Received control command from host -> " + cmd);
            handleControlCommand(cmd);
        } else {
            System.out.println("[JAM PARSE WARNING] Ignored unknown command: " + cmd);
        }
    }


    // CLIENT HANDLER
    private class ClientHandler implements Runnable {

        private final Socket socket;
        private final PrintWriter writer;

        public ClientHandler(Socket socket, PrintWriter writer) {
            this.socket = socket;
            this.writer = writer;
        }

        @Override
        public void run() {
            System.out.println("[JAM HOST HANDLER] Started handler thread for client: " + socket.getRemoteSocketAddress());

            try (
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(),
                                    StandardCharsets.UTF_8
                            )
                    )
            ) {

                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println("[JAM HOST HANDLER] Received control message from client -> " + line);
                    if (line.startsWith("CONTROL|")) {
                        handleControlCommand(line);
                    }
                }

            } catch (IOException e) {
                System.out.println("[JAM HOST HANDLER] Client disconnected abruptly: " + socket.getRemoteSocketAddress());
                view.getJamButton().setBackground(view.buttonDark);
            } finally {
                clientWriters.remove(writer);
                System.out.println("[JAM HOST HANDLER] Cleaned up client writer. Remaining clients: " + clientWriters.size());

                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void sendControlCommand(String action, double position) {

        String command = "CONTROL|" + action + "|" + position;

        if (isHost) {
            System.out.println("[JAM HOST] Executing local control action -> " + command);
            handleControlCommand(command);
        } else {
            System.out.println("[JAM CLIENT] Transmitting control action to host -> " + command);
            sendToHost(command);
        }
    }

    private void handleControlCommand(String command) {
        String[] parts = command.split("\\|", 4);

        if (parts.length < 2 || !parts[0].equals("CONTROL")) {
            System.out.println("[JAM CONTROL WARNING] Invalid control command format: " + command);
            return;
        }

        String action = parts[1];


        if (action.equals("SELECT_TRACK") && parts.length >= 4) {
            String videoId = parts[2];
            String trackName = new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8);

            System.out.println("[JAM CONTROL] Dispatching remote track selection: " + trackName + " [" + videoId + "]");
            SwingUtilities.invokeLater(() -> musicController.remoteSelectTrack(videoId, trackName));

            if (isHost) {
                broadcast(command);
            }
            return;
        }


        double position = 0.0;
        if (parts.length >= 3) {
            try {
                position = Double.parseDouble(parts[2]);
            } catch (NumberFormatException e) {

            }
        }

        final double finalPos = position;
        System.out.println("[JAM CONTROL] Dispatching action: " + action + " with param: " + finalPos);

        SwingUtilities.invokeLater(() -> {
            switch (action) {
                case "PLAY":
                    musicController.remotePlay();
                    break;
                case "PAUSE":
                    musicController.remotePause();
                    break;
                case "SEEK":
                    musicController.remoteSeek(finalPos);
                    break;
                case "NEXT":
                    musicController.remoteNext();
                    break;
                case "PREVIOUS":
                    musicController.remotePrevious();
                    break;
                case "SHUFFLE":
                    musicController.remoteShuffle();
                    break;
                case "STOP":
                    musicController.remoteStop();
                    break;
                default:
                    System.out.println("[JAM CONTROL WARNING] Unhandled action type: " + action);
                    break;
            }
        });

        if (isHost) {
            broadcast(command);
        }
    }

    // STATUS
    public boolean isConnected() {
        return isConnected;
    }

    public boolean isHost() {
        return isHost;
    }
}