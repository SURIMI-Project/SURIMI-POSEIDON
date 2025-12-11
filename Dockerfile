FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/image/ /app/
RUN mkdir -p /app/outputs
ENTRYPOINT [ \
  "java", \
  "-Djava.util.logging.config.file=/app/logging.properties", \
  "-cp", "/app/SURIMI-POSEIDON.jar:/app/lib/*", \
  "eu.project.surimi.poseidon.server.Server", \
  "-s", "scenario.yaml" \
]
# The port on which to listen for gRPC requests can be changed but defaults to 50051
CMD ["-p", "50051"]
