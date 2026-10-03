# Build Mode 채점 샌드박스 — Go (ADR-0051). Go 배포판에는 표준 라이브러리의 컴파일
# 산출물이 들어 있지 않아(1.20부터), 빈 캐시로 `go run` 하면 매 채점마다 runtime·fmt·
# sync 부터 다시 컴파일한다 — 128m 에서는 그 컴파일러가 OOM 으로 죽는다. 이미지에
# `go build std` 캐시를 구워 두면 제출 코드만 컴파일한다(약 1초).
FROM golang:1.25

ENV GOCACHE=/root/.cache/go-build \
    GOTOOLCHAIN=local \
    CGO_ENABLED=0
RUN go build std
