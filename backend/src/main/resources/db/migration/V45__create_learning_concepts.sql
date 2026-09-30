-- docs/LEARNING_COMMUNITY_PLAN.md §5.2 / ADR-0039 — Learning 개념 라이브러리.
--
-- risk_key 가 PK 다. 이 테이블의 행은 RuleEvaluator 가 채점에 쓰는 riskKey 와
-- 1:1 로 대응해야 하며(LearningConceptCatalogTest 가 검증한다), 그래서 대리키를
-- 두지 않고 채점 엔진의 키를 그대로 식별자로 쓴다 — 두 세계가 같은 어휘를
-- 쓴다는 것이 이 기능의 핵심이다.
--
-- category 는 RuleEvaluator.categoryByRiskKey 를 복제한 값이 아니라 그것으로부터
-- 채운 값이다. 매핑 자체는 채점 로직이라 코드에 남는다(ADR-0039 "범위 밖").
create table learning_concepts (
    risk_key          varchar(80) primary key,
    category          varchar(60)  not null,
    label             varchar(120) not null,
    summary           text         not null,
    why_it_matters    text         not null,
    -- 아래 넷은 목록이라 jsonb. 개수가 개념마다 다르고 순서가 의미를 갖는다.
    symptoms          jsonb        not null default '[]',
    patterns          jsonb        not null default '[]',
    tradeoffs         text         not null,
    related_domains   jsonb        not null default '[]',
    related_actions   jsonb        not null default '[]',
    related_challenges jsonb       not null default '[]',
    -- 화면에서 카테고리 안의 노출 순서. 같은 값이면 label 순.
    display_order     int          not null default 0
);

-- 카테고리별 조회가 유일한 접근 패턴이다(개념 목록 화면).
create index idx_learning_concepts_category on learning_concepts (category, display_order);
