package com.ordo.itsm.triage.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
public class TriageEngineConfig {

    @Bean
    public TriageEngine triageEngine(JsonMapper jsonMapper,
                                     @Value("${llm.provider:openai-compatible}") String provider,
                                     @Value("${llm.base-url:http://localhost:11434/v1}") String baseUrl,
                                     @Value("${llm.api-key:}") String apiKey,
                                     @Value("${llm.model:}") String model,
                                     @Value("${llm.response-format:json_schema}") String responseFormat,
                                     @Value("${llm.timeout-seconds:20}") int timeoutSeconds) {
        String key = provider == null ? "" : provider.trim().toLowerCase();
        return switch (key) {
            case "openai-compatible" -> {
                log.info("[Triage] OpenAI-compat 엔진 사용 (base={}, model={}, response_format={})",
                        baseUrl, model, responseFormat);
                yield new OpenAiCompatibleTriageEngine(
                        jsonMapper, baseUrl, apiKey, model, responseFormat, timeoutSeconds);
            }
            case "gemini" -> {
                if (apiKey == null || apiKey.isBlank()) {
                    log.warn("[Triage] provider=gemini 지만 API 키가 없음 → 규칙 기반 엔진 사용");
                    yield new KeywordTriageEngine(jsonMapper);
                }
                log.info("[Triage] Gemini 엔진 사용 (model={})", model);
                yield new GeminiTriageEngine(jsonMapper, apiKey, model, timeoutSeconds);
            }
            case "keyword" -> {
                log.info("[Triage] 규칙 기반(keyword) 엔진 사용");
                yield new KeywordTriageEngine(jsonMapper);
            }
            default -> {
                log.warn("[Triage] 알 수 없는 llm.provider='{}' → 규칙 기반 엔진 사용", provider);
                yield new KeywordTriageEngine(jsonMapper);
            }
        };
    }
}
