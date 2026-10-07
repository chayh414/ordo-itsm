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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환 /v1/chat/completions API 호출.
 * Ollama, vLLM, OpenAI 등 동일 인터페이스를 노출하는 추론 서버 모두 base-url만 바꿔 쓴다.
 *
 * JSON 강제는 response_format=json_schema를 1순위로 쓰고, 서버가 거부하면(HTTP 4xx) json_object로 1회 폴백한다.
 * 프롬프트에도 JSON 출력 지시가 들어있어 response_format=none에서도 JSON은 유도된다.
 */
@Slf4j
public class OpenAiCompatibleTriageEngine implements TriageEngine {

    private static final int MAX_PARSE_ATTEMPTS = 2;   // 파싱 실패 시 1회 재시도

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String responseFormat;

    public OpenAiCompatibleTriageEngine(JsonMapper jsonMapper, String baseUrl, String apiKey,
                                        String model, String responseFormat, int timeoutSeconds) {
        this.jsonMapper = jsonMapper;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.responseFormat = (responseFormat == null ? "json_schema" : responseFormat).toLowerCase();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String modelName() {
        return model + "@openai-compat";
    }

    @Override
    public TriageOutcome analyze(String maskedRequest) {
        FormatState state = new FormatState(responseFormat);
        TriageEngineException lastParseError = null;

        for (int attempt = 1; attempt <= MAX_PARSE_ATTEMPTS; attempt++) {
            String responseBody = callWithSchemaFallback(maskedRequest, state);
            String content = extractContent(responseBody);
            try {
                TriageResult result = jsonMapper.readValue(content, TriageResult.class);
                return new TriageOutcome(content, result);
            } catch (RuntimeException e) {
                log.warn("[Triage] OpenAI-compat JSON 파싱 실패 (시도 {}/{})", attempt, MAX_PARSE_ATTEMPTS);
                lastParseError = new TriageEngineException(TriageEngineException.Kind.INVALID_JSON,
                        "AI 응답이 스키마와 맞지 않습니다.", content, e);
            }
        }
        throw lastParseError;
    }

    /** json_schema 거부(HTTP 4xx)면 json_object로 1회 다시 호출. */
    private String callWithSchemaFallback(String maskedRequest, FormatState state) {
        try {
            return call(buildBody(maskedRequest, state.format));
        } catch (RestClientResponseException e) {
            if (state.canFallback() && isSchemaRejection(e)) {
                log.warn("[Triage] json_schema 거부(HTTP {}) → json_object 폴백", e.getStatusCode().value());
                state.fallbackToJsonObject();
                try {
                    return call(buildBody(maskedRequest, state.format));
                } catch (RestClientResponseException e2) {
                    throw mapHttpError(e2);
                } catch (ResourceAccessException e2) {
                    throw mapNetworkError(e2);
                }
            }
            throw mapHttpError(e);
        } catch (ResourceAccessException e) {
            throw mapNetworkError(e);
        }
    }

    private String call(Map<String, Object> body) {
        String json = jsonMapper.writeValueAsString(body);
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            spec = spec.header("Authorization", "Bearer " + apiKey);
        }
        return spec.body(json).retrieve().body(String.class);
    }

    /** 스키마 미지원으로 서버가 요청 자체를 거부할 때 흔한 상태 코드. */
    private static boolean isSchemaRejection(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return status == 400 || status == 404 || status == 422 || status == 501;
    }

    private TriageEngineException mapHttpError(RestClientResponseException e) {
        // 응답 본문에 키/토큰이 섞여 올 수 있으므로 전체는 debug로, 요약만 warn에 남긴다
        log.warn("[Triage] OpenAI-compat 호출 실패: HTTP {}", e.getStatusCode().value());
        return new TriageEngineException(TriageEngineException.Kind.FAILED,
                "AI 호출 실패 (HTTP " + e.getStatusCode().value() + ")", e.getResponseBodyAsString(), e);
    }

