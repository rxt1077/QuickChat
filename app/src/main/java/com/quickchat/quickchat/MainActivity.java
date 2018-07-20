package com.quickchat.quickchat;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    Network Net = Network.INSTANCE;
    String chatMessages = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Net.activity = this;
    }

    public void addMessage(String msg) {
        chatMessages += String.format("%s\n", msg);
        refreshView();
    }
    public void refreshView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView chatView = findViewById(R.id.chatView);
                chatView.setText(chatMessages);
            }
        });
    }
    // Called when the user taps the setup button
    public void setup(View view) {
        Intent intent = new Intent(this, Setup.class);
        startActivity(intent);
    }

    // Called when the user taps the send button
    public void send(View view) {

    }
}
