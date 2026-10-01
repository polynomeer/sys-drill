# 상용화 체크리스트

> 이 문서는 SysDrill을 실사용자에게 유료로 제공하기까지 남은 작업을 정리한다. [ROADMAP.md](ROADMAP.md)의 Phase 1~6은 기능 구현 기준으로는 끝났지만, "로드맵 운영 원칙"이 요구하는 실사용자 검증 신호는 아직 하나도 확보되지 않은 상태다 — 이 문서의 항목들은 그 검증을 전제로 하지, 검증을 대신하지 않는다.
>
> 두 축으로 나눈다: **코드로 구현 가능한 것**(Claude Code가 이 저장소 안에서 만들 수 있는 것)과 **사람이 직접 해야 하는 일**(계정 개설, 법적 검토, 의사결정, 외부 계약 등 코드 밖의 일). 각 항목은 지금 코드베이스에서 실제로 확인한 현재 상태를 근거로 한다.

## 우선순위 요약

전부 한 번에 할 필요는 없다. 실제로 순서가 있다:

0. **지금 당장(실사용자가 이미 겪을 수 있는 문제)**: 인증 토큰-DB 정합성 미검증, 전역 예외 처리 catch-all 부재, 프론트 에러 바운더리 부재 — 아래 "완성도 재진단" 1~3번. 기능은 다 있어도 이 세 가지가 없으면 사용자는 원인 모를 흰 화면/500만 본다.
1. **베타 오픈에 필요한 최소 세트**: 이메일 발송(조직 초대·평가 초대가 실제로 도달해야 함), 최소한의 에러 추적(Sentry 등), 약관/개인정보처리방침 페이지(회원가입이 이미 개인정보를 받고 있음).
2. **결제를 받기 시작하는 순간 필수가 되는 것**: 결제 연동, LLM 비용 상한, rate limiting, CI/CD, 프로덕션 배포 파이프라인, 사업자 등록.
3. **트래픽/조직 고객이 늘면 필요해지는 것**: 관측성 고도화, 관리자 대시보드, 백업/복구 자동화, 보안 감사, 워커 동시성 확장(아래 4번).

---

## 완성도 재진단: 안정성 · 확장성 · 보안 · UX (2026-09-30) ✅ 10개 항목 전부 완료 (2026-10-01)

> 위 "코드로 구현 가능한 것" 섹션은 **기능이 존재하는가**를 기준으로 2026-09-09에 "완료"로 표시됐다. 이번 갱신은 다른 축이다 — **그 기능이 실제로 끊김 없이 동작하는가**를 코드를 직접 읽고 로컬 벤치마크·실사용 중 발견한 문제로 검증했다. 아래 항목은 전부 이 저장소에서 지금 확인한 사실에 근거하며, 파일·라인을 명시한다.
>
> **2026-10-01 갱신**: 아래 10개 항목을 1~7라운드에 걸쳐 전부 처리했다(각 항목에 ✅ 표시, 상세 과정은 `PLAN.md`의 해당 라운드 기록 참고). 정밀 조사 과정에서 당초 진단보다 범위가 넓어진 경우(5번 레이트리밋, 당초 1개 엔드포인트 → 실제 5개)와 좁아진 경우(3번 입력 검증, 당초 "컨트롤러 절반" → 실제 진짜 구멍은 1곳)가 둘 다 있었다 — 숫자만 보고 세운 당초 진단은 추정이었지, 그 자체가 근거는 아니었다는 걸 보여준다.

### 안정성 / 에러 처리

