package com.ordo.itsm.assistant.engine;

import java.util.Map;

/** 어시스턴트가 LLM 에 노출하는 도구 1개의 스펙 (OpenAI 호환 tools). */
public record ToolSpec(String name, String description, Map<String, Object> parametersSchema) {
}
