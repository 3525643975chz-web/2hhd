package com.meow.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new WebBridge(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        
        webView.loadUrl("file:///android_asset/index.html");
        
        setContentView(webView);

        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    private class WebBridge {
        private Activity mActivity;

        public WebBridge(Activity activity) {
            this.mActivity = activity;
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    mActivity.startActivity(intent);
                }
            });
        }

        @JavascriptInterface
        public String getInstalledApps() {
            try {
                PackageManager pm = mActivity.getPackageManager();
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                // 核心：查询所有带有启动图标的App
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

                JSONArray jsonArray = new JSONArray();
                Set<String> addedPkgs = new HashSet<>();

                for (ResolveInfo info : resolveInfos) {
                    String pkgName = info.activityInfo.packageName;
                    if (pkgName.equals(mActivity.getPackageName())) continue; 
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
                return "[]";
            }
        }
    }
}
