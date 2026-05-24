FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 10001 --home-dir /app --shell /usr/sbin/nologin appuser

ARG JAR_FILE=target/*.jar
COPY --from=build /app/${JAR_FILE} app.jar
RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
