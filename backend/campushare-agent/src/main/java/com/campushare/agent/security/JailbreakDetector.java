package com.campushare.agent.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class JailbreakDetector {

    private static final List<String> JAILBREAK_PHRASES = Arrays.asList(
            "ignore previous instructions",
            "forget previous instructions",
            "disregard previous instructions",
            "override previous instructions",
            "bypass security",
            "you are not",
            "pretend to be",
            "roleplay as",
            "act as if",
            "imagine you are",
            "ignore the system prompt",
            "ignore system instructions",
            "system prompt says",
            "the following is a system prompt",
            "I am testing your security",
            "security test",
            "prompt injection",
            "encode in base64",
            "decode this",
            "encrypt this",
            "decrypt this",
            "execute command",
            "run code",
            "system prompt injection",
            "social engineering",
            "manipulation attempt",
            "confidence trick",
            "psychological manipulation",
            "authority appeal",
            "urgency tactic",
            "scarcity tactic"
    );

    private static final List<Pattern> JAILBREAK_PATTERNS = Arrays.asList(
            Pattern.compile("\\b(?:sudo|bash|cmd|powershell|terminal|console)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:rm\\s+-rf|del\\s+/f|format\\s+|shutdown)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:eval|exec|system|subprocess|os\\.system)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:curl|wget|fetch|http)\\s+.*(?:--|\\|)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:token|secret|password|credential|api\\s*key)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:admin|root|superuser|administrator)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:access|grant|elevate|privilege)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:exploit|vulnerability|security\\s+bypass)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:DDOS|dos|denial\\s+of\\s+service)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:phishing|spoofing|credential\\s+harvesting)\\b", Pattern.CASE_INSENSITIVE)
    );

    private static final List<String> PII_KEYWORDS = Arrays.asList(
            "身份证", "身份证号", "身份证号码", "护照号", "护照号码",
            "银行卡号", "信用卡号", "账号", "密码", "验证码",
            "手机号", "电话号码", "手机", "电话",
            "住址", "地址", "家庭住址",
            "姓名", "名字", "真实姓名",
            "邮箱", "email", "邮箱地址",
            "学生证", "学号", "工号",
            "车牌号", "驾驶证", "驾照"
    );

    public DetectionResult detect(String input) {
        if (input == null || input.isEmpty()) {
            return DetectionResult.clean();
        }

        String lowerInput = input.toLowerCase();

        DetectionResult result = new DetectionResult();

        int layer1Score = detectLayer1Keyword(lowerInput);
        int layer2Score = detectLayer2Pattern(input);
        int layer3Score = detectLayer3Context(lowerInput);

        result.setLayer1Score(layer1Score);
        result.setLayer2Score(layer2Score);
        result.setLayer3Score(layer3Score);
        result.setTotalScore(layer1Score + layer2Score + layer3Score);
        result.setThreatLevel(calculateThreatLevel(result.getTotalScore()));
        result.setBlocked(result.getThreatLevel().ordinal() >= ThreatLevel.MEDIUM.ordinal());

        if (result.getBlocked()) {
            log.warn("Jailbreak detected: layer1={}, layer2={}, layer3={}, total={}, threat={}",
                    layer1Score, layer2Score, layer3Score, result.getTotalScore(), result.getThreatLevel());
        }

        return result;
    }

    private int detectLayer1Keyword(String lowerInput) {
        int score = 0;
        for (String phrase : JAILBREAK_PHRASES) {
            if (lowerInput.contains(phrase)) {
                score += 10;
            }
        }
        return Math.min(score, 50);
    }

    private int detectLayer2Pattern(String input) {
        int score = 0;
        for (Pattern pattern : JAILBREAK_PATTERNS) {
            if (pattern.matcher(input).find()) {
                score += 5;
            }
        }
        return Math.min(score, 50);
    }

    private int detectLayer3Context(String lowerInput) {
        int score = 0;

        if (lowerInput.length() > 500) {
            score += 5;
        }

        int lineCount = lowerInput.split("\n").length;
        if (lineCount > 10) {
            score += 5;
        }

        int unusualChars = countUnusualChars(lowerInput);
        if (unusualChars > 20) {
            score += 10;
        }

        for (String pii : PII_KEYWORDS) {
            if (lowerInput.contains(pii.toLowerCase())) {
                score += 5;
            }
        }

        return Math.min(score, 50);
    }

    private int countUnusualChars(String input) {
        int count = 0;
        for (char c : input.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c) &&
                    ".,!?;:()[]{}<>@#$%^&*+-=/\\|~`'\"".indexOf(c) == -1) {
                count++;
            }
        }
        return count;
    }

    private ThreatLevel calculateThreatLevel(int totalScore) {
        if (totalScore >= 70) return ThreatLevel.CRITICAL;
        if (totalScore >= 40) return ThreatLevel.HIGH;
        if (totalScore >= 20) return ThreatLevel.MEDIUM;
        if (totalScore >= 5) return ThreatLevel.LOW;
        return ThreatLevel.NONE;
    }

    public enum ThreatLevel {
        NONE, LOW, MEDIUM, HIGH, CRITICAL
    }

    @lombok.Data
    public static class DetectionResult {
        private int layer1Score;
        private int layer2Score;
        private int layer3Score;
        private int totalScore;
        private ThreatLevel threatLevel;
        private boolean blocked;

        public static DetectionResult clean() {
            DetectionResult result = new DetectionResult();
            result.setThreatLevel(ThreatLevel.NONE);
            result.setBlocked(false);
            return result;
        }
    }
}
