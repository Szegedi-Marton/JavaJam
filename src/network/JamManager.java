package network;

import controller.MusicController;
import view.MusicView;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class JamManager {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private final List<PrintWriter> clientWriters = new ArrayList<>();
    private boolean isHost = false;
    private boolean isConnected = false;
    private MusicController musicController;
    private Thread listenerThread;
    private volatile String latestState = null;

    public JamManager(MusicController controller) {
        this.musicController = controller;
    }

    // HOST MODE
    public void startHosting(int port) {
        isHost = true;
        isConnected = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);


                while (true) {
                    Socket socket = serverSocket.accept();
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    synchronized (clientWriters) {
                        clientWriters.add(writer);
                    }

                    // Listen to incoming client
                    new Thread(new ClientHandler(socket)).start();
                    if (latestState != null) {
                        writer.println(latestState);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Broadcast a command to all clients
    public void broadcast(String command) {
        if (!isHost) return;
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println(command);
            }
        }
    }

    // CLIENT MODE
    public void joinHost(String ipAddress, int port) {
        isHost = false;
        new Thread(() -> {
            try {
                clientSocket = new Socket(ipAddress, port);
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                isConnected = true;


                String line;
                while ((line = in.readLine()) != null) {
                    handleIncomingCommand(line);
                }
            } catch (IOException e) {
                isConnected = false;
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                musicController.getView(),
                                "Failed to connect to host: " + e.getMessage()
                        )
                );
            }
        }).start();
    }

    // Send a command to the host if client wants to control,
    // or use this strictly for Host-to-Client synchronization.
    public void sendToHost(String command) {
        if (!isHost && out != null) {
            out.println(command);
        }
    }


    // COMMAND PARSER n EXECUTOR
    private void handleIncomingCommand(String cmd) {

        String[] parts = cmd.split("\\|", 5);

        if (parts.length < 5 || !parts[0].equals("STATE")) {
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

            SwingUtilities.invokeLater(() ->
                    musicController.syncFromHost(
                            videoId,
                            position,
                            playing,
                            trackName
                    )
            );

        } catch (Exception e) {
            System.err.println("Invalid playback state: " + e.getMessage());
        }
    }

    private class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    // If clients can send commands back to host, handle them here
                }
            } catch (IOException ignored) {
            }
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isHost() {
        return isHost;
    }

    public void broadcastState(
            String videoId,
            double position,
            boolean playing,
            String trackName
    ) {
        if (!isHost) return;

        String encodedName = Base64.getEncoder().encodeToString(
                trackName.getBytes(StandardCharsets.UTF_8)
        );

        String state = String.join("|",
                "STATE",
                videoId,
                String.valueOf(position),
                String.valueOf(playing),
                encodedName
        );

        latestState = state;

        broadcast(state);
    }
}