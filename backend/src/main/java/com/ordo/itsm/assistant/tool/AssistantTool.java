package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.security.AuthUser;

import java.util.Map;

/**
 * 어시스턴트가 호출할 수 있는 도구 1개의 인터페이스.
 * - execute(AuthUser, args): 권한·테넌트 범위는 AuthUser 에서 꺼내 사용한다. args 에 tenantId·userId 가 들어와도 무시한다.
 * - parametersSchema(): OpenAI 호환 tools JSON Schema (type/properties/required/additionalProperties).
 */
public interface AssistantTool {

    String name();

    String description();

    Map<String, Object> parametersSchema();

    ToolResult execute(AuthUser user, Map<String, Object> args);
}
