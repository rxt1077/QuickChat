package com.quickchat.quickchat;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum  Network {
    INSTANCE;

    String CHARSET = "UTF-8";
    int PORT = 40941;
    int BUFFSIZE = 1024; // max size of a read or write in bytes

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
                OutputStream out;
                InputStream in;
                JSONObject jsonData;
                String command = "";

                // setup our input and output streams
                out = socket.getOutputStream();
                in = socket.getInputStream();

                // read the JSON from the socket
                jsonData = readJSON(in);
                if (jsonData == null) {
                    response(out, false, "unable read JSON");
                    in.close();
                    out.close();
                    return;
                }

                // get the command
                try {
                    command = jsonData.getString("command");
                } catch (JSONException e) {
                    response(out, false, "unable to parse command");
                    e.printStackTrace();
                    in.close();
                    out.close();
                    return;
                }

                // take different actions based on the command
                switch (command) {
                    case "add":
                        peers.add(socket.getInetAddress());
                        response(out, true, "peer added");
                        break;
                    case "remove":
                        peers.remove(socket.getInetAddress());
                        response(out, true, "peer removed");
                        break;
                    case "message":
                        // only accept messages from peers we know
                        if (peers.contains(socket.getInetAddress())) {
                            try {
                                String message = jsonData.getString("message");
                                activity.addMessage(message);
                                response(out, true, "message delivered");
                            } catch (JSONException e) {
                                e.printStackTrace();
                                response(out, false, "unable to parse message");
                            }
                        } else
                            response(out, false, "not in peer list");
                        break;
                    case "getPeers":
                        try {
                            JSONArray jsonPeers = new JSONArray(peers);
                            JSONObject jsonResponse = new JSONObject();
                            jsonResponse.put("peers", jsonPeers);
                            writeJSON(out, jsonResponse);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            response(out, false, "unable to send peers");
                        }
                        break;
                    default:
                        response(out, false, "unknown command");
                }
                in.close();
                out.close();
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

    // returns a list of our IP addresses
    List<InetAddress> ip() {
        try {
            List<InetAddress> addrs = new ArrayList<>();
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces)
                addrs.addAll(Collections.list(intf.getInetAddresses()));
            return addrs;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // returns a string representation of the IPv4 addresses that can be used to connect to us
    String ipToString() {
        List<InetAddress> addrs = ip();
        List<String> sAddrs = new ArrayList<>();

        for (InetAddress addr: addrs) {
            if (!addr.isLoopbackAddress()) { // make sure it's not a loopback device
                String sAddr = addr.getHostAddress();
                if (sAddr.indexOf(':') < 0) //make sure it's an IPv4 address (no colons)
                    sAddrs.add(sAddr);
            }
        }
        return TextUtils.join(",", sAddrs);
    }

    // returns a string representation of the current peers
    String peersToString() {
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

    // reads JSON from an InputStream, handling conversion
    JSONObject readJSON(InputStream in) {
        byte[] data = new byte[BUFFSIZE];
        String dataString;
        int count;

        try {
            // read the data
            count = in.read(data, 0, data.length);
            // convert from bytes to a String (UTF-8 encoding)
            dataString = new String(data, CHARSET);
            // convert the string to a JSON object
            System.out.printf("readJSON: %s\n", dataString);
            return new JSONObject(dataString);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // writes JSON to an OutputStream, handling converion
    void writeJSON(OutputStream out, JSONObject object) {
        System.out.printf("writeJSON: %s\n", object.toString());
        try {
            out.write(object.toString().getBytes(CHARSET));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // creates a sends a standard JSON response { "success": boolean, "message": String }
    void response(OutputStream out, boolean success, String message) {
        try {
            JSONObject responseJSON = new JSONObject();

            responseJSON.put("success", success);
            responseJSON.put("message", message);
            writeJSON(out, responseJSON);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // replaces our peer list with one from another peer
    void getPeers(InetAddress ip) {
        try {
            // create a socket and send our request
            Socket s = new Socket(ip, PORT);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            JSONObject request = new JSONObject();
            request.put("command", "getPeers");
            writeJSON(out, request);

            // read the response
            JSONObject response = readJSON(in);
            if (response == null)
                return;

            // create a new peer list
            List<InetAddress> local_addrs = ip();
            peers = new HashSet<InetAddress>();
            JSONArray json_peers = response.getJSONArray("peers");
            for (int i = 0; i < json_peers.length(); i++) {
                InetAddress peer = InetAddress.getByName(json_peers.get(i).toString());
                if (! local_addrs.contains(peer)) // do not add ourselves
                    peers.add(peer);
            }
            peers.add(ip); // be sure to add the peer we got it from
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // make a request to an address that they add us to their peers list
    void requestAdd(InetAddress ip) {
        try {
            // create a socket and send our request
            Socket s = new Socket(ip, PORT);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            JSONObject request = new JSONObject();
            request.put("command", "add");
            writeJSON(out, request);

            // read the response
            JSONObject response = readJSON(in);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // send out requests to all our peers that they add us to their peers list
    void requestAddAllPeers() {
        for (InetAddress addr: peers)
            requestAdd(addr);
    }
}