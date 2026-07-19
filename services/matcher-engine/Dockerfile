# glibc base (NOT alpine/musl) — ONNX Runtime bundles glibc-linked native libs
# for the face/iris extract path; alpine would fail to load them.
FROM eclipse-temurin:21-jre AS runtime

RUN groupadd -r impilo && useradd -r -g impilo impilo

WORKDIR /app
COPY target/matcher-engine-*.jar app.jar

RUN chown -R impilo:impilo /app
USER impilo

EXPOSE 9200

ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
