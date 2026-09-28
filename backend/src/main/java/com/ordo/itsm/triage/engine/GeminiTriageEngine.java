package com.ordo.itsm.triage.engine;

import com.ordo.itsm.triage.TriageResult;
import com.ordo.itsm.triage.TriageVocabulary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Google Gemini API (generateContent + JSON 스키마 강제) */
@Slf4j
public class GeminiTriageEngine implements TriageEngine {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final int MAX_ATTEMPTS = 2;   // JSON 형식 오류 시 1회 재시도

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String apiKey;
    private final String model;

    public GeminiTriageEngine(JsonMapper jsonMapper, String apiKey, String model, int timeoutSeconds) {
        this.jsonMapper = jsonMapper;
        this.apiKey = apiKey;
        this.model = model;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public TriageOutcome analyze(String maskedRequest) {
        String requestBody = jsonMapper.writeValueAsString(buildRequest(maskedRequest));

        TriageEngineException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String responseBody = call(requestBody);
            String text = extractText(responseBody);
            try {
                TriageResult result = jsonMapper.readValue(text, TriageResult.class);
                return new TriageOutcome(text, result);
            } catch (RuntimeException e) {
                log.warn("[Triage] Gemini JSON 파싱 실패 (시도 {}/{})", attempt, MAX_ATTEMPTS);
                lastError = new TriageEngineException(TriageEngineException.Kind.INVALID_JSON,
                        "AI 응답이 스키마와 맞지 않습니다.", text, e);
            }
        }
        throw lastError;
    }

    private String call(String requestBody) {
        try {
            return restClient.post()
                    .uri(BASE_URL + "/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 키 값은 로그에 남기지 않는다
            log.warn("[Triage] Gemini 호출 실패: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new TriageEngineException(TriageEngineException.Kind.FAILED,
                    "AI 호출 실패 (HTTP " + e.getStatusCode().value() + ")", e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            log.warn("[Triage] Gemini 연결 실패/시간 초과: {}", e.getMessage());
            throw new TriageEngineException(TriageEngineException.Kind.FAILED,
                    "AI 서버 연결 실패 또는 시간 초과", null, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(String responseBody) {
        try {
            Map<String, Object> root = jsonMapper.readValue(responseBody, Map.class);
            List<Object> candidates = (List<Object>) root.get("candidates");
            Map<String, Object> content = (Map<String, Object>) ((Map<String, Object>) candidates.get(0)).get("content");
            List<Object> parts = (List<Object>) content.get("parts");
            return (String) ((Map<String, Object>) parts.get(0)).get("text");
        } catch (RuntimeException e) {
            throw new TriageEngineException(TriageEngineException.Kind.INVALID_JSON,
                    "AI 응답에서 결과를 찾을 수 없습니다.", responseBody, e);
        }
    }

    private Map<String, Object> buildRequest(String maskedRequest) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", TriagePrompt.SYSTEM))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", TriagePrompt.wrapUserRequest(maskedRequest)))
        )));
        body.put("generationConfig", Map.of(
                "temperature", 0,
                "responseMimeType", "application/json",
                "responseSchema", responseSchema()
        ));
        return body;
    }

    /** Gemini가 반드시 이 모양의 JSON만 내도록 강제 */
    private static Map<String, Object> responseSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("requestType", enumOf(List.of("INCIDENT", "SERVICE_REQUEST", "CHANGE", "INQUIRY")));
        props.put("category", enumOf(TriageVocabulary.CATEGORIES));
        props.put("environment", nullable(enumOf(List.of("PRODUCTION", "STAGING", "DEV"))));
        props.put("requestedTime", nullable(Map.of("type", "STRING")));
        props.put("recommendedPriority", enumOf(List.of("P1", "P2", "P3", "P4")));
        props.put("recommendedTeam", enumOf(TriageVocabulary.TEAMS));
        props.put("affectedService", nullable(Map.of("type", "STRING")));
        props.put("testPlanProvided", nullable(Map.of("type", "BOOLEAN")));
        props.put("rollbackPlanProvided", nullable(Map.of("type", "BOOLEAN")));
        props.put("missingFields", Map.of("type", "ARRAY", "items", enumOf(TriageVocabulary.fieldKeys())));
        props.put("riskFactors", Map.of("type", "ARRAY", "items", enumOf(TriageVocabulary.RISK_FACTORS)));
        props.put("evidence", Map.of("type", "ARRAY", "items", Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "factor", enumOf(TriageVocabulary.RISK_FACTORS),
                        "quote", Map.of("type", "STRING")
                ),
                "required", List.of("factor", "quote")
        )));
        props.put("clarifyingQuestions", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
        props.put("confidence", Map.of("type", "NUMBER"));

        return Map.of(
                "type", "OBJECT",
                "properties", props,
                "required", List.of("requestType", "category", "recommendedPriority", "recommendedTeam",
                        "missingFields", "riskFactors", "evidence", "clarifyingQuestions", "confidence")
        );
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "STRING", "enum", values);
    }

    private static Map<String, Object> nullable(Map<String, Object> schema) {
        Map<String, Object> copy = new LinkedHashMap<>(schema);
        copy.put("nullable", true);
        return copy;
    }
}