    private TriageEngineException mapNetworkError(ResourceAccessException e) {
        log.warn("[Triage] OpenAI-compat 연결 실패/시간 초과: {}", e.getMessage());
        return new TriageEngineException(TriageEngineException.Kind.FAILED,
                "AI 서버 연결 실패 또는 시간 초과", null, e);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(String responseBody) {
        try {
            Map<String, Object> root = jsonMapper.readValue(responseBody, Map.class);
            List<Object> choices = (List<Object>) root.get("choices");
            Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
            return (String) message.get("content");
        } catch (RuntimeException e) {
            throw new TriageEngineException(TriageEngineException.Kind.INVALID_JSON,
                    "AI 응답에서 결과를 찾을 수 없습니다.", responseBody, e);
        }
    }

    private Map<String, Object> buildBody(String maskedRequest, String format) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("messages", List.of(
                Map.of("role", "system", "content", TriagePrompt.SYSTEM),
                Map.of("role", "user",   "content", TriagePrompt.wrapUserRequest(maskedRequest))
        ));
        Map<String, Object> rf = responseFormatPayload(format);
        if (rf != null) {
            body.put("response_format", rf);
        }
        return body;
    }

    private static Map<String, Object> responseFormatPayload(String format) {
        return switch (format) {
            case "json_schema" -> Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", "triage_result",
                            "strict", true,
                            "schema", openAiSchema()
                    )
            );
            case "json_object" -> Map.of("type", "json_object");
            default -> null;
        };
    }

    /** Gemini 엔진의 스키마와 동치지만 OpenAI JSON Schema 형식(lowercase types, strict 호환 null 유니온). */
    private static Map<String, Object> openAiSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("requestType", enumOf(List.of("INCIDENT", "SERVICE_REQUEST", "CHANGE", "INQUIRY")));
        props.put("category", enumOf(TriageVocabulary.CATEGORIES));
        props.put("environment", nullableEnumOf(List.of("PRODUCTION", "STAGING", "DEV")));
        props.put("requestedTime", nullableTypeOf("string"));
        props.put("recommendedPriority", enumOf(List.of("P1", "P2", "P3", "P4")));
        props.put("recommendedTeam", enumOf(TriageVocabulary.TEAMS));
        props.put("affectedService", nullableTypeOf("string"));
        props.put("testPlanProvided", nullableTypeOf("boolean"));
        props.put("rollbackPlanProvided", nullableTypeOf("boolean"));
        props.put("missingFields", arrayOf(enumOf(TriageVocabulary.fieldKeys())));
        props.put("riskFactors", arrayOf(enumOf(TriageVocabulary.RISK_FACTORS)));
        props.put("evidence", arrayOf(Map.of(
                "type", "object",
                "properties", Map.of(
                        "factor", enumOf(TriageVocabulary.RISK_FACTORS),
                        "quote", Map.of("type", "string")
                ),
                "required", List.of("factor", "quote"),
                "additionalProperties", false
        )));
        props.put("clarifyingQuestions", arrayOf(Map.of("type", "string")));
        props.put("confidence", Map.of("type", "number"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        // strict:true는 모든 property의 required 선언을 요구한다. optional 항목은 null 유니온으로 처리.
        schema.put("required", new ArrayList<>(props.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static Map<String, Object> nullableEnumOf(List<String> values) {
        List<Object> withNull = new ArrayList<>(values);
        withNull.add(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", List.of("string", "null"));
        m.put("enum", withNull);
        return m;
    }

    private static Map<String, Object> nullableTypeOf(String type) {
        return Map.of("type", List.of(type, "null"));
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) return "http://localhost:11434/v1";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 폴백 상태는 analyze() 호출 범위 안에서만 유지한다 (인스턴스 상태 아님 → 스레드 안전). */
    private static class FormatState {
        private String format;
        private boolean fallbackUsed;

        FormatState(String initial) {
            this.format = initial;
            this.fallbackUsed = false;
        }

        boolean canFallback() {
            return !fallbackUsed && "json_schema".equals(format);
        }

        void fallbackToJsonObject() {
            this.format = "json_object";
            this.fallbackUsed = true;
        }
    }
}
