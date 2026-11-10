# Runtime image for the writer. The jar is built beforehand with `mvn package` (see README, "Running with Docker Compose").
FROM eclipse-temurin:25-jre

RUN useradd --system --uid 10001 sportsmutationsdataloader
WORKDIR /app
# Build context is the repository root (see docker-compose.yml)
COPY sports-mutations-dataloader-writer/target/sports-mutations-dataloader-writer-*.jar app.jar
USER sportsmutationsdataloader

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