**1. 인증 토큰이 매 요청마다 DB 존재 여부를 검증하지 않는다 — 오늘 실사용자가 겪은 버그의 근본 원인**
- 현재 상태: `AuthInterceptor.preHandle`([AuthInterceptor.kt:21-40](../backend/src/main/kotlin/com/sysdrill/backend/auth/AuthInterceptor.kt))은 `jwtService.verify()`로 서명·만료만 검증하고, 추출한 `userId`가 실제 `users` 테이블에 있는지는 전혀 확인하지 않는다. DB가 리셋되거나(로컬 개발에서 흔함) 유저가 삭제되어도 발급된 지 30일 이내 토큰은 계속 "인증됨"으로 통과하다가, 그 요청이 실제로 `users`를 참조하는 INSERT/UPDATE에 도달했을 때 처음으로 FK 제약 위반이 터진다.
- 실측 재현: `POST /build-challenges/rate-limiter/submissions` 호출 시 `DataIntegrityViolationException`(`build_submissions_user_id_fkey`) → 클라이언트는 원인 불명의 `500 Internal Server Error`만 받음.
- 조치:
  - (빠른 완화) `GlobalExceptionHandler`에 사용자 FK 위반을 401로 매핑하는 핸들러 추가.
  - (근본 수정) `AuthInterceptor` 또는 `AuthenticatedUserIdArgumentResolver`에서 유저 존재 여부를 확인 — 매 요청 DB 왕복은 과하므로 Redis에 짧은 TTL(예: 60초)로 캐시.
  - 아래 7번(로그아웃/토큰 폐기)과 함께 설계하면 "유저 삭제 시 세션 즉시 무효화"까지 한 번에 해결됨.

**2. 전역 예외 처리기에 catch-all이 없다**
- 현재 상태: `GlobalExceptionHandler`([GlobalExceptionHandler.kt](../backend/src/main/kotlin/com/sysdrill/backend/common/web/GlobalExceptionHandler.kt))는 앱이 정의한 6개 커스텀 예외(`NotFoundException` 등)만 처리한다. 그 외 모든 예외(DB 제약 위반, NPE, 외부 API 타임아웃 등)는 Spring Boot 기본 `/error` 핸들러로 떨어져 `{"status":500,"error":"Internal Server Error"}`처럼 원인을 전혀 알 수 없는 응답만 나간다(1번에서 실제로 관찰).
- 조치: `@ExceptionHandler(Exception::class)` catch-all을 추가해 (a) 서버 로그에 요청 컨텍스트를 구조화해서 남기고 (b) 클라이언트에는 안전한 일반 메시지("일시적인 오류입니다, 잠시 후 다시 시도해주세요")를 반환.

**3. 입력 검증(`@Valid`)이 컨트롤러의 절반에만 적용돼 있다 — ✅ 3라운드(2026-10-01)에서 정밀 감사 완료**
- 당초 상태(이번 라운드 이전): `@RestController` 21개 중 `@Valid`를 쓰는 파일은 11개뿐이라고 추정했었다.
- **정밀 감사 결과**: 실제로 컨트롤러 수는 30개로 늘어나 있었고(다른 기능 추가로), `@Valid` 없는 17개 중 **14개는 애초에 `@RequestBody` 자체가 없는 GET/path-param 전용 엔드포인트**라 `@Valid`가 적용될 자리가 없었다 — "숫자가 절반"이라는 당초 진단은 실제 위험을 과대평가한 것이었다. `@RequestBody`가 있는 나머지 3개(`UserPreferencesController`/`MentorController`/`ProductEventController`)를 하나씩 확인한 결과:
  - `UserPreferencesRequest`(둘 다 nullable enum) — enum 역직렬화 자체가 이미 잘못된 값을 400으로 거부해서(1라운드에서 추가한 `ResponseEntityExceptionHandler` 상속이 이 경로의 응답 형식까지 커버) 추가할 제약이 없음.
  - `ProductEventRequest.name` — `ProductEventService.record()`가 이미 허용 목록(`ALLOWED`) 화이트리스트로 빈 값/미지 값을 전부 400 처리 중이라 `@NotBlank`를 얹어도 실질적으로 더 막아주는 게 없음.
  - **`MentorHintRequest.rawText`** — 진짜 구멍이었다. 길이 제한 없이 그대로 LLM 프롬프트(`MentorService.getHint`)에 들어가고, 실 제출과 달리 `LlmUsageGuard`의 일일 한도 체크도 안 거친다. `@field:Size(max = 20_000)` + 컨트롤러에 `@Valid` 추가로 수정.
- **하지 않은 것**: `community/` 패키지의 `RankingController`/`WriteupController`도 각각 `@RequestBody`(`RankingVisibilityRequest`/`SessionVisibilityRequest`)가 있고 `@Valid`가 없다 — 이 저장소에서 동시에 작업 중인 다른 세션이 그 패키지를 활발히 수정 중이라 충돌을 피하려 이번 라운드에서 제외했다. 다음 감사 때 확인 필요.

