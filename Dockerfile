FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/image/ /app/
RUN mkdir -p /app/outputs
ENTRYPOINT [ \
  "java", \
  "-Xms1g", \
  "-Xmx8g", \
  "-Djava.util.logging.config.file=/app/logging.properties", \
  "-cp", "/app/SURIMI-POSEIDON.jar:/app/lib/*", \
  "eu.project.surimi.poseidon.server.Server", \
  "-s", "inputs/western_med/scenario.yaml" \
]
# The port on which to listen for gRPC requests can be changed but defaults to 50051
CMD ["-p", "50051"]
