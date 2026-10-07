package com.ordo.itsm.triage;

import com.ordo.itsm.ticket.EnvironmentType;
import com.ordo.itsm.ticket.Priority;
import com.ordo.itsm.ticket.RequestType;

import java.util.List;

/**
 * AI 트리아지 결과. AI는 "사실 추출"만 하며 점수·등급·승인 필드는 존재하지 않는다.
 * (프롬프트 인젝션으로 "승인해"라고 해도 승인할 수 있는 필드가 없음)
 */
public record TriageResult(
        RequestType requestType,
        String category,
        EnvironmentType environment,
        String requestedTime,
        Priority recommendedPriority,
        String recommendedTeam,
        String affectedService,
        Boolean testPlanProvided,
        Boolean rollbackPlanProvided,
        List<String> missingFields,
        List<String> riskFactors,
        List<Evidence> evidence,
        List<String> clarifyingQuestions,
        Double confidence,
        /** 서버 보정 내역. 엔진은 채우지 않고 TriageValidator가 채운다. [FIX] = AI 오류 교정, [FILL] = 규칙 기반 누락 보완. */
        List<String> corrections
) {
    /** 위험 요인별 원문 근거 */
    public record Evidence(String factor, String quote) {
    }
}
