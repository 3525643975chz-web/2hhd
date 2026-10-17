package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        String result = original;
        
        for (CatConfig.Rule rule : config.rules) {
            if (result.contains(rule.from)) {
                result = result.replace(rule.from, rule.to);
            }
        }
        
        if (config.appendEnabled && config.suffix != null && !config.suffix.isEmpty()) {
            if (!result.endsWith(config.suffix)) {
                result = result + config.suffix;
            }
        }
        return result;
    }
}
