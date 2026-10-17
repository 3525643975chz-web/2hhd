package com.meow.assistant;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebUIActivity extends AppCompatActivity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 强制透明防闪屏
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // 注入桥接接口（保持和 HTML 里的名字一致）
        webView.addJavascriptInterface(new WebBridge(), "AndroidBridge");

        // 防止网页跳转到外部浏览器
        webView.setWebViewClient(new WebViewClient());
        
        // 加载 assets 里的前端页面
        webView.loadUrl("file:///android_asset/index.html");
        
        setContentView(webView);

        // 透明状态栏和导航栏
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    // 核心通信桥梁
    private class WebBridge {
        @JavascriptInterface
        public void openAccessibilitySettings() {
            WebUIActivity.this.runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                WebUIActivity.this.startActivity(intent);
            });
        }

        @JavascriptInterface
        public void openBatterySettings() {
            WebUIActivity.this.runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                WebUIActivity.this.startActivity(intent);
            });
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            WebUIActivity.this.runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, WebUIActivity.this.getPackageName());
                WebUIActivity.this.startActivity(intent);
            });
        }

        @JavascriptInterface
        public void openAutoStartSettings() {
            WebUIActivity.this.runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + WebUIActivity.this.getPackageName()));
                WebUIActivity.this.startActivity(intent);
            });
        }

        @JavascriptInterface
        public void setBackgroundMode(boolean enabled) {
            WebUIActivity.this.getSharedPreferences("meow_prefs", Context.MODE_PRIVATE).edit().putBoolean("bg_mode_hide", enabled).apply();
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                PackageManager pm = WebUIActivity.this.getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

                JSONArray jsonArray = new JSONArray();
                Set<String> addedPkgs = new HashSet<>();

                for (ResolveInfo info : resolveInfos) {
                    String pkgName = info.activityInfo.packageName;
                    if (pkgName.equals(WebUIActivity.this.getPackageName())) continue; // 排除自己
                    if (addedPkgs.contains(pkgName)) continue;

                    ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0);
                    JSONObject obj = new JSONObject();
                    obj.put("name", pm.getApplicationLabel(appInfo).toString());
                    obj.put("pkg", pkgName);
                    jsonArray.put(obj);
                    addedPkgs.add(pkgName);
                }
                return jsonArray.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "[]";
            }
        }

        @JavascriptInterface
        public void saveConfig(String json) {
            WebUIActivity.this.getSharedPreferences("meow_prefs", Context.MODE_PRIVATE).edit().putString("weai_config", json).apply();
            
            WebUIActivity.this.runOnUiThread(() -> Toast.makeText(WebUIActivity.this, "规则已保存", Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String loadConfig() {
            return WebUIActivity.this.getSharedPreferences("meow_prefs", Context.MODE_PRIVATE).getString("weai_config", "");
        }
    }
}
