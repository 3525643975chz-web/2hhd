package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        String result = original;
        
        String suffix = config.suffix;
        boolean append = config.appendEnabled;
        
        // 拔掉尾巴上的后缀
        if (append && suffix != null && !suffix.isEmpty()) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }

        // 执行替换
        for (CatConfig.Rule rule : config.rules) {
            if (result.contains(rule.from)) {
                result = result.replace(rule.from, rule.to);
            }
        }
        
        // 重新加后缀
        if (append && suffix != null && !suffix.isEmpty()) {
            if (!result.isEmpty()) {
                result = result + suffix;
            }
        }
        
        return result;
    }
}
