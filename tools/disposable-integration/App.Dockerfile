FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

COPY server/target/server-0.0.1-SNAPSHOT.jar /app/server.jar
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
