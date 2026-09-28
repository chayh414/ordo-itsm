package com.ordo.itsm.triage.engine;

import com.ordo.itsm.triage.TriageResult;

/** rawJson: 엔진이 돌려준 원본 JSON (감사·디버깅용), result: 파싱된 결과 */
public record TriageOutcome(String rawJson, TriageResult result) {
}
