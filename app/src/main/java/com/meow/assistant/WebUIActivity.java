package com.meow.assistant;

import android.content.Intent;
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
            runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            });
        }

        @JavascriptInterface
        public void openBatterySettings() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            });
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            });
        }

        @JavascriptInterface
        public void openAutoStartSettings() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
        }

        @JavascriptInterface
        public void setBackgroundMode(boolean enabled) {
            // 保存无痕模式配置（对应喵喵助手底层的 SharedPreferences）
            getSharedPreferences("meow_prefs", MODE_PRIVATE).edit().putBoolean("bg_mode_hide", enabled).apply();
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                java.util.List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

                JSONArray jsonArray = new JSONArray();
                java.util.Set<String> addedPkgs = new java.util.HashSet<>();

                for (android.content.pm.ResolveInfo info : resolveInfos) {
                    String pkgName = info.activityInfo.packageName;
                    if (pkgName.equals(getPackageName())) continue; // 排除自己
                    if (addedPkgs.contains(pkgName)) continue;

                    android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(pkgName, 0);
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
            // 这里接收前端的 JSON，由于不知道喵喵助手底层具体字段，我们先存到本地
            // 后续如果发现替换不生效，需要在这里把 JSON 拆分，存进喵喵助手原生的 CatConfig 里
            getSharedPreferences("meow_prefs", MODE_PRIVATE).edit().putString("weai_config", json).apply();
            
            // 尝试触发底层刷新（如果原项目有这个方法）
            // MeowAccessibilityService.reloadConfig(); 
            
            runOnUiThread(() -> Toast.makeText(WebUIActivity.this, "规则已保存", Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String loadConfig() {
            return getSharedPreferences("meow_prefs", MODE_PRIVATE).getString("weai_config", "");
        }
    }
}
