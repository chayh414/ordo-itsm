package com.ordo.itsm.triage.engine;

/**
 * 트리아지 엔진. 구현체를 바꿔 끼울 수 있도록 인터페이스로 분리한다.
 * (Gemini → 다른 LLM → 사내 LLM 교체 가능, 기획안 13.2)
 */
public interface TriageEngine {

    /** 마스킹된 요청 텍스트를 분석한다. 실패 시 TriageEngineException */
    TriageOutcome analyze(String maskedRequest);

    String modelName();
}
