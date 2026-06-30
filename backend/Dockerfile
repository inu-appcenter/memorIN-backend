# ─── 1단계: 빌드 ───────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Gradle wrapper + 의존성 캐시 레이어 분리
# 라이브러리는 잘 안 바뀌기 때문에 캐시로 재사용 하면 빌드 속도가 빨라짐.
# 그래서 의존성파일 gradle 먼저 복사하고 dependencies 다운 받는다.
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon -q

# 소스 복사 및 빌드
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ─── 2단계: 실행 ───────────────────────────────────────────────
# 빌드 도구는 실행할 때 필요없어서 버림 => "멀티스테이지 빌드"
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

# 보안: non-root 실행
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
