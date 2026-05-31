FROM eclipse-temurin:21-jre
RUN groupadd -r impilo && useradd -r -g impilo impilo
WORKDIR /app
COPY target/product-registry-service-*.jar app.jar
RUN chown -R impilo:impilo /app
USER impilo
EXPOSE 8097
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

