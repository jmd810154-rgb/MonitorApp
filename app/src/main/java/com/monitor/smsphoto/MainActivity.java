package com.monitor.smsphoto;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView statusText = findViewById(R.id.statusText);
        Button startBtn = findViewById(R.id.startBtn);
        Button stopBtn = findViewById(R.id.stopBtn);

        startBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, MonitorService.class);
            startService(intent);
            statusText.setText("✅ মনিটর চালু হয়েছে");
        });

        stopBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, MonitorService.class);
            stopService(intent);
            statusText.setText("❌ মনিটর বন্ধ হয়েছে");
        });

        // পারমিশন চাওয়া
        ActivityCompat.requestPermissions(this, new String[]{
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.INTERNET
        }, 100);
    }
}
