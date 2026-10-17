package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        
        // 1. 过滤占位符，防止被当成文字替换
        if (original.equals("发消息...") || original.equals("发消息…") || original.equals("发送消息...") || original.equals("说点什么...")) {
            return original; 
        }
        
        String result = original;
        String suffix = config.suffix;
        boolean append = config.appendEnabled;
        
        // 2. 精准剥离末尾的旧后缀（绝不误杀中间的字）
        if (append && suffix != null && !suffix.isEmpty()) {
            while (result.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
            }
        }

        // 3. 执行规则替换
        for (CatConfig.Rule rule : config.rules) {
            if (result.contains(rule.from)) {
                result = result.replace(rule.from, rule.to);
            }
        }
        
        // 4. 重新加上后缀
        if (append && suffix != null && !suffix.isEmpty()) {
            if (!result.isEmpty()) {
                result = result + suffix;
            }
        }
        
        return result;
    }
}
