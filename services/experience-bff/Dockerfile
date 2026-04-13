FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy parent POM and shared libs for dependency resolution
COPY services/pom.xml services/pom.xml
COPY libs/tech-companion/pom.xml libs/tech-companion/pom.xml
COPY libs/shared-kernel-java/pom.xml libs/shared-kernel-java/pom.xml

# Copy source
COPY libs/tech-companion/src libs/tech-companion/src
COPY libs/shared-kernel-java/src libs/shared-kernel-java/src
COPY services/experience-bff/pom.xml services/experience-bff/pom.xml
COPY services/experience-bff/src services/experience-bff/src

# Build
WORKDIR /workspace/services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl experience-bff -am -DskipTests package -q

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /workspace/services/experience-bff/target/experience-bff-*.jar app.jar
EXPOSE 8160
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
