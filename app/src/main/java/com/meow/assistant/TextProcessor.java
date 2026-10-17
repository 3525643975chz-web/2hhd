package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        
        String result = original;
        
        // 1. 替换规则
        for (CatConfig.Rule rule : config.rules) {
            if (result.contains(rule.from)) {
                result = result.replace(rule.from, rule.to);
            }
        }
        
        // 2. 追加后缀
        if (config.appendEnabled && config.suffix != null && !config.suffix.isEmpty()) {
            if (!result.endsWith(config.suffix)) {
                result = result + config.suffix;
            }
        }
        return result;
    }
}
