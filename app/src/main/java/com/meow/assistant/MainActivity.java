package com.meow.assistant;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 创建通知渠道与权限
        createNotificationChannel();
        requestNotificationPermission();

        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true); 
        settings.setDomStorageEnabled(true); 
        settings.setAllowFileAccess(true);   
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 注入接口
        webView.addJavascriptInterface(new WebAppInterface(MainActivity.this), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("weaitext_channel", "WeaiText 运行通知", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

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

        // 暴力抓取所有应用
        @JavascriptInterface
        public String getInstalledApps() {
            JSONArray jsonArray = new JSONArray();
            PackageManager pm = mContext.getPackageManager();
            
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            Set<String> addedPackages = new HashSet<>();

            for (ResolveInfo ri : resolveInfos) {
                String pkg = ri.activityInfo.packageName;
                if (!addedPackages.contains(pkg) && !pkg.equals(mContext.getPackageName())) {
                    addedPackages.add(pkg);
                    try {
                        JSONObject app = new JSONObject();
                        app.put("name", ri.loadLabel(pm).toString());
                        app.put("pkg", pkg);
                        jsonArray.put(app);
                    } catch (Exception e) { }
                }
            }
            return jsonArray.toString();
        }

        @JavascriptInterface
        public void saveConfig(String jsonString) {
            mContext.getSharedPreferences("weai_config", Context.MODE_PRIVATE)
                    .edit().putString("config_json", jsonString).apply();
            Intent intent = new Intent(mContext, MeowAccessibilityService.class);
            intent.setAction("RELOAD_CONFIG");
            mContext.startService(intent);
        }

        @JavascriptInterface
        public String loadConfig() {
            return mContext.getSharedPreferences("weai_config", Context.MODE_PRIVATE)
                    .getString("config_json", "");
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception e) { }
        }

        @JavascriptInterface
        public void openBatterySettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception e) {
                openAppDetailsSettings();
            }
        }

        @JavascriptInterface
        public void openAutoStartSettings() {
            openAppDetailsSettings();
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, mContext.getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception e) {
                openAppDetailsSettings();
            }
        }
        
        private void openAppDetailsSettings() {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + mContext.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }
    }
}
