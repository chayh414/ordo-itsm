package com.ordo.itsm.triage;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/** 외부 LLM 전송 전 비밀번호·키·주민번호·카드번호를 가린다 (기획안 13.2) */
@Component
public class SensitiveDataMasker {

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            // 비밀번호: xxx / password=xxx / pw: xxx
            new Rule(Pattern.compile("(?i)(비밀번호|패스워드|password|passwd|pwd|pw)\\s*[:=은는]?\\s*\\S+"), "$1: [MASKED]"),
            // API 키·토큰류
            new Rule(Pattern.compile("\\b(AIza[0-9A-Za-z_\\-]{20,}|sk-[0-9A-Za-z_\\-]{16,}|AKIA[0-9A-Z]{16}|ghp_[0-9A-Za-z]{20,})\\b"), "[MASKED_KEY]"),
            // 주민등록번호
            new Rule(Pattern.compile("\\b\\d{6}-?[1-4]\\d{6}\\b"), "[MASKED_RRN]"),
            // 카드번호
            new Rule(Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), "[MASKED_CARD]")
    );

    public String mask(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }
}
