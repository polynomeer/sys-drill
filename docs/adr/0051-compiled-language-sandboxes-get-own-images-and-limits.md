---
status: accepted
---

# Java·Kotlin·Go 채점은 저장소에서 빌드한 이미지와 언어별 자원 한도로 돈다

Build 과제에 Java·Kotlin·Go 판을 더하면서(V72, 우선 rate-limiter) 샌드박스의 "모든 언어가 공식 이미지 + `--cpus 0.5 --memory 128m` + 10초"라는 전제가 깨졌다. 컴파일 언어는 단계마다 컨테이너 안에서 컴파일부터 하는데, 그 한도에서 실측한 결과:

- **Java**(`eclipse-temurin:25-jdk`, 공식 이미지 그대로): `javac` + 실행이 단계당 2~4초 — 기본 한도로 충분.
- **Kotlin**: 공식 컴파일러 이미지가 없다. `kotlinc`는 128m에서 OOM으로 죽고, 384m·0.5 CPU에서도 컴파일만 7초 가까이 걸린다.
- **Go**(`golang:1.25`): 1.20부터 배포판에 표준 라이브러리 빌드 산출물이 없어, 빈 캐시로 `go run`하면 runtime·fmt·sync부터 다시 컴파일하다 128m에서 OOM으로 죽는다(18초 후).

**결정**: Kotlin과 Go는 [`sandbox/`](../../sandbox/)의 Dockerfile로 이미지를 직접 빌드한다 — Kotlin은 JDK 이미지에 JetBrains 릴리스 `kotlinc`를 얹고, Go는 `go build std` 캐시를 이미지에 구워 둔다(이후 단계당 약 1초). 그리고 `SandboxExecutor`의 언어별 런타임이 자원 한도를 따로 가진다: Kotlin만 `--cpus 1.0 --memory 384m`, 컴파일 언어는 설정된 테스트 시간에 컴파일 몫(Java·Go 5초, Kotlin 10초)을 더 받는다.

**대안과 이유**: 한도를 모든 언어에 일괄로 올리는 쪽은 Python·TS 제출까지 호스트 자원을 더 쓰게 하고, 무제한 루프 같은 악성 제출에 주는 여유도 늘린다. Go를 공식 이미지 그대로 쓰고 메모리만 올리는 쪽은 매 단계 표준 라이브러리 재컴파일(1 CPU에서도 십수 초 × 6단계)을 치른다. 컴파일을 한 번만 하고 여섯 단계를 한 컨테이너에서 돌리는 쪽은 "단계마다 새 컨테이너"라는 격리 모델(ADR-0007)과 워커 구조를 바꿔야 한다.

**대가**: 이미지 두 개를 저장소가 소유한다 — 로컬과 CI 모두 `docker compose --profile sandbox-images build`가 선행돼야 하고, 버전(Kotlin 2.4.20, Go 1.25)을 올리는 것도 우리 일이다. Kotlin 제출은 단계당 4~5초, 채점 한 번에 30초 안팎이 걸리고, 워커 한 스레드가 잡는 자원이 다른 언어의 두세 배다(`worker-concurrency` 주석 참고).