### 확장성 / 성능

**4. 핵심 백그라운드 워커 3개가 전부 단일 스레드 — 이번 세션 로컬 벤치마크로 실측**
- 현재 상태: `EvaluationWorker`([EvaluationWorker.kt:48](../backend/src/main/kotlin/com/sysdrill/backend/evaluation/EvaluationWorker.kt)), `BuildRunnerWorker`([BuildRunnerWorker.kt:32](../backend/src/main/kotlin/com/sysdrill/backend/build/BuildRunnerWorker.kt)), `RealInfraSessionSweepWorker`([RealInfraSessionSweepWorker.kt:42](../backend/src/main/kotlin/com/sysdrill/backend/simulation/realinfra/RealInfraSessionSweepWorker.kt)) 전부 `Executors.newSingleThreadExecutor()`.
- 실측: `scripts/benchmark-traffic.sh`로 200 req/s 부하를 걸자 `sysdrill:evaluation:jobs` 큐가 최대 **4,592건**까지 적체됐고, 부하 종료 후 60초를 더 관찰해도 거의 배출되지 않았다(4592→4588). 단일 스레드 + LLM API 왕복 지연이 겹치면 처리량이 사실상 초당 1건 미만으로 수렴한다는 뜻 — 사용자가 몰리는 순간 채점 대기 시간이 무한정 늘어난다.
- 조치: 워커별 동시성을 환경변수로 설정 가능하게 만들고(`newFixedThreadPool(n)`으로 전환). 단, 무작정 늘리면 안 되는 이유가 있다 — evaluation은 사용자별 일일 한도(`LlmUsageGuard`)와, build는 도커 샌드박스 동시 실행 시 호스트 리소스(컨테이너당 `--cpus 0.5`/`--memory 128m`) 총량과 부딪힌다. 동시성 상한은 호스트 스펙 기준으로 별도 산정 필요.
- 부수 발견: 이 벤치마크 도중 로컬 `.env.local`의 실 Anthropic API 키가 워커를 통해 실제로 호출되는 사고가 있었다(비용 영향은 확인 결과 없었음 — 벤치마크 중 완료된 평가 0건). 벤치마크/테스트 실행 시 `LLM_ANTHROPIC_API_KEY`가 절대 활성화되지 않도록 하는 가드(예: 특정 프로파일에서 강제 무시)를 추가하는 게 안전하다.

**5. API 레이트리밋 범위가 인증 3개 엔드포인트로 한정돼 있다 — ✅ 4라운드(2026-10-01)에서 확대 완료**
- 당초 상태: `RateLimitInterceptor`는 `/auth/signup`·`/auth/login`·`/auth/password-reset/request`에만 걸려 있었다. LLM을 호출하는 `POST /sessions/{id}/submissions`는 일일 카운터(`LlmUsageGuard`, 기본 50/day)만 있고 분당 버스트 제한이 없었다.
- **정밀 조사 결과**: LLM 호출 지점을 전부 찾아보니 당초 진단보다 범위가 넓었다 — mentor-hint/postmortem 저장/시뮬레이션 인시던트 내레이션 3개는 **아무 제한도 없었다**(daily cap조차 없음).
- 조치: 기존 `RateLimiter`(Redis 고정 윈도우)를 재사용하는 신규 `ActionRateLimiter`(유저ID 기반, 분당)로 5곳 전부 커버 — evaluation(일일 한도 위에 분당 버스트 추가)/mentor-hint/postmortem 저장/build 제출은 초과 시 429, 시뮬레이션 내레이션만 기존 "fail-open" 설계를 따라 한도 초과도 조용히 내레이션만 생략(인시던트 시작 자체는 막지 않음).

### 보안

