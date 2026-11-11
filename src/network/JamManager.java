
package network;

import controller.MusicController;

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
    }


    // HOST MODE
    public void startHosting(int port) {

        isHost = true;

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isConnected = true;

                System.out.println("Jam server started on port " + port);

                while (!serverSocket.isClosed()) {

                    Socket socket = serverSocket.accept();

                    PrintWriter writer = new PrintWriter(
                            socket.getOutputStream(), true
                    );

                    clientWriters.add(writer);

                    // Send latest state to newly connected client
                    if (latestState != null) {
                        writer.println(latestState);
                    }

                    new Thread(
                            new ClientHandler(socket, writer)
                    ).start();
                }

            } catch (IOException e) {
                if (isConnected) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    // CLIENT MODE
    public void joinHost(String ipAddress, int port) {

        isHost = false;

        new Thread(() -> {
            try {

                clientSocket = new Socket(ipAddress, port);

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
                    handleIncomingCommand(line);
                }

            } catch (IOException e) {

                isConnected = false;

                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(
                                musicController.getView(),
                                "Connection lost: " + e.getMessage()
                        )
                );

            } finally {
                isConnected = false;
            }
        }).start();
    }

    // SEND COMMAND
    public void sendToHost(String command) {

        if (!isHost && isConnected && out != null) {
            out.println(command);
        }
    }

    // BROADCAST TO EVERYONE

    public void broadcast(String command) {

        if (!isHost) return;

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

            broadcast(state);

        } else {

            sendToHost(state);
        }
    }


    // RECEIVE COMMAND
    private void handleIncomingCommand(String cmd) {

        String[] parts = cmd.split("\\|", 5);

        if (parts.length != 5 || !parts[0].equals("STATE")) {
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
            System.err.println(
                    "Invalid playback state: " + e.getMessage()
            );
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

                    // Client sends state to host.
                    // Host updates its own playback and broadcasts
                    // the state to all other clients.

                    if (line.startsWith("STATE|")) {

                        latestState = line;

                        handleIncomingCommand(line);

                        broadcast(line);
                    }
                }

            } catch (IOException e) {
                System.out.println("Client disconnected.");
            } finally {

                clientWriters.remove(writer);

                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
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
