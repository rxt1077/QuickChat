package com.quickchat.quickchat;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Setup extends AppCompatActivity {
    Network Net = Network.INSTANCE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        // this allows us to perform network operations in this thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        refreshView();
    }

    void refreshView() {
        TextView setupView = findViewById(R.id.setupView);
        setupView.setText(String.format(
            "Your IP(s): %s\n" +
            "Your peers: %s\n\n" +
            "Directions: To join a chat, put in the IP address of one of the peers and tap " +
            "CONNECT. This will replace your current peer list with a new group of peers. To " +
            "start a new chat have someone connect to your IP address.",
            Net.ipToString(), Net.peersToString()));
    }

    // called when the user taps CONNECT
    public void connect(View view) {
        EditText ipText = findViewById(R.id.ipText);
        try {
            // convert the IP address they entered
            InetAddress ip = InetAddress.getByName(ipText.getText().toString());
            // update our peers list
            Net.getPeers(ip);
            // request that all our peers add us to their peers list
            Net.requestAddAllPeers();
            // refresh the display
            refreshView();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