**6. JWT 시크릿 기본값이 프로덕션에서도 조용히 통과된다 — ✅ 5라운드(2026-10-01)에서 fail-fast 추가**
- 당초 상태: `jwt-secret: ${SYSDRILL_AUTH_JWT_SECRET:dev-only-insecure-secret-change-me}` — 환경변수 설정을 빠뜨려도 앱은 경고 없이 알려진 문자열을 시크릿으로 써서 정상 기동됐다.
- 이 저장소엔 Spring 프로파일 같은 "프로덕션 감지" 신호가 전혀 없어서(`@Profile` 미사용, Dockerfile도 프로파일 미지정), 당초 조치안("프로덕션 프로파일에서 감지")을 그대로 쓸 수 없었다. 대신 `Dockerfile`이 `SYSDRILL_DEPLOYMENT_MODE=container`를 설정하도록 하고(이 저장소에서 "진짜 배포"를 가리키는 유일한 실제 신호), 신규 `JwtSecretStartupCheck`이 그 신호 + 기본 시크릿 조합일 때만 `@PostConstruct`에서 기동을 막는다 — `bootRun`/테스트는 전혀 영향 없음.

**7. 토큰 폐기(로그아웃) 메커니즘이 서버에 없다, TTL은 30일 — ✅ 6라운드(2026-10-01)에서 구현 완료**
- 당초 상태: `POST /auth/logout` 같은 서버 엔드포인트 자체가 존재하지 않았다 — "로그아웃"은 프론트에서 `localStorage`의 토큰을 지우는 것뿐이었다.
- 조치: 개별 토큰 블랙리스트 대신 유저당 "이 시각 이전 토큰은 전부 무효" 타임스탬프 하나(`TokenRevocationService`, 1번의 `UserExistenceCache`와 정확히 같이 예고했던 메커니즘 공유) — 로그아웃이 자연히 모든 기기 로그아웃이 되고 구현도 단순하다. `JwtService.verify()`가 토큰 발급 시각(`iat`)도 반환하도록 확장해 `AuthInterceptor`에서 체크.
- **실 브라우저 검증 중 발견한 부수 버그**: 로그아웃 직후 백그라운드 폴링(`NotificationBell`)이 막 폐기된 토큰으로 401을 받으면, 1라운드에서 추가한 전역 401 핸들러가 `/login?reason=expired`로 하드 리다이렉트해 로그아웃 자체의 정상 리다이렉트와 경합했다 — `markLoggingOut()` 플래그로 로그아웃 진행 중엔 그 핸들러가 끼어들지 않게 수정.

**8. 표준 보안 헤더가 설정돼 있지 않다 — ✅ 7라운드(2026-10-01)에서 추가 완료**
- 당초 상태: CSP/HSTS/`X-Frame-Options`/`X-Content-Type-Options` 등 레포 전체에서 0건.
- 조치: 프론트는 Next 공식 "Without Nonces" CSP 패턴을 `next.config.ts`의 `headers()`로 전 경로 적용(`connect-src`는 `NEXT_PUBLIC_API_BASE_URL`을 그대로 읽어 실제 백엔드 origin과 항상 일치) + HSTS/Referrer-Policy/Permissions-Policy. 백엔드는 JSON API라 CSP 체감 효과는 적지만 `X-Content-Type-Options`/`X-Frame-Options`/`Referrer-Policy`를 신규 `SecurityHeadersFilter`로. 실 브라우저로 로그인/Bridge(CodeMirror)/대시보드를 돌며 CSP violation 없음 확인.

### UX / 제품 완성도

**9. 프론트엔드에 에러 바운더리가 하나도 없다**
- 현재 상태: `frontend/src/app/` 전체에서 `error.tsx`/`global-error.tsx` 0건(디렉터리 목록으로 확인) — 렌더링 중 예외가 발생하면 Next.js 기본 에러 화면이 그대로 노출된다.
- 조치: 최소한 루트 `app/error.tsx` + `app/global-error.tsx` 추가, 이미 연동된 프론트 Sentry(`@sentry/nextjs`)와 묶어서 에러 리포팅까지 일원화.

**10. 세션 만료/유저 없음 상태에 대한 사용자 안내가 없다**
- 현재 상태: 1번과 짝을 이루는 프론트 쪽 문제 — 서버가 401이든 500이든 반환해도 `frontend/src/lib/api.ts`의 공통 fetch 래퍼는 `ApiError`를 그대로 throw할 뿐, 화면엔 각 페이지의 범용 에러 메시지만 보인다. "세션이 만료됐어요, 다시 로그인해주세요" 안내와 자동 `/login` 리다이렉트가 없다.
- 조치: `api.ts`의 공통 래퍼에서 401 응답을 감지하면 토큰 제거 + `/login` 리다이렉트하는 전역 처리 추가.

