package com.meow.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
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
import android.view.WindowManager;
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
    private boolean isIncognitoMode = false; // 记录无痕模式状态

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ================= 1. 全屏沉浸式适配 =================
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN 
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR 
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

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

        webView.addJavascriptInterface(new WebAppInterface(MainActivity.this), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    // ================= 2. 核心：动态隐藏多任务卡片 =================
    private void updateTaskState() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            for (ActivityManager.AppTask task : am.getAppTasks()) {
                // 开启无痕 -> 从多任务移除；关闭无痕 -> 恢复显示
                task.setExcludeFromRecents(isIncognitoMode);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 当用户按 Home 键或切换到其他 App 时，如果开了无痕，立刻把自己从多任务里踢出去
        if (isIncognitoMode) {
            updateTaskState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 保证再次回到 App 时状态正确
        updateTaskState(); 
    }
    // ================================================================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("weaitext_channel", "WeaiText 运行通知", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
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

        // 接收前端的无痕模式开关指令
        @JavascriptInterface
        public void setBackgroundMode(boolean enabled) {
            if (mContext instanceof MainActivity) {
                ((MainActivity) mContext).runOnUiThread(() -> {
                    ((MainActivity) mContext).isIncognitoMode = enabled;
                    ((MainActivity) mContext).updateTaskState(); // 实时触发隐藏/恢复
                });
            }
        }

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
            
            try {
                org.json.JSONObject json = new org.json.JSONObject(jsonString);
                boolean isModuleActive = json.optBoolean("enabled", false);
                Intent serviceIntent = new Intent(mContext, KeepAliveService.class);
                
                if (isModuleActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mContext.startForegroundService(serviceIntent);
                    } else {
                        mContext.startService(serviceIntent);
                    }
                } else {
                    mContext.stopService(serviceIntent);
                }
            } catch (Exception e) { e.printStackTrace(); }

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
