# ── build stage ──────────────────────────────────────────────────────────────
FROM gradle:8.12-jdk23 AS build
WORKDIR /app
# 의존성 캐시 레이어를 먼저 만들어 재빌드 시간 단축
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon -q || true
COPY src ./src
RUN gradle bootJar --no-daemon -q -x test

# ── runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:23-jre
WORKDIR /app
# curl: compose/k8s 헬스체크가 /actuator/health 를 호출하는 데 사용.
# eclipse-temurin JRE 기본 이미지에는 curl 이 없으므로 명시적으로 설치한다.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -r -u 1001 appuser
COPY --from=build /app/build/libs/diary-service-0.1.0.jar app.jar
USER appuser
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
