package com.quickchat.quickchat;

import android.content.Context;
import android.content.Intent;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    Network Net = Network.INSTANCE;
    String chatMessages = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // this allows us to perform network operations in this thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        Net.activity = this;
        refreshView();
    }

    public void refreshView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView chatView = findViewById(R.id.chatView);
                chatView.setText(Net.messages);
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
        EditText msgText = findViewById(R.id.msgText);
        Net.sendMessageAllPeers(msgText.getText().toString());
        msgText.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(msgText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }
}
