package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started. Listening on port " + port + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Handling client: " + clientSocket);
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                System.out.println("Received: " + line);
                String[] parts = line.split(" ", 3);
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "CREATE":
                        if (parts.length < 2) {
                            writer.println("ERROR Missing file name!");
                            break;
                        }
                        try {
                            fsManager.createFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' created.");
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        break;
                    case "READ":
                        if (parts.length < 2) {
                            writer.println("ERROR Missing file name!");
                            break;
                        }
                        try {
                            byte[] response = fsManager.readFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' read. File contains: " + new String(response));
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        break;
                    case "WRITE":
                        if (parts.length < 3) {
                            writer.println("ERROR Missing arguments!!");
                            break;
                        }
                        try {
                            fsManager.writeFile(parts[1], parts[2].getBytes());
                            writer.println("SUCCESS: File '" + parts[1] + "' written.");
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        break;
                    case "DELETE":
                        if (parts.length < 2) {
                            writer.println("ERROR Missing file name!");
                            break;
                        }
                        try {
                            fsManager.deleteFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' deleted.");
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        break;
                    case "LIST":
                        try {
                            writer.println(java.util.Arrays.toString(fsManager.listFile()));
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        break;
                    case "QUIT":
                        writer.println("SUCCESS: Disconnecting.");
                        return;
                    default:
                        writer.println("ERROR: Unknown command.");
                        break;
                }
                writer.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client disconnected: " + clientSocket);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
