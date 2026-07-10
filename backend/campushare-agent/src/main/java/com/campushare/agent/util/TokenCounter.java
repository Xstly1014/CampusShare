package com.campushare.agent.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.IntArrayList;
import com.knuddels.jtokkit.api.ModelType;

public final class TokenCounter {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncodingForModel(ModelType.GPT_3_5_TURBO);

    private TokenCounter() {
    }

    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

    public static int countTokens(Iterable<String> texts) {
        int total = 0;
        for (String text : texts) {
            total += countTokens(text);
        }
        return total;
    }

    public static String truncateToTokens(String text, int maxTokens) {
        if (text == null || text.isEmpty() || maxTokens <= 0) {
            return text;
        }
        if (countTokens(text) <= maxTokens) {
            return text;
        }
        var tokens = ENCODING.encode(text);
        if (tokens.size() <= maxTokens) {
            return text;
        }
        IntArrayList truncated = new IntArrayList(maxTokens);
        for (int i = 0; i < maxTokens && i < tokens.size(); i++) {
            truncated.add(tokens.get(i));
        }
        return ENCODING.decode(truncated);
    }
}
