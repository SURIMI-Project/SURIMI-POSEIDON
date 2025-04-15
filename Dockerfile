FROM eclipse-temurin:21-jre

WORKDIR /app

# Create an output direction where `fishing_actions.csv`
# will be written once the simulation is completed:
RUN mkdir /app/outputs 

# Add the `logging.properties` to make sure logs
# are shown when running within the container
# while ensuring GeoTools doesn't bombard the
# user with warnings:
COPY logging.properties /app/logging.properties

# Copy the fat jar that includes POSEIDON and all dependencies:
COPY build/libs/SURIMI-POSEIDON-all.jar /app/POSEIDON.jar

# Copy the scenario file and the data files
# for the Western Med case study:
COPY western_med/scenario.yaml /app/
COPY western_med/data/ /app/data/

# Have POSEIDON run the scenario as the container's entry point:
ENTRYPOINT ["java", "-Djava.util.logging.config.file=/app/logging.properties", "-jar", "/app/POSEIDON.jar", "-s", "scenario.yaml"]
# The port on which to listen for gRPC requests can be changed but defaults to 50051
CMD ["-p", "50051"]
