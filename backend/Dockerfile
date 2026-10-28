# syntax=docker/dockerfile:1
# ---- Build : jar avec Maven (les tests tournent en local, ils demandent Docker) ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.test.skip=true package \
    && cp target/garage-backend-*.jar /app/app.jar

# ---- Run : JRE seul, utilisateur non root ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 garage
COPY --from=build /app/app.jar app.jar
USER garage
# Render offre gratuite : 512 Mo de RAM. On limite la JVM pour eviter de depasser la memoire.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
