# Build Mode 채점 샌드박스 — Kotlin (ADR-0051). 공식 Kotlin 컴파일러 이미지가 없어
# JDK 이미지에 JetBrains 릴리스의 kotlinc 를 얹는다. 채점 컨테이너는 --network none 이라
# 필요한 것은 전부 빌드 시점에 받아 둔다.
FROM eclipse-temurin:21-jdk

ARG KOTLIN_VERSION=2.4.20
RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip \
 && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o /tmp/kotlinc.zip "https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip" \
 && unzip -q /tmp/kotlinc.zip -d /opt \
 && rm /tmp/kotlinc.zip

# JAVA_OPTS 는 kotlinc 스크립트의 기본값(-Xmx256M -Xss2m)을 대체한다 — -Xmx 를 빼면 컨테이너
# 메모리의 1/4 만 힙으로 잡혀 OOM 이 난다. 수명이 몇 초인 컴파일러라 C1 만 쓰는 편이 빠르다.
ENV PATH=/opt/kotlinc/bin:$PATH \
    KOTLIN_STDLIB=/opt/kotlinc/lib/kotlin-stdlib.jar \
    JAVA_OPTS="-Xmx256m -Xss2m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
