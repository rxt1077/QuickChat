package com.quickchat.quickchat;

import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum Network {
    INSTANCE;

    String CHARSET = "UTF-8";
    int PORT = 40941;

    MainActivity activity;
    ServerSocket serverSocket;
    Set<InetAddress> peers = new HashSet<InetAddress>();

    private Network() {
        startServer();
    }

    // this is the thread that handles incoming requests
    class SocketServerThread extends Thread {
        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(PORT);

                while (true) { // create a socket and pass it off
                    Socket socket = serverSocket.accept();

                    SocketServerCommandThread socketServerCommandThread = new SocketServerCommandThread(socket);
                    socketServerCommandThread.run();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // this is the thread that reads and performs commands
    class SocketServerCommandThread extends Thread {
        Socket socket;

        SocketServerCommandThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                OutputStream outputStream;
                InputStream inputStream;
                byte[] data = new byte[512];
                String dataString = "";
                JSONObject jsonData = null;
                String command = "";

                // setup our input and output streams
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
                inputStream.read(data, 0, data.length);

                // convert from bytes to a String (UTF-8 encoding)
                try {
                    dataString = new String(data, CHARSET);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                try {
                    // convert the string to a JSON object
                    jsonData = new JSONObject(dataString);
                    // extract the command from the JSON object
                    command = jsonData.getString("command");
                } catch (JSONException e) {
                    response(outputStream, false, "unable to parse command");
                    e.printStackTrace();
                }

                // take different actions based on the command
                switch (command) {
                    case "add":
                        peers.add(socket.getInetAddress());
                        response(outputStream, true, "peer added");
                        break;
                    case "remove":
                        peers.remove(socket.getInetAddress());
                        response(outputStream, true, "peer removed");
                        break;
                    case "message":
                        try {
                            String message = jsonData.getString("message");
                            activity.addMessage(message);
                            response(outputStream,true, "message delivered");
                        } catch (JSONException e) {
                            e.printStackTrace();
                            response(outputStream,false, "unable to parse message");
                        }
                        break;
                    case "getPeers":
                        try {
                            JSONObject jsonPeers = new JSONObject();
                            jsonPeers.put("peers", peers);
                            outputStream.write(jsonPeers.toString().getBytes(CHARSET));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            response(outputStream, false, "unable to send peers");
                        }
                        break;
                    default:
                        response(outputStream, false, "unknown command");
                }
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    void startServer() {
        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();
    }

    void stopServer() {
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    String getIPAddresses() {
        try {
            List<String> sAddrs = new ArrayList<String>();
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr.indexOf(':') < 0) //make sure it is an IPv4 address (no colons)
                            sAddrs.add(sAddr);
                    }
                }
            }
            return sAddrs.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    byte[] createResponse(boolean success, String message) {
        try {
            JSONObject response = new JSONObject();

            response.put("success", success);
            response.put("message", message);
            return response.toString().getBytes(CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    void response(OutputStream outputStream, boolean success, String message) {
        try {
            byte[] response = createResponse(success, message);
            if (response != null)
                outputStream.write(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}