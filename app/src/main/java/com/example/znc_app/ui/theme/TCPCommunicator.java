package com.example.znc_app.ui.theme;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPCommunicator {
    private String serverIp;
    private int serverPort;
    private Socket socket;
    private BufferedReader bufferedReader;
    private PrintWriter printWriter;
    private Handler handler;
    private TCPListener listener;
    private final ExecutorService executor; // For single, sequential network operations
    private Thread readingThread; // A dedicated thread for the blocking read operation


    public TCPCommunicator(String serverIp, int serverPort, TCPListener listener) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        this.executor = Executors.newSingleThreadExecutor();
    }

    public interface TCPListener {
        void onMessageReceived(String message);
        void onError(Exception e);
    }

    public void connect() {
        executor.submit(() -> {
            try {
                socket = new Socket(serverIp, serverPort);
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                printWriter = new PrintWriter(socket.getOutputStream(), true);
    
                handler.post(() -> {
                    if (listener != null) {
                        listener.onMessageReceived("{\"type\":\"connected\"}");
                    }
                });
    
                // The blocking read operation must be in its own dedicated thread,
                // not in the executor's thread pool, to avoid deadlocks.
                readingThread = new Thread(this::readMessages);
                readingThread.start();
    
            } catch (IOException e) {
                handleError(e);
            }
        });
    }
    
    private void readMessages() {
        try {
            String line;
            while (!Thread.currentThread().isInterrupted() && (line = bufferedReader.readLine()) != null) {
                final String message = line;
                Log.i("TCPCOMMER", "recv JSON: " + message);
                handler.post(() -> {
                    if (listener != null) {
                        listener.onMessageReceived(message);
                    }
                });
            }
        } catch (IOException e) {
            // This exception is expected when the socket is closed from the other end or by disconnect()
            Log.i("TCPCOMMER", "Socket closed, reading thread terminated.");
        } finally {
            // When loop exits (due to error or clean disconnect), signal disconnection
            handler.post(() -> {
                if (listener != null) {
                    listener.onMessageReceived("{\"type\":\"disconnected\"}");
                }
            });
        }
    }
    
    
    private void handleError(Exception e) {
        if (listener != null) {
            handler.post(() -> listener.onError(e));
        }
        disconnect(); // Attempt to clean up on error
    }
    
    public void sendMessage(final String message) {
        executor.submit(() -> {
            // Add critical logging to see exactly what is being sent.
            Log.i("TCPCOMMER", "Sending message: " + message);
            if (printWriter != null && !printWriter.checkError()) {
                printWriter.println(message);
            }
        });
    }
    
    public void disconnect() {
        executor.submit(() -> {
            try {
                // Interrupt the dedicated reading thread
                if (readingThread != null && readingThread.isAlive()) {
                    readingThread.interrupt();
                }
                // Closing the socket will also cause the blocking readLine() to throw an exception,
                // which is another way to stop the reading thread.
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e("TCPCommunicator", "Error while closing socket", e);
            } finally {
                // The readMessages loop's finally block will handle sending the "disconnected" message.
                // We just need to shut down the executor.
                if (!executor.isShutdown()) {
                    executor.shutdown();
                }
            }
        });
    }
    public boolean isconnect(){
        if (socket == null)
            return false;
        return socket.isConnected();
    }
    public boolean isClose(){
        if (socket == null)
            return true;
        return socket.isClosed();
    }
}