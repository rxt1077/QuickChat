package com.quickchat.quickchat;

import android.text.TextUtils;

import org.json.JSONArray;
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
                        if (peers.contains(socket.getInetAddress())) {
                            try {
                                String message = jsonData.getString("message");
                                activity.addMessage(message);
                                response(outputStream, true, "message delivered");
                            } catch (JSONException e) {
                                e.printStackTrace();
                                response(outputStream, false, "unable to parse message");
                            }
                        } else
                            response(outputStream, false, "not in peer list");
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

    // returns a string representation of the devices IPv4 addresses
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
            return TextUtils.join(",", sAddrs);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    // returns a string representation of the current peers
    String getPeers() {
        String[] host_addresses = new String[peers.size()];

        // the toString method on an InetAddress gives both the hostname/address causing most of
        // our peers to show up as just an IP with a slash in front. This fixes that.
        int i = 0;
        for (InetAddress addr: peers) {
            host_addresses[i] = addr.getHostAddress();
            i++;
        }
        return TextUtils.join(",", host_addresses);
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

    // replaces our peer list with one from another peer
    void updatePeers(InetAddress ip) {
        try {
            // create a socket and send our request
            Socket s = new Socket(ip, PORT);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            JSONObject request = new JSONObject();
            request.put("command", "getPeers");
            out.write(request.toString().getBytes(CHARSET));

            // read and convert the response
            byte[] buffer = new byte[1024];
            in.read(buffer);
            String jsonString = new String(buffer, CHARSET);
            JSONObject response = new JSONObject(jsonString);
            JSONArray json_peers = response.getJSONArray("peers");

            // set peers
            peers = new HashSet<InetAddress>();
            for (int i = 0; i < json_peers.length(); i++) {
                peers.add(InetAddress.getByName(json_peers.get(i).toString()));
            }
            peers.add(ip); // be sure to add the peer we got it from

        } catch (Exception ignored) {
        }
    }
}