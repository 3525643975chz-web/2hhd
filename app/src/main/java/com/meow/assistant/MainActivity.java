package com.meow.assistant;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

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

    public static class WebAppInterface {
        private Context mContext;
        
        public WebAppInterface(Context c) { 
            mContext = c; 
        }

        // 【修复】改用 Intent 查询，获取带有桌面图标的真实 App
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
                // 过滤掉重复的包名，并排除自己
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
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }

        @JavascriptInterface
        public void openBatterySettings() {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }

        // 【新增】跳转到应用详情页（自启动通常在这里面设置）
        @JavascriptInterface
        public void openAutoStartSettings() {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + mContext.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        }

        // 【新增】跳转到通知设置页
        @JavascriptInterface
        public void openNotificationSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, mContext.getPackageName());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception e) {
                // 兼容老版本，直接跳详情页
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + mContext.getPackageName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            }
        }

        // 为了兼容，保留一个空的方法，防止报错
        @JavascriptInterface
        public void openAppDetailsSettings() {
            openAutoStartSettings();
        }
    }
}
