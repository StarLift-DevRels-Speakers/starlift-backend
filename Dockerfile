# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN --mount=type=cache,id=starlift-maven,target=/root/.m2,sharing=locked \
    mvn -B -ntp -q dependency:go-offline

COPY src src
RUN --mount=type=cache,id=starlift-maven,target=/root/.m2,sharing=locked \
    mvn -B -ntp -Dmaven.test.skip=true package

ARG JAR_FILE=target/*.jar
# Extract stable dependencies separately from frequently changing application code.
RUN cp ${JAR_FILE} app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S -g 10001 appuser \
    && adduser -S -D -H -u 10001 -G appuser -h /app -s /sbin/nologin appuser \
    && chown appuser:appuser /app

COPY --from=build --chown=10001:10001 /app/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /app/extracted/application/ ./

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
