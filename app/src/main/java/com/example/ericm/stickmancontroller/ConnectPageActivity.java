package com.example.ericm.stickmancontroller;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class ConnectPageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect_page);
    }

    @Override
    protected void onDestroy() {
        Toast.makeText(this, "ConnectPageActivity::onDestroy", Toast.LENGTH_SHORT).show();
    }

    public void connectButtonHandler(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }
}