---

## 코드로 구현 가능한 것

> **2026-09-09 갱신**: 아래 중 결제/과금과 프로덕션 IaC를 제외한 전 항목을 구현했다. 결제는 가격 정책이라는 사람의 결정이 먼저 필요해서, IaC는 클라우드 프로바이더 결정이 먼저 필요해서 이번엔 손대지 않았다 — 컨테이너 이미지(Dockerfile)까지만 만들었다. 상세 구현 기록은 [PLAN.md](../PLAN.md)의 "상용화 준비" 항목 참고.

### 인증/계정 보안 ✅ 구현 완료
- **비밀번호 재설정**: `PasswordResetService` + `POST /auth/password-reset/request|confirm` + 프론트 `/reset-password`. 토큰 기반, 단회용.
- **이메일 인증**: `EmailVerificationService` + `GET /auth/verify-email` + 프론트 `/verify-email`. 가입 시 자동 발송, 인증 안 해도 기존 기능은 그대로 쓸 수 있음(게이트하지 않음).
- **Rate limiting**: `RateLimiter`(Redis 고정 윈도우) + `RateLimitInterceptor`, `/auth/signup`·`/auth/login`·`/auth/password-reset/request`에 IP 기준 적용.
- **로그인 실패 잠금**: `LoginAttemptService`, 5회 실패 시 15분 잠금(이메일 기준). CAPTCHA는 사이트 키 발급(외부 계정)이 선행돼야 해서 보류.

### 이메일 발송 ✅ 구현 완료
- `EmailSender` 인터페이스 + `SmtpEmailSender`(`spring.mail.host` 설정 시 활성화) + `LoggingEmailSender`(미설정 시 로그만, 기본값) — `MailConfig`가 자동 선택.
- 조직 초대(`OrganizationService.inviteMember`)·채용 평가 초대(`OrganizationAssessmentService.create`)가 생성 즉시 실제 메일 발송을 시도하도록 연결.
- 실제 발송에는 `spring.mail.host`/`username`/`password` 환경변수 설정만 남음(사람이 할 일 — 아래 참고).

### 결제/과금 — 이번 라운드에서 보류
- 가격 정책(구독제/사용량제/티어)이 정해지지 않아 손대지 않았다. 결정만 되면 스코핑 가능.

### LLM 비용 제어 ✅ 구현 완료
- `LlmUsageGuard`(Redis 일별 카운터), `SessionService.submit()`에서 큐 적재 전 체크. 기본 사용자당 일일 50회, 초과 시 409로 명확히 거부.

### CI/CD ✅ 구현 완료
- `.github/workflows/ci.yml` — 백엔드는 `docker compose up`으로 전체 인프라(postgres/redis/kafka/toxiproxy/jaeger)를 띄운 뒤 `./gradlew test` 직접 실행(CI 러너는 매번 새 VM이라 `run-tests-isolated.sh`의 격리 장치가 불필요), 프론트는 lint/tsc/build/`npm audit`.
- `.github/dependabot.yml` — npm/gradle/github-actions 주간 점검.

### 배포/인프라 — Dockerfile까지 완료, IaC는 보류
- `backend/Dockerfile`, `frontend/Dockerfile`(Next.js standalone 출력) 작성, `docker build` 성공 확인.
- 클라우드 프로바이더가 정해지지 않아 Terraform 등 IaC는 만들지 않았다.

### 관측성/장애 대응 — 프론트만 구현, 백엔드는 막힘
- **프론트**: `@sentry/nextjs` 연동(`instrumentation.ts`/`instrumentation-client.ts`), `SENTRY_DSN` 미설정 시 비활성.
- **백엔드**: `sentry-spring-boot-starter-jakarta`를 시도했으나 **이 프로젝트의 Spring Boot 4.1.1과 호환되지 않아**(`RestClientCustomizer` 클래스 누락으로 앱이 아예 기동 안 됨) 되돌렸다. Sentry의 Spring Boot 4 지원이 나오거나, Spring 특화 스타터 없이 순수 Sentry Java SDK를 수동으로 연동하는 방법을 나중에 다시 시도해야 한다.
- DB/Redis 백업 자동화는 미착수(클라우드 프로바이더 결정 이후가 자연스러움).

### 보안 하드닝 ✅ 부분 완료
- `npm audit`로 발견한 실제 취약점(Next.js critical RCE 포함) 수정, CI에 `npm audit` 게이트 추가.
- Dependabot로 향후 취약점 자동 감지.
- CORS는 이미 프로덕션에 안전한 explicit 단일 origin 설정이었음(재확인만, 변경 없음).
- git-secrets류 시크릿 스캔은 미착수.

### 법적 문서의 "그릇" ✅ 구현 완료
- `/terms`, `/privacy` 페이지(명확한 "초안, 법률 검토 필요" 배너 포함, 실제 약관 문구는 플레이스홀더).
- 가입 폼에 필수 동의 체크박스 + `User.termsAcceptedAt` 기록.

### 관리자 도구 ✅ 구현 완료
- `GET /admin/dashboard/stats`(총 사용자/오늘 신규 가입/총 조직/오늘 완료 세션) + 프론트 `/admin` — 기존 `PlatformAccessGuard` 재사용.

---

## 사람이 직접 해야 하는 일

### 사업/법률
- **사업자 등록**(법인 또는 개인사업자) — 결제를 받으려면 선행 필요.
- **이용약관·개인정보처리방침 문구 작성 및 검토** — 특히 AI 평가에 사용자 답안이 어떻게 쓰이는지(LLM 프로바이더로 전송됨), 마켓플레이스 정산, 미성년자 이용 여부 등은 법률 자문을 권장.
- **개인정보 국외 이전 검토** — Anthropic API 등 LLM 프로바이더가 해외 서버라 한국 사용자 대상 서비스라면 개인정보보호법상 국외 이전 고지/동의 절차가 필요할 수 있음.
- **결제대행사(PG)/Stripe 등 가맹점 계약 및 사업자 심사** — 사업자 등록 이후 진행.
- **가격 정책 결정** — 구독제/사용량제/마켓플레이스 수수료율 등은 순수 비즈니스 의사결정이라 코드가 대신할 수 없다.
- **오픈소스 라이선스 준수 검토** — 현재 의존성(Spring Boot, mermaid, swagger-parser 등)의 라이선스가 상용 서비스 배포와 충돌하지 않는지 확인.
- **상표/브랜드명 사용권 확인** — "SysDrill" 이름의 상표 충돌 여부.
- **마켓플레이스 콘텐츠 저작권 정책 수립** — 외부 제작자가 올리는 시나리오의 저작권/신고 처리 정책.

### 계정/외부 서비스
- **클라우드 계정 개설**(AWS/GCP/기타) 및 결제 수단 등록, 예산 알림(Billing Alert) 설정 — 실수로 인한 과금 폭탄 방지.
- **도메인 구매 및 DNS 설정**.
- **이메일 발송 서비스 계정 개설**(SES/SendGrid/Resend 등, SMTP 인터페이스 지원하는 곳이면 코드 변경 없이 `spring.mail.*` 설정만으로 연결됨) 및 발신 도메인 인증(SPF/DKIM/DMARC) — 스팸함행 방지에 필수.
- **Anthropic API 상용 사용량 한도/과금 계약 협의** — 트래픽이 늘 경우 API rate limit 상향이 필요할 수 있음.
- **에러 추적/모니터링 서비스 계정 개설**(Sentry, Datadog 등).
- **보안 감사/침투 테스트 외부 발주** — 결제·개인정보를 다루는 시점에는 자체 점검만으로는 부족.

### 제품 검증 (가장 중요하지만 코드가 대신할 수 없는 것)
- **실사용자 베타 테스터 모집 및 피드백 수집** — ROADMAP.md의 "로드맵 운영 원칙"이 요구하는 검증 신호는 실사용자 없이는 절대 나오지 않는다.
- Phase 1의 핵심 질문("실무적이라 느끼는가, 재도전하는가")에 대한 답이 나오기 전까지는, 결제/마케팅 투자를 늘리는 것보다 베타 규모를 유지하며 신호를 보는 편이 낫다.

### 운영
- **고객 지원 채널 운영** — 이메일/채팅 도구 연동은 코드로 가능하지만, 문의 응대 자체는 사람이 한다.
- **커뮤니티/마케팅 채널 구축**(랜딩 페이지 이후의 사용자 유입).
