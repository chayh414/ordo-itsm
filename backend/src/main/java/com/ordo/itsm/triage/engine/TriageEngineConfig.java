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
                                     @Value("${llm.provider:gemini}") String provider,
                                     @Value("${llm.api-key:}") String apiKey,
                                     @Value("${llm.model:gemini-2.5-flash-lite}") String model,
                                     @Value("${llm.timeout-seconds:20}") int timeoutSeconds) {
        if ("gemini".equalsIgnoreCase(provider) && !apiKey.isBlank()) {
            log.info("[Triage] Gemini 엔진 사용 (model={})", model);
            return new GeminiTriageEngine(jsonMapper, apiKey, model, timeoutSeconds);
        }
        log.warn("[Triage] LLM 키가 없거나 provider={} → 규칙 기반 엔진 사용", provider);
        return new KeywordTriageEngine(jsonMapper);
    }
}
