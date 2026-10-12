package com.ordo.itsm.assistant.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallRecoveryTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Set<String> ALLOWED = Set.of(
            "create_request", "list_my_requests", "get_request");

    // --------- 형태 1: 함수 호출 문법 --------------------------------

    @Test
    @DisplayName("함수 호출 문법: create_request(\"…내용…\") → create_request text 복구")
    void functionCall_withQuotedString() {
        String content = "요청서를 작성하고 있습니다. create_request(\"운영 DB 테이블 컬럼 추가. 롤백 아직.\")";
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(content, ALLOWED, mapper);

        assertEquals(1, rc.size());
        assertEquals("create_request", rc.get(0).name());
        assertEquals("운영 DB 테이블 컬럼 추가. 롤백 아직.", rc.get(0).arguments().get("text"));
    }

    @Test
    @DisplayName("함수 호출 문법: get_request(\"ORD-...\") → ticketNo 복구")
    void functionCall_getRequest() {
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(
                "get_request(\"ORD-20261012-0001\") 조회해드리겠습니다.", ALLOWED, mapper);

        assertEquals(1, rc.size());
        assertEquals("get_request", rc.get(0).name());
        assertEquals("ORD-20261012-0001", rc.get(0).arguments().get("ticketNo"));
    }

    @Test
    @DisplayName("함수 호출 문법: 명명 인자(text=\"…\") 도 처리")
    void functionCall_namedArgs() {
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(
                "create_request(text=\"운영 DB 변경 요청\")", ALLOWED, mapper);

        assertEquals(1, rc.size());
        assertEquals("운영 DB 변경 요청", rc.get(0).arguments().get("text"));
    }

    // --------- 형태 2: bare JSON 객체 --------------------------------

    @Test
    @DisplayName("bare JSON: {\"name\":\"create_request\",\"arguments\":{\"text\":\"…\"}} 복구")
    void bareJsonObject() {
        String content = "{\"name\":\"create_request\",\"arguments\":{\"text\":\"운영 DB 컬럼 추가\"}}";
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(content, ALLOWED, mapper);

        assertEquals(1, rc.size());
        assertEquals("create_request", rc.get(0).name());
        assertEquals("운영 DB 컬럼 추가", rc.get(0).arguments().get("text"));
    }

    @Test
    @DisplayName("bare JSON: arguments 가 string 으로 왔을 때도 재파싱")
    void bareJsonObject_stringifiedArgs() {
        String content = "{\"name\":\"get_request\",\"arguments\":\"{\\\"ticketNo\\\":\\\"ORD-x\\\"}\"}";
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(content, ALLOWED, mapper);

        assertEquals(1, rc.size());
        assertEquals("ORD-x", rc.get(0).arguments().get("ticketNo"));
    }

    // --------- 형태 3: <tool_call> 태그 (qwen) -----------------------

    @Test
    @DisplayName("qwen 태그: <tool_call>{...}</tool_call> 복구")
    void qwenToolCallTag() {
        String content = """
                알겠습니다. 요청을 등록하겠습니다.
                <tool_call>
                {"name": "create_request", "arguments": {"text": "운영 DB 컬럼 추가"}}
                </tool_call>
                """;
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(content, ALLOWED, mapper);

        assertEquals(1, rc.size());
        assertEquals("create_request", rc.get(0).name());
        assertEquals("운영 DB 컬럼 추가", rc.get(0).arguments().get("text"));
    }

    @Test
    @DisplayName("qwen 태그 여러 개: 전부 복구")
    void qwenToolCallTag_multiple() {
        String content = """
                <tool_call>{"name":"list_my_requests","arguments":{}}</tool_call>
                그 다음에
                <tool_call>{"name":"get_request","arguments":{"ticketNo":"ORD-x"}}</tool_call>
                """;
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(content, ALLOWED, mapper);

        assertEquals(2, rc.size());
        assertEquals("list_my_requests", rc.get(0).name());
        assertEquals("get_request", rc.get(1).name());
    }

    // --------- 역할 가드 ---------------------------------------------

    @Test
    @DisplayName("역할 밖 도구 (list_requests) 는 허용 안 되면 복구하지 않음")
    void disallowedTool_isNotRecovered() {
        Set<String> requesterOnly = Set.of("create_request", "list_my_requests", "get_request");
        String content = "list_requests(\"INFO_REQUIRED\")";
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(content, requesterOnly, mapper);

        assertTrue(rc.isEmpty(), "요청자 세트에 list_requests 가 없으면 복구 금지");
    }

    @Test
    @DisplayName("JSON 형태로 역할 밖 도구 주입 시도 — 복구 안 됨")
    void disallowedTool_viaJson_isRejected() {
        Set<String> providerOnly = Set.of("list_requests", "get_request");
        String content = "{\"name\":\"create_request\",\"arguments\":{\"text\":\"...\"}}";
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(content, providerOnly, mapper);

        assertTrue(rc.isEmpty(), "공급사 세트에 create_request 가 없으면 복구 금지");
    }

    // --------- 복구 실패 안전처리 ------------------------------------

    @Test
    @DisplayName("일반 자연어는 복구 없음")
    void plainText_noRecovery() {
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(
                "안녕하세요! 어떤 요청을 도와드릴까요?", ALLOWED, mapper);
        assertTrue(rc.isEmpty());
    }

    @Test
    @DisplayName("깨진 JSON 은 복구 없음, 예외도 없음")
    void brokenJson_isSafelyIgnored() {
        List<ToolCallRecovery.Recovered> rc = ToolCallRecovery.parse(
                "{\"name\":\"create_request\",\"arguments\":{broken json", ALLOWED, mapper);
        assertTrue(rc.isEmpty());
    }

    // --------- strip (화면 보호) -------------------------------------

    @Test
    @DisplayName("strip: 함수 호출 문자열은 제거, 자연어는 유지")
    void strip_functionCall_keepsNaturalText() {
        String content = "요청서를 작성했습니다. create_request(\"운영 DB 변경\") 확인해 주세요.";
        String stripped = ToolCallRecovery.strip(content, ALLOWED, mapper);
        assertEquals("요청서를 작성했습니다.  확인해 주세요.".trim(), stripped);
    }

    @Test
    @DisplayName("strip: <tool_call> 태그 제거")
    void strip_qwenTag_removed() {
        String content = "확인했습니다.\n<tool_call>{\"name\":\"get_request\",\"arguments\":{}}</tool_call>\n조회 결과입니다.";
        String stripped = ToolCallRecovery.strip(content, ALLOWED, mapper);
        assertFalse(stripped.contains("<tool_call>"));
        assertTrue(stripped.contains("확인했습니다"));
        assertTrue(stripped.contains("조회 결과입니다"));
    }

    @Test
    @DisplayName("strip: 통째로 bare JSON 이면 빈 문자열")
    void strip_bareJson_wholeContent_becomesEmpty() {
        String content = "{\"name\":\"create_request\",\"arguments\":{\"text\":\"x\"}}";
        String stripped = ToolCallRecovery.strip(content, ALLOWED, mapper);
        assertEquals("", stripped);
    }

    @Test
    @DisplayName("strip: 모르는 도구 이름이면 그대로 둔다")
    void strip_unknownTool_keepsText() {
        String content = "approve_request(\"xxx\")";
        String stripped = ToolCallRecovery.strip(content, ALLOWED, mapper);
        assertEquals(content, stripped);
    }
}
