package com.ordo.itsm.assistant.engine;

import java.util.Map;

/** LLM 이 호출한 도구 1건. arguments 는 OpenAI 스펙상 JSON 문자열로 오지만 여기선 파싱된 Map 으로 보관. */
public record ToolCall(String id, String name, Map<String, Object> arguments) {
}
