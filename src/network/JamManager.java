package network;

import controller.MusicController;
import view.MusicView;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
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
        String[] parts = cmd.split(":", 2);
        String action = parts[0];
        String data = parts.length > 1 ? parts[1] : "";

        SwingUtilities.invokeLater(() -> {
            switch (action) {
                case "PLAY_VIDEO":
                    musicController.loadVideoInApp(data);
                    break;
                case "PAUSE":
                    musicController.pauseVideo();
                    break;
                case "PLAY":
                    musicController.playVideo();
                    break;
                case "SEEK":
                    try {
                        double seconds = Double.parseDouble(data);
                        musicController.seekVideo(seconds);
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                case "STOP":
                    musicController.stopVideo();
                    break;
            }
        });
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
}