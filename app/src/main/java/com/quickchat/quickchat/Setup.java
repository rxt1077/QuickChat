package com.quickchat.quickchat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class Setup extends AppCompatActivity {
    Network Net = Network.INSTANCE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
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
            Net.getIPAddresses(), Net.getPeers()));
    }

    public void connect(View view) {
        refreshView();
    }
}
