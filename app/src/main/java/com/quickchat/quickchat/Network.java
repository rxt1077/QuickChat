package com.quickchat.quickchat;

import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Network {
    public static String getIPAddresses() {
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
}
