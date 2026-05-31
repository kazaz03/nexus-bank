# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q && \
    find target -maxdepth 1 -name "*.jar" ! -name "*.original" -exec cp {} target/app.jar \;

# ---- Run Stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S nexus && adduser -S nexus -G nexus

COPY --from=build --chown=nexus:nexus /workspace/target/app.jar app.jar

USER nexus

EXPOSE 8083

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseSerialGC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
