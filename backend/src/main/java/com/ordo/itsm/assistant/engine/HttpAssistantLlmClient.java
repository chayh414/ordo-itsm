package com.ordo.itsm.assistant.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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
 * OpenAI 호환 /v1/chat/completions 호출. 트리아지와 같은 llm.* 설정을 공유한다.
 * - temperature=0
 * - tool_choice=auto (tools 가 비어 있으면 생략)
 * - response 에서 choices[0].message 를 꺼내 AssistantReply 로 변환
 */
@Slf4j
@Component
public class HttpAssistantLlmClient implements AssistantLlmClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int contextLength;

    public HttpAssistantLlmClient(JsonMapper jsonMapper,
                                  @Value("${llm.base-url:http://localhost:11434/v1}") String baseUrl,
                                  @Value("${llm.api-key:}") String apiKey,
                                  @Value("${llm.model:}") String model,
                                  @Value("${llm.timeout-seconds:60}") int timeoutSeconds,
                                  @Value("${llm.context-length:8192}") int contextLength) {
        this.jsonMapper = jsonMapper;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.contextLength = contextLength;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public AssistantReply chat(List<ChatMessage> messages, List<ToolSpec> tools) {
        String json = jsonMapper.writeValueAsString(buildBody(messages, tools));
        String response;
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header("Authorization", "Bearer " + apiKey);
            }
            response = spec.body(json).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            log.warn("[Assistant LLM] HTTP {} — {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AssistantLlmException("AI 서버가 요청을 거부했습니다 (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (ResourceAccessException e) {
            log.warn("[Assistant LLM] 연결 실패/시간 초과: {}", e.getMessage());
            throw new AssistantLlmException("AI 서버 연결 실패 또는 시간 초과", e);
        }
        return parseReply(response);
    }

    private Map<String, Object> buildBody(List<ChatMessage> messages, List<ToolSpec> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("messages", messages.stream().map(this::serializeMessage).toList());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(HttpAssistantLlmClient::serializeTool).toList());
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private Map<String, Object> serializeMessage(ChatMessage m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", m.role());
        if (m.content() != null) {
            map.put("content", m.content());
        } else if ("assistant".equals(m.role())) {
            // OpenAI 스펙: tool_calls 가 있고 content 가 없으면 null 로 명시
            map.put("content", null);
        }
        if (m.toolCallId() != null) {
            map.put("tool_call_id", m.toolCallId());
        }
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            map.put("tool_calls", m.toolCalls().stream().map(c -> {
                Map<String, Object> fc = new LinkedHashMap<>();
                fc.put("name", c.name());
                fc.put("arguments", serializeArguments(c.arguments()));
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("id", c.id());
                wrapper.put("type", "function");
                wrapper.put("function", fc);
                return wrapper;
            }).toList());
        }
        return map;
    }

    private String serializeArguments(Map<String, Object> args) {
        if (args == null) return "{}";
        try {
            return jsonMapper.writeValueAsString(args);
        } catch (RuntimeException e) {
            return "{}";
        }
    }

    private static Map<String, Object> serializeTool(ToolSpec t) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", t.name());
        function.put("description", t.description());
        function.put("parameters", t.parametersSchema());
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    private AssistantReply parseReply(String body) {
        try {
            Map<String, Object> root = jsonMapper.readValue(body, Map.class);
            List<Object> choices = (List<Object>) root.get("choices");
            Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
            String content = (String) message.get("content");
            List<Object> rawCalls = (List<Object>) message.get("tool_calls");
            List<ToolCall> calls = new ArrayList<>();
            if (rawCalls != null) {
                for (Object raw : rawCalls) {
                    Map<String, Object> callMap = (Map<String, Object>) raw;
                    String id = (String) callMap.get("id");
                    Map<String, Object> function = (Map<String, Object>) callMap.get("function");
                    String name = (String) function.get("name");
                    Map<String, Object> args = parseArguments(function.get("arguments"));
                    calls.add(new ToolCall(id, name, args));
                }
            }

            // usage.prompt_tokens 모니터링 — 컨텍스트 압박 사전 감지
            Integer promptTokens = extractPromptTokens(root);
            if (promptTokens != null) {
                log.info("[Assistant LLM] prompt_tokens={} (ctx={}, model={})",
                        promptTokens, contextLength, model);
                if (promptTokens > (int) (contextLength * 0.8)) {
                    log.warn("[Assistant LLM] prompt_tokens {} > 80% of context_length {} — tool_calls 누락/텍스트 복구 가능성 상승",
                            promptTokens, contextLength);
                }
            }

            return new AssistantReply(content, calls, promptTokens);
        } catch (RuntimeException e) {
            log.warn("[Assistant LLM] 응답 파싱 실패: {}", body);
            throw new AssistantLlmException("AI 응답을 해석하지 못했습니다", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Integer extractPromptTokens(Map<String, Object> root) {
        Object usage = root.get("usage");
        if (!(usage instanceof Map<?, ?> u)) return null;
        Object pt = ((Map<String, Object>) u).get("prompt_tokens");
        if (pt instanceof Number n) return n.intValue();
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object raw) {
        if (raw == null) return Map.of();
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        String s = raw.toString();
        if (s.isBlank()) return Map.of();
        try {
            return jsonMapper.readValue(s, Map.class);
        } catch (RuntimeException e) {
            return Map.of();   // 모델이 깨진 JSON 을 돌려주면 안전하게 빈 인자로 처리
        }
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isBlank()) return "http://localhost:11434/v1";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
