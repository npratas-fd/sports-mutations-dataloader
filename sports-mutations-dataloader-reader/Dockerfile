# Runtime image for the reader. The jar is built beforehand with `mvn package` (see README, "Running with Docker Compose").
FROM eclipse-temurin:25-jre

RUN useradd --system --uid 10001 sportsmutationsdataloader
WORKDIR /app
# Build context is the repository root (see docker-compose.yml)
COPY sports-mutations-dataloader-reader/target/sports-mutations-dataloader-reader-*.jar app.jar
USER sportsmutationsdataloader

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
