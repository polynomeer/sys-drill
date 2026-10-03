# Build Mode 채점 샌드박스 — Go (ADR-0051, ADR-0052). Go 배포판에는 표준 라이브러리의 컴파일
# 산출물이 들어 있지 않아(1.20부터), 빈 캐시로 `go run` 하면 매 채점마다 runtime·fmt·
# sync 부터 다시 컴파일한다 — 128m 에서는 그 컴파일러가 OOM 으로 죽는다. 이미지에
# 빌드 캐시를 구워 두면 제출 코드만 컴파일한다(약 1초).
#
# 채점은 `go run -race` 로 돈다(ADR-0052) — 레이스 디텍터는 cgo 가 필요하고, 레이스용
# 표준 라이브러리 빌드도 따로 캐시해 둔다. GORACE 는 첫 경쟁에서 바로 멈추게 해서,
# 경쟁이 난 제출이 끝까지 돌아 RESULT:PASS 를 찍는 일이 없게 한다.
FROM golang:1.25

ENV GOCACHE=/root/.cache/go-build \
    GOTOOLCHAIN=local \
    CGO_ENABLED=1 \
    GORACE=halt_on_error=1
RUN go build std && go build -race std
