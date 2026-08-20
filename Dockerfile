# mes_server 레포 홈서버 이전 파일럿 도커 이미지 (OCI systemd 배포와 병행 — deploy.yml 무수정).
# application-prod.yml은 수정하지 않는다 — datasource/redis 접속정보·포트는 compose에서
# env(SPRING_DATASOURCE_URL, SPRING_DATA_REDIS_HOST/PORT, SERVER_PORT)로 오버라이드한다
# (Spring Boot relaxed binding). 운영 포트는 기존 OCI 배포와 동일하게 8086 고정(-Dserver.port 대체).

# ---- 빌드 스테이지 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# gradle wrapper·설정 파일 먼저 복사해 의존성 레이어 캐시 극대화
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# ---- 런타임 스테이지 ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# compose 헬스체크(curl http://localhost:8086/actuator/health)용 — 베이스 이미지에 기본 미포함
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# UID/GID 1000 고정 — 베이스 이미지(Ubuntu 기반)에 이미 uid/gid 1000인 기본 "ubuntu" 계정이
# 있어 충돌하므로 먼저 제거한 뒤 비루트 실행 유저를 재생성한다(community 레포 Dockerfile 패턴).
RUN (userdel -r ubuntu 2>/dev/null || true) \
    && (groupdel ubuntu 2>/dev/null || true) \
    && groupadd --gid 1000 spring \
    && useradd --uid 1000 --gid 1000 --create-home --shell /usr/sbin/nologin spring

COPY --from=build /workspace/build/libs/*.jar app.jar

RUN chown -R spring:spring /app
USER spring

EXPOSE 8086

# JAVA_OPTS로 힙 상한 등을 compose에서 주입할 수 있도록 shell 형태로 실행
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
