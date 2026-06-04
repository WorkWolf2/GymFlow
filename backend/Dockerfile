# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY package.json .
COPY package-lock.json .
COPY tailwind.config.js .
COPY src ./src

RUN apk add --no-cache maven nodejs npm && \
    npm ci && \
    npm run build:css:prod && \
    mvn package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache postgresql16-client && \
    addgroup -S spring && adduser -S spring -G spring

RUN mkdir -p /app/database-backups && chown spring:spring /app/database-backups
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080 2169

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
