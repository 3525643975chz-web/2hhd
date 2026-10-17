package com.meow.assistant;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. 创建通知渠道
        createNotificationChannel();
        // 2. 申请通知权限 (Android 13+)
        requestNotificationPermission();

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); 
        settings.setDomStorageEnabled(true); 
        settings.setAllowFileAccess(true);   
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        webView.addJavascriptInterface(new WebAppInterface(MainActivity.this), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    // 通知渠道
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "WeaiText 运行通知";
            String description = "保持魔法引擎在后台运行";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel("weaitext_channel", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // 申请权限
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    public static class WebAppInterface {
        private Context mContext;
        
        public WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public String getInstalledApps() {
            // (保持原来的获取应用逻辑不变...)
            return "[]"; // 替换为你原来的代码
        }

        @JavascriptInterface
        public void saveConfig(String jsonString) {
            mContext.getSharedPreferences("weai_config", Context.MODE_PRIVATE)
                    .edit().putString("config_json", jsonString).apply();
            
            // 如果保存配置了，顺便发个常驻通知保活
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, "weaitext_channel")
                    .setContentTitle("WeaiText 魔法引擎运转中")
                    .setContentText("喵喵正在后台为您服务 🐾")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true);
            try {
                NotificationManagerCompat.from(mContext).notify(1, builder.build());
            } catch (SecurityException e) {}

            Intent intent = new Intent(mContext, MeowAccessibilityService.class);
            intent.setAction("RELOAD_CONFIG");
            mContext.startService(intent);
        }

        // ... (保留你的其他方法：loadConfig, openAccessibilitySettings 等)
    }
}
