-- docs/LEARNING_EXPANSION_PLAN.md L5·L6 (PLAN.md Round E9) — Learning 랩.
--
-- kind:
--   CAPACITY — 규모 추정 연습(L6). spec = {inputs, asks, variants}. 정답은 저장하지 않고 asks의
--              formula(입력 키에 대한 사칙연산)를 서버가 계산한다.
--   ENGINE   — 시뮬레이션 엔진 랩(L5, Round E11). spec = {knobs, watch, predict}. 결과는 엔진이 낸다(ADR-0047).
-- 콘텐츠는 마이그레이션으로 시딩한다(ADR-0002).
create table learning_labs (
    id uuid primary key default gen_random_uuid(),
    slug varchar(64) not null unique,
    kind varchar(16) not null,
    risk_key varchar(64) references learning_concepts(risk_key),
    domain varchar(64),
    title varchar(200) not null,
    summary text not null,
    spec jsonb not null,
    display_order int not null default 0
);

-- 단위는 십진(1 TB = 10^6 MB)으로 통일한다 — 규모 감각 훈련이라 1000/1024 차이는 판정(2배 이내)에 영향이 없다.
insert into learning_labs (slug, kind, title, summary, spec, display_order) values
('capacity-feed', 'CAPACITY', '사진 피드 서비스',
 'Instagram 같은 사진 피드의 쓰기·읽기 트래픽과 저장 용량을 어림합니다. 정확한 숫자보다 자릿수가 맞는지가 중요합니다.',
 '{
   "inputs": [
     {"key": "dau", "label": "DAU", "value": 10000000, "unit": "명"},
     {"key": "postsPerUser", "label": "사용자당 하루 게시물", "value": 2, "unit": "건"},
     {"key": "readWriteRatio", "label": "읽기 : 쓰기", "value": 100, "unit": ": 1"},
     {"key": "imageMB", "label": "사진 크기", "value": 2, "unit": "MB"},
     {"key": "peakFactor", "label": "피크 / 평균", "value": 3, "unit": "배"}
   ],
   "asks": [
     {"key": "writeRps", "label": "평균 쓰기 RPS", "unit": "req/s", "formula": "dau * postsPerUser / 86400"},
     {"key": "peakReadRps", "label": "피크 읽기 RPS", "unit": "req/s", "formula": "writeRps * readWriteRatio * peakFactor"},
     {"key": "storagePerDayTB", "label": "하루 저장량", "unit": "TB", "formula": "dau * postsPerUser * imageMB / 1000000"},
     {"key": "storagePerYearPB", "label": "1년 저장량", "unit": "PB", "formula": "storagePerDayTB * 365 / 1000"}
   ],
   "variants": [
     {"label": "기본"},
     {"label": "DAU가 10배(1억)가 되면?", "overrides": {"dau": 100000000}}
   ]
 }', 1),
('capacity-shortener', 'CAPACITY', 'URL 단축기',
 '쓰기는 적고 읽기는 많은 서비스입니다. 5년치 저장 공간이 얼마나 되는지, 피크 읽기를 어디서 받아야 하는지 어림합니다.',
 '{
   "inputs": [
     {"key": "newUrlsPerDay", "label": "하루 새 단축 URL", "value": 100000000, "unit": "건"},
     {"key": "readWriteRatio", "label": "읽기 : 쓰기", "value": 10, "unit": ": 1"},
     {"key": "recordBytes", "label": "레코드 크기", "value": 500, "unit": "B"},
     {"key": "years", "label": "보관 기간", "value": 5, "unit": "년"},
     {"key": "peakFactor", "label": "피크 / 평균", "value": 2, "unit": "배"}
   ],
   "asks": [
     {"key": "writeRps", "label": "평균 쓰기 RPS", "unit": "req/s", "formula": "newUrlsPerDay / 86400"},
     {"key": "peakReadRps", "label": "피크 읽기 RPS", "unit": "req/s", "formula": "writeRps * readWriteRatio * peakFactor"},
     {"key": "storageTB", "label": "보관 기간 전체 저장량", "unit": "TB", "formula": "newUrlsPerDay * 365 * years * recordBytes / 1000000000000"}
   ],
   "variants": [
     {"label": "기본"},
     {"label": "보관 기간을 10년으로 늘리면?", "overrides": {"years": 10}}
   ]
 }', 2),
('capacity-chat', 'CAPACITY', '메시지 채팅',
 '메시지는 작지만 많습니다. 초당 메시지 수와 하루 저장량이 어느 자릿수인지 어림합니다.',
 '{
   "inputs": [
     {"key": "dau", "label": "DAU", "value": 50000000, "unit": "명"},
     {"key": "messagesPerUser", "label": "사용자당 하루 메시지", "value": 40, "unit": "건"},
     {"key": "messageBytes", "label": "메시지 크기", "value": 200, "unit": "B"},
     {"key": "peakFactor", "label": "피크 / 평균", "value": 3, "unit": "배"}
   ],
   "asks": [
     {"key": "messageRps", "label": "평균 메시지 RPS", "unit": "msg/s", "formula": "dau * messagesPerUser / 86400"},
     {"key": "peakMessageRps", "label": "피크 메시지 RPS", "unit": "msg/s", "formula": "messageRps * peakFactor"},
     {"key": "storagePerDayGB", "label": "하루 저장량", "unit": "GB", "formula": "dau * messagesPerUser * messageBytes / 1000000000"}
   ],
   "variants": [
     {"label": "기본"},
     {"label": "사진 첨부를 허용해 평균 메시지가 20KB가 되면?", "overrides": {"messageBytes": 20000}}
   ]
 }', 3);
