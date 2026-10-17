package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        String result = original;
        
        String suffix = config.suffix;
        boolean append = config.appendEnabled;
        
        // 1. 如果开启了后缀，先把末尾已有的后缀拔掉
        if (append && suffix != null && !suffix.isEmpty()) {
            if (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }

        // 2. 执行规则替换
        for (CatConfig.Rule rule : config.rules) {
            if (result.contains(rule.from)) {
                result = result.replace(rule.from, rule.to);
            }
        }
        
        // 3. 替换完成后，重新把后缀加在最末尾
        if (append && suffix != null && !suffix.isEmpty()) {
            if (!result.isEmpty()) {
                result = result + suffix;
            }
        }
        
        return result;
    }
}
