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
    public boolean appendEnabled = false; // 默认关闭
    public String suffix = "喵";

    public static CatConfig load(Context ctx) {
        CatConfig cfg = new CatConfig();
        SharedPreferences prefs = ctx.getSharedPreferences("weai_config", Context.MODE_PRIVATE);
        String jsonStr = prefs.getString("config_json", "");
        if (!jsonStr.isEmpty()) {
            try {
                JSONObject json = new JSONObject(jsonStr);
                
                // 兼容前端传来的结构
                if (json.has("appendMagic")) {
                    JSONObject magic = json.getJSONObject("appendMagic");
                    cfg.appendEnabled = magic.optBoolean("enabled", false);
                    cfg.suffix = magic.optString("suffix", "喵");
                } else {
                    // 兼容旧版
                    cfg.appendEnabled = json.optBoolean("appendEnabled", false);
                    cfg.suffix = json.optString("suffix", "喵");
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
