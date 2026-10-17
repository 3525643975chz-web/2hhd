package com.meow.assistant;

public class TextProcessor {
    public static String process(String original, CatConfig config) {
        if (original == null || original.isEmpty() || config == null) return original;
        
        // 1. 拦截常见的输入框占位符（如豆包、QQ的默认提示）
        if (original.equals("发消息...") || original.equals("发消息…") || original.equals("发送消息...") || original.equals("说点什么...")) {
            return original; 
        }
        
        String result = original;
        String suffix = config.suffix;
        boolean append = config.appendEnabled;
        
        // 2. 如果开启了后缀，精准拔除文本【末尾】的旧后缀（防止把用户自己打的字删了）
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
        
        // 4. 替换完成后，重新把后缀加在最末尾
        if (append && suffix != null && !suffix.isEmpty()) {
            if (!result.isEmpty()) {
                result = result + suffix;
            }
        }
        
        return result;
    }
}
