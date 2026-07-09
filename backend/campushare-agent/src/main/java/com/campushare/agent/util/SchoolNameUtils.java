package com.campushare.agent.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SchoolNameUtils {

    private SchoolNameUtils() {
    }

    private static final Map<String, String> ID_TO_NAME;
    private static final Map<String, String> ALIAS_TO_NAME;

    private static final Pattern SCHOOL_PATTERN;

    static {
        Map<String, String> idMap = new HashMap<>();
        idMap.put("1", "北京大学");
        idMap.put("2", "清华大学");
        idMap.put("3", "复旦大学");
        idMap.put("4", "浙江大学");
        idMap.put("5", "上海交通大学");
        idMap.put("6", "南京大学");
        idMap.put("7", "武汉大学");
        idMap.put("8", "中国人民大学");
        idMap.put("9", "中山大学");
        idMap.put("10", "厦门大学");
        idMap.put("11", "哈尔滨工业大学");
        idMap.put("12", "深圳大学");
        ID_TO_NAME = Collections.unmodifiableMap(idMap);

        Map<String, String> aliasMap = new HashMap<>();
        aliasMap.put("北大", "北京大学");
        aliasMap.put("清华", "清华大学");
        aliasMap.put("复旦", "复旦大学");
        aliasMap.put("浙大", "浙江大学");
        aliasMap.put("上交", "上海交通大学");
        aliasMap.put("上海交大", "上海交通大学");
        aliasMap.put("南大", "南京大学");
        aliasMap.put("武大", "武汉大学");
        aliasMap.put("人大", "中国人民大学");
        aliasMap.put("中山", "中山大学");
        aliasMap.put("厦大", "厦门大学");
        aliasMap.put("哈工大", "哈尔滨工业大学");
        aliasMap.put("深大", "深圳大学");
        for (String name : idMap.values()) {
            aliasMap.put(name, name);
        }
        ALIAS_TO_NAME = Collections.unmodifiableMap(aliasMap);

        StringBuilder regex = new StringBuilder();
        for (String alias : ALIAS_TO_NAME.keySet()) {
            if (regex.length() > 0) regex.append("|");
            regex.append(Pattern.quote(alias));
        }
        SCHOOL_PATTERN = Pattern.compile("(" + regex + ")");
    }

    public static String getNameById(String schoolId) {
        if (schoolId == null) return null;
        return ID_TO_NAME.get(schoolId);
    }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return null;
        String trimmed = input.trim();
        if (ID_TO_NAME.containsKey(trimmed)) {
            return ID_TO_NAME.get(trimmed);
        }
        return ALIAS_TO_NAME.get(trimmed);
    }

    public static String extractFromQuery(String query) {
        if (query == null || query.isBlank()) return null;
        Matcher m = SCHOOL_PATTERN.matcher(query);
        if (m.find()) {
            String matched = m.group(1);
            return ALIAS_TO_NAME.getOrDefault(matched, matched);
        }
        return null;
    }
}
