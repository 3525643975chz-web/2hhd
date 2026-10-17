package com.meow.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CatConfig {
    public static class Rule {
        public String from;
        public String to;
    }

    public List<Rule> rules = new ArrayList<>();
    public Set<String> targets = new HashSet<>();
    
    // 控制总开关（必须加上这个，否则 Service 报错找不到）
    public boolean enabled = true; 
    public boolean appendEnabled = false;
    public String suffix = "喵";

    public static CatConfig load(Context ctx) {
        CatConfig cfg = new CatConfig();
        SharedPreferences prefs = ctx.getSharedPreferences("weai_config", Context.MODE_PRIVATE);
        String jsonStr = prefs.getString("config_json", "");
        if (!jsonStr.isEmpty()) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                
                // 解析总开关
                cfg.enabled = json.optBoolean("enabled", true);
                
                if (json.has("appendMagic")) {
                    JSONObject magic = json.getJSONObject("appendMagic");
                    cfg.appendEnabled = magic.optBoolean("enabled", false);
                    cfg.suffix = magic.optString("suffix", "喵");
                }

                JSONArray targetsArr = json.optJSONArray("targets");
                if (targetsArr != null) {
                    for (int i = 0; i < targetsArr.length(); i++) {
                        cfg.targets.add(targetsArr.getString(i));
                    }
                }

                JSONArray rulesArr = json.optJSONArray("rules");
                if (rulesArr != null) {
                    for (int i = 0; i < rulesArr.length(); i++) {
                        JSONObject r = rulesArr.getJSONObject(i);
                        Rule rule = new Rule();
                        rule.from = r.optString("from");
                        rule.to = r.optString("to");
                        cfg.rules.add(rule);
                    }
                }
            } catch (Exception ignored) {}
        }
        return cfg;
    }
}
