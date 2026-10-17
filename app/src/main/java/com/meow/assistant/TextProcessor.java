package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        String result = original;
        
        String suffix = config.suffix;
        boolean append = config.appendEnabled;
        
        // 1. 如果开启了后缀，先把原文本里所有的后缀（哪怕在中间）全部拔掉，只留纯主体
        if (append && suffix != null && !suffix.isEmpty()) {
            int lastIndex = result.lastIndexOf(suffix);
            if (lastIndex != -1) {
                // 剔除掉发现的后缀
                result = result.substring(0, lastIndex) + result.substring(lastIndex + suffix.length());
            }
        }

        // 2. 对纯主体文本执行词替换
        for (CatConfig.Rule rule : config.rules) {
            if (result.contains(rule.from)) {
                result = result.replace(rule.from, rule.to);
            }
        }
        
        // 3. 替换完成后，重新把后缀加在最末尾
        if (append && suffix != null && !suffix.isEmpty()) {
            result = result + suffix;
        }
        
        return result;
    }
}
