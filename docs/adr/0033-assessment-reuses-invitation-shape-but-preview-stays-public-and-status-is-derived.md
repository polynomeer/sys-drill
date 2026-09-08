---
status: accepted
---

# 채용 평가는 조직 초대의 이메일 바인딩 토큰 구조를 재사용하지만, 미리보기는 공개로 남기고 상태는 저장하지 않고 파생한다

`OrganizationAssessment`는 `OrganizationInvitation`(ADR-0022/0023)의 opaque 토큰·이메일 바인딩·TTL 구조를 그대로 복제하되, 조직 멤버십 대신 시나리오 하나에 스코프한다. 사용자에게 후보자 계정 필요 여부를 확인했고(AskUserQuestion), "계정 필요"를 선택했다 — [0020](0020-incremental-real-auth-rollout-guest-flow-fully-replaced.md)이 게스트 세션 플로우를 완전히 제거한 것과 같은 결이라, 익명 세션 경로를 새로 만드는 대신 기존 `SessionService.startSession`을 그대로 재사용한다.

**미리보기(`GET /organizations/assessments/{token}`)는 초대 미리보기와 달리 인증 없이 공개된다.** `AuthWebConfig`는 `/organizations/**` 전체를 인증 필수로 등록하지만, 이 경로 하나만 `excludePathPatterns`로 뺐다. 초대 수신자는 이미 플랫폼을 쓰고 있을 가능성이 높은 기존 사용자라 프런트엔드가 로그인 안 돼 있으면 바로 `/login`으로 보내지만(`InvitationAcceptPage`), 채용 후보자는 대체로 SysDrill을 처음 접하는 사람이다 — 무엇을 요청받았는지(회사명, 시나리오)도 못 본 채 로그인부터 강요하면 "왜 가입해야 하는지" 알 방법이 없다. 이 차이를 놓치고 처음엔 초대 패턴을 그대로 복사해 미리보기까지 인증을 요구했다가, 실제 브라우저로 비로그인 후보자 플로우를 검증하는 과정에서 401을 확인하고 바로잡았다. `POST .../start`는 `@AuthenticatedUserId`가 필요하므로 계속 인증이 걸려 있다 — `excludePathPatterns`의 단일 `*`는 토큰 세그먼트 하나만 매칭해 `/start`는 건드리지 않는다.

**평가 상태(`NOT_STARTED`/`IN_PROGRESS`/`COMPLETED`)는 컬럼으로 저장하지 않고 매 요청 시 `resultSessionId`와 그 세션 자체의 `Session.status`에서 파생한다**([0011](0011-derived-values-are-never-persisted.md), [0030의 커리큘럼 완료 판정](0030-onboarding-curriculum-is-advisory-full-replace-and-counts-retroactive-completion.md)과 같은 결) — 별도 상태 필드를 두면 세션이 어떤 이유로든 상태를 바꿀 때 동기화를 놓칠 여지가 생긴다. 관리자의 리포트 조회(`GET /organizations/{orgId}/assessments/{assessmentId}/report`)도 [`SessionAccessGuard`](../../backend/src/main/kotlin/com/sysdrill/backend/session/SessionAccessGuard.kt)를 확장하지 않고 완전히 별도 경로로 갔다 — Game Day의 `requireOwnerOrSpectator`는 "세션 오너와 같은 조직 멤버"를 보는데, 채용 후보자는 애초에 그 조직 멤버가 아니므로(외부 인물) 이 가드가 원천적으로 안 맞는다.
