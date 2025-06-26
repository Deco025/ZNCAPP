package com.example.znc_app.ui.theme;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.*;
import java.net.Socket;

public class TCPCommunicator {
    private String serverIp;
    private int serverPort;
    private Socket socket;
    private BufferedReader bufferedReader;
    private PrintWriter printWriter;
    private OutputStream outputStream;
    private Handler handler;
    private TCPListener listener;
    private InputStream inputStream;
    private boolean connectflag=false;

    public TCPCommunicator(String serverIp, int serverPort, TCPListener listener) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public interface TCPListener {
        void onMessageReceived(int[] message);
        void onError(Exception e);
    }

    public void connect() {
        connectflag = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(serverIp, serverPort);
                    if (socket == null)
                        return;
                    if (socket.isConnected())
                    {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (listener != null) {
                                    listener.onMessageReceived(new int[]{0x12});
                                }
                            }
                        });
                    }
                    bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    printWriter = new PrintWriter(socket.getOutputStream(), true);
                    outputStream = socket.getOutputStream();
                    inputStream = socket.getInputStream();
                    // 启动接收消息的线程
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                byte[] buffer = new byte[1024]; // 缓冲区大小可以根据需要调整
                                int bytesRead;
                                while (socket.isConnected() && (bytesRead = inputStream.read(buffer)) != -1 ) {
                                    int [] data =new int[bytesRead];

                                    for (int i = 0; i < bytesRead; i++) {
                                        data[i] = (int) buffer[i] & 0xFF; // 将 byte 转换为无符号 int
                                    }
                                    String res ="";
                                    for (int i =0 ;i< bytesRead;i++)
                                        res += String.format("%d ",data[i]);
                                    res += String.format("len %d",buffer.length);
                                    Log.i("TCPCOMMER","recvdata:  "+ res);
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (listener != null) {

                                                listener.onMessageReceived(data);
                                            }
                                        }
                                    });
                                }
                                listener.onMessageReceived(new int[]{0x21});
                            } catch (IOException e) {
                                if (listener != null) {
                                    listener.onError(e);
                                    errorLoop();
                                }
                            }
                        }
                    }).start();
                } catch (IOException e) {
                    if (listener != null) {
                        listener.onError(e);
                        errorLoop();
                    }
                }
            }
        }).start();
    }
    public void errorLoop(){
        listener.onMessageReceived(new int[]{0x21});
        try {
            if(socket!=null)
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void sendMessage(final String message) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (socket != null && !socket.isClosed() && printWriter != null) {
                        printWriter.println(message);
                        printWriter.flush();
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onError(e);
                        errorLoop();
                    }
                }
            }
        }).start();
    }

    public void sendBytes(final byte[] data) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (socket != null && !socket.isClosed() && outputStream != null) {
                        outputStream.write(data);
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            }
        }).start();
    }
    public void disconnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (bufferedReader != null) {
                        bufferedReader.close();
                    }
                    if (printWriter != null) {
                        printWriter.close();
                    }
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            }
        }).start();
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