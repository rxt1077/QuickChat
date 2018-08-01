package com.quickchat.quickchat;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.DatagramSocket;
import java.net.SocketException;
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
    Set<InetAddress> peers = new HashSet<InetAddress>();
    String messages = "";

    // start our server thread as soon as we are created
    private Network() {
        try {
            Thread socketServerThread = new Thread(new ServerThread());
            socketServerThread.start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    // this thread listens for new UDP packets on PORT and creates a new thread to handle them
    class ServerThread extends Thread {
        DatagramSocket socket;

        ServerThread() throws SocketException {
            socket = new DatagramSocket(PORT);
        }

        @Override
        public void run() {
            boolean running = true;
            byte[] buffer = new byte[BUFFSIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, BUFFSIZE);
                try {
                    socket.receive(packet);
                    ServerCommandThread ServerCommandThread = new ServerCommandThread(packet);
                    ServerCommandThread.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // this is the thread takes a UDP packet and performs a command
        class ServerCommandThread extends Thread {
            DatagramPacket packet;

            ServerCommandThread(DatagramPacket packet) {
                this.packet = packet;
            }

            @Override
            public void run() {
                JSONObject jsonData;
                String command;

                jsonData = packetToJSON(packet);
                if (jsonData == null) {
                    response(false, "unable to read command");
                    return;
                }

                // get the command
                try {
                    command = jsonData.getString("command");
                } catch (JSONException e) {
                    response(false, "unable to parse command");
                    e.printStackTrace();
                    return;
                }

                // take different actions based on the command
                switch (command) {
                    case "add":
                        peers.add(packet.getAddress());
                        response(true, "peer added");
                        break;
                    case "remove":
                        peers.remove(packet.getAddress());
                        response(true, "peer removed");
                        break;
                    case "message":
                        InetAddress addr = packet.getAddress();
                        // only accept messages from peers we know
                        if (peers.contains(addr)) {
                            try {
                                String message = jsonData.getString("message");
                                addMessage(addr.getHostAddress(), message);
                                response(true, "message delivered");
                            } catch (JSONException e) {
                                e.printStackTrace();
                                response(false, "unable to parse message");
                            }
                        } else
                            response(false, "not in peer list");
                        break;
                    case "getPeers":
                        try {
                            JSONArray jsonPeers = new JSONArray(peers);
                            JSONObject jsonResponse = new JSONObject();
                            jsonResponse.put("peers", jsonPeers);
                            sendJSON(packet.getAddress(), packet.getPort(), jsonResponse);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            response(false, "unable to send peers");
                        }
                        break;
                    default:
                        response(false, "unknown command");
                }
            }

            // creates and sends a standard JSON response { "success": boolean, "message": String }
            void response(boolean success, String message) {
                try {
                    JSONObject responseJSON = new JSONObject();

                    responseJSON.put("success", success);
                    responseJSON.put("message", message);
                    sendJSON(packet.getAddress(), packet.getPort(), responseJSON);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // sends a UDP packet with a JSON object in it. Handles encoding and conversions
    DatagramSocket sendJSON(InetAddress addr, int port, JSONObject object) {
        try {
            String dataString = object.toString();
            System.out.printf("sendJSON: %s %d %s\n", addr.getHostAddress(), port, dataString);
            byte[] data = dataString.getBytes();
            DatagramSocket socket = new DatagramSocket();
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            socket.send(packet);
            return socket;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // decodes a JSONObject from a UDP packet. Handles encoding and conversions
    JSONObject packetToJSON(DatagramPacket packet) {
        try {
            // read the data and convert it to a string
            String dataString = new String(packet.getData(), 0, packet.getLength(), CHARSET);
            System.out.printf("packetToJSON: %s %d %s\n", packet.getAddress().getHostAddress(),
                    packet.getPort(), dataString);
            // convert the string to a JSON object
            return new JSONObject(dataString);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // reads a UDP packet with a JSON object in it over a socket
    JSONObject readJSON(DatagramSocket socket) {
        byte[] buffer = new byte[BUFFSIZE];
        DatagramPacket packet = new DatagramPacket(buffer, BUFFSIZE);

        try {
            socket.receive(packet);
            JSONObject response = packetToJSON(packet);
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // sends a JSON object over UDP and reads one back (same port)
    JSONObject sendAndRecieve(InetAddress addr, JSONObject object) {
        DatagramSocket socket;

        socket = sendJSON(addr, PORT, object);
        return readJSON(socket);
    }

    // adds a message to our message string and tells the UI thread to refresh the view
    void addMessage(String name, String message) {
        messages += String.format("%s: %s\n", name, message);
        activity.refreshView();
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
        return TextUtils.join(", ", sAddrs);
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
        return TextUtils.join(", ", host_addresses);
    }

    // replaces our peer list with one from another peer
    void getPeers(InetAddress ip) {
        try {
            JSONObject request = new JSONObject();
            request.put("command", "getPeers");
            JSONObject response = sendAndRecieve(ip, request);
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

    // send a request to an address that they add us to their peers list
    void requestAdd(InetAddress ip) {
        try {
            JSONObject request = new JSONObject();
            request.put("command", "add");
            sendAndRecieve(ip, request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // send out requests to all our peers that they add us to their peers list
    void requestAddAllPeers() {
        for (InetAddress addr: peers)
            requestAdd(addr);
    }

    // send a request to an address that they remove us from their peers list
    void requestRemove(InetAddress ip) {
        try {
            JSONObject request = new JSONObject();
            request.put("command", "remove");
            sendAndRecieve(ip, request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // send out requests to all our peers asking that they remove us
    void requestRemoveAllPeers() {
        for (InetAddress addr: peers)
            requestRemove(addr);
    }

    // sends a message to a peer
    void sendMessage(InetAddress ip, String message) {
        try {
            JSONObject request = new JSONObject();
            request.put("command", "message");
            request.put("message", message);
            sendAndRecieve(ip, request);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // sends a message to everyone in our peer list
    void sendMessageAllPeers(String message) {
        for (InetAddress addr: peers) {
            sendMessage(addr, message);
            addMessage("me", message); // add it to our view as well
        }
    }
}