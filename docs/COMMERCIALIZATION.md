# 상용화 체크리스트

> 이 문서는 SysDrill을 실사용자에게 유료로 제공하기까지 남은 작업을 정리한다. [ROADMAP.md](ROADMAP.md)의 Phase 1~6은 기능 구현 기준으로는 끝났지만, "로드맵 운영 원칙"이 요구하는 실사용자 검증 신호는 아직 하나도 확보되지 않은 상태다 — 이 문서의 항목들은 그 검증을 전제로 하지, 검증을 대신하지 않는다.
>
> 두 축으로 나눈다: **코드로 구현 가능한 것**(Claude Code가 이 저장소 안에서 만들 수 있는 것)과 **사람이 직접 해야 하는 일**(계정 개설, 법적 검토, 의사결정, 외부 계약 등 코드 밖의 일). 각 항목은 지금 코드베이스에서 실제로 확인한 현재 상태를 근거로 한다.

## 우선순위 요약

전부 한 번에 할 필요는 없다. 실제로 순서가 있다:

1. **베타 오픈에 필요한 최소 세트**: 이메일 발송(조직 초대·평가 초대가 실제로 도달해야 함), 최소한의 에러 추적(Sentry 등), 약관/개인정보처리방침 페이지(회원가입이 이미 개인정보를 받고 있음).
2. **결제를 받기 시작하는 순간 필수가 되는 것**: 결제 연동, LLM 비용 상한, rate limiting, CI/CD, 프로덕션 배포 파이프라인, 사업자 등록.
3. **트래픽/조직 고객이 늘면 필요해지는 것**: 관측성 고도화, 관리자 대시보드, 백업/복구 자동화, 보안 감사.

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
