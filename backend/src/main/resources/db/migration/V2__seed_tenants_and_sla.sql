-- =========================================================
-- 시연용 기본 데이터: 테넌트 + 고객사별 SLA 정책
-- (사용자 시드는 비밀번호 암호화가 필요하므로 인증 구현 단계에서 추가)
-- =========================================================

INSERT INTO tenants (code, name, type) VALUES
    ('SYSONE',  '시스원 운영센터', 'PROVIDER'),
    ('A-UNIV',  'A대학교',        'CUSTOMER'),
    ('B-BANK',  'B은행',          'CUSTOMER'),
    ('C-MALL',  'C쇼핑몰',        'CUSTOMER');

-- 고객사 3곳에 동일한 기본 SLA 적용 (분 단위, 영업일은 MVP에서 24시간 기준으로 단순화)
-- P1: 응답 15분 / 해결 4시간
-- P2: 응답 30분 / 해결 8시간
-- P3: 응답 4시간 / 해결 3일
-- P4: 응답 1일 / 해결 5일
INSERT INTO sla_policies (tenant_id, priority, response_minutes, resolution_minutes)
SELECT t.id, p.priority, p.response_minutes, p.resolution_minutes
FROM tenants t
CROSS JOIN (VALUES
    ('P1',   15,   240),
    ('P2',   30,   480),
    ('P3',  240,  4320),
    ('P4', 1440,  7200)
) AS p(priority, response_minutes, resolution_minutes)
WHERE t.type = 'CUSTOMER';
