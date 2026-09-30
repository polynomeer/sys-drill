-- 슬라이스 6 (docs/LEARNING_COMMUNITY_PLAN.md §6.2, docs/adr/0041) — 풀이 공유.
--
-- 새 테이블 대신 sessions 에 컬럼을 더한다. scenarios.visibility 가 이미 같은
-- 문제를 같은 방식으로 풀었고(V34), 공개 단위가 "세션 하나 통째"라 별도 엔티티가
-- 담을 고유 상태가 사실상 없다.
--
-- 기본값 PRIVATE — ARCHITECTURE §13 "사용자 설계 답안은 기본 비공개, 공유는
-- 명시적 옵션". scenarios 쪽 기본값이 PUBLIC 인 것과 반대인데, 그건 콘텐츠고
-- 이건 남의 답안이기 때문이다. 이미 쌓인 세션이 소급 공개되는 일은 없어야 한다.
alter table sessions add column visibility varchar(20) not null default 'PRIVATE';

-- 닉네임 대신 익명으로 공개하는 선택(§6.2). 공개를 철회했다가 다시 하면 이 값만
-- 바꿀 수 있으므로 visibility 와 분리해 둔다.
alter table sessions add column shared_anonymously boolean not null default false;

-- 목록 정렬용(최신순). 공개를 철회하면 다시 null 이 된다 — "언제 공개했나"는
-- 공개 중일 때만 의미가 있다.
alter table sessions add column shared_at timestamptz;

-- 목록 조회는 항상 "이 시나리오 버전의 공개된 세션"이라 부분 인덱스로 충분하다.
create index idx_sessions_public_by_version
    on sessions (scenario_version_id, shared_at desc)
    where visibility = 'PUBLIC';
