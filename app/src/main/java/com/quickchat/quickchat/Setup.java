package com.quickchat.quickchat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

public class Setup extends AppCompatActivity {
    Network Net = Network.INSTANCE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        TextView setupView = findViewById(R.id.setupView);
        setupView.setText(String.format("Your IP(s): %s", Net.getIPAddresses()));
    }
}
