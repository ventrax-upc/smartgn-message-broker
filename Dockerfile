FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system --gid 10001 app && adduser --system --uid 10001 --ingroup app app
COPY --from=build /workspace/target/smartgn-message-broker-1.0.0.jar app.jar
COPY tools/health.sh /app/health.sh
USER 10001:10001
EXPOSE 8087
HEALTHCHECK --interval=5s --timeout=4s --start-period=20s --retries=30 CMD ["bash", "/app/health.sh"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
