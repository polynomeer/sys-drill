# docs/COMMERCIALIZATION.md — container image for whatever hosting target is
# chosen later (ECS/Fargate/Kubernetes, per docs/ARCHITECTURE.md §3); no
# cloud-specific IaC here on purpose, see docs/adr and the commercialization
# plan for why that's deferred.
FROM gradle:9.5.1-jdk21 AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S sysdrill && adduser -S sysdrill -G sysdrill
COPY --from=build /app/build/libs/*.jar app.jar
USER sysdrill
EXPOSE 8081
# docs/COMMERCIALIZATION.md — marks this as a real deployment, not
# bootRun/tests, so JwtSecretStartupCheck can fail fast if the JWT secret
# was never overridden from its dev-only default.
ENV SYSDRILL_DEPLOYMENT_MODE=container
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
