package com.meow.assistant;

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
    public boolean appendEnabled = true;
    public String suffix = "喵~";
}
