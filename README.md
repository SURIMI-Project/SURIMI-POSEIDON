# SURIMI-POSEIDON

[![Java CI with Gradle](https://github.com/SURIMI-Project/SURIMI-POSEIDON/actions/workflows/gradle.yml/badge.svg)](https://github.com/SURIMI-Project/SURIMI-POSEIDON/actions/workflows/gradle.yml)
[![codecov](https://codecov.io/gh/SURIMI-Project/SURIMI-POSEIDON/graph/badge.svg)](https://codecov.io/gh/SURIMI-Project/SURIMI-POSEIDON)
[![Javadoc](https://github.com/SURIMI-Project/SURIMI-POSEIDON/actions/workflows/javadoc.yml/badge.svg)](https://surimi-project.github.io/SURIMI-POSEIDON/)
[![Licence: GPL-3.0](https://img.shields.io/github/license/SURIMI-Project/SURIMI-POSEIDON)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-blue)](https://openjdk.org/projects/jdk/25/)
[![Docker image](https://img.shields.io/badge/docker-ghcr.io-blue?logo=docker)](https://github.com/SURIMI-Project/SURIMI-POSEIDON/pkgs/container/surimiposeidon)

SURIMI-POSEIDON connects the [POSEIDON](https://github.com/poseidon-fisheries/POSEIDON)
agent-based fisheries model to the SURIMI project. It runs POSEIDON as a gRPC service, which the
SURIMI controller drives alongside the other models of the ensemble. It also contains the
scenario through which SURIMI uses POSEIDON: the Northwestern Mediterranean fishery, where the
model simulates the Spanish bottom trawlers and purse seiners from 2013 onwards. The scenario
covers where the vessels fish, what they catch, land and sell, and how regulations constrain
them.

SURIMI-POSEIDON and its Northwestern Mediterranean scenario are described in SURIMI deliverable
D3.3. The API documentation is published at https://surimi-project.github.io/SURIMI-POSEIDON/.

## Run the model with the GUI

You need JDK 25 and Git.

```
git clone --recurse-submodules https://github.com/SURIMI-Project/SURIMI-POSEIDON.git
cd SURIMI-POSEIDON
./gradlew runNorthwesternMedGui
```

The first run downloads dependencies and compiles the model, which takes a few minutes. A control
window then opens: press play to start the simulation. The map shows ports, vessels, biomass and
the areas closed to each fleet; the inspector shows the model's components and lets you follow
individual vessels. In this mode, biomass and prices are read from the files in
`inputs/northwestern_med/`.

## Run the model as a SURIMI service

Within SURIMI, the model runs as a gRPC service driven by the SURIMI controller, alongside the
other models of the ensemble. The service is published as a Docker image:

```
docker run -p 50051:50051 ghcr.io/surimi-project/surimiposeidon:latest
```

It listens on port 50051; to use another port, pass `-p <port>` after the image name. To run it
from source instead, use `./gradlew run --args="-p 50051"`. If `OTEL_EXPORTER_OTLP_ENDPOINT` is
set, the service sends OpenTelemetry traces and metrics there.

### Protocol

The protocol is defined in [SURIMI-protocol](https://buf.build/surimi/surimi-protocol). A
simulation runs as follows:

1. `InitialiseSimulation` names the scenario (`northwestern_med`) and delivers the *contract*: the
   species, markets, price categories, fleet segments and units that the ensemble's models
   exchange. Outputs only mention contract items, and fleet segments whose `model` is `POSEIDON`
   select the vessels reported.
2. `SimulateStep` advances the model to a given date. Between steps, the controller sends biomass
   (`UpdateBiomass`), prices (`UpdateSpeciesPrices`) and regulations (`UpdateRegulations`), and
   reads catches (`GetCatchDisposition`), fishing activity (`GetFishingActivity`) and sales
   (`GetSales`).
3. `FinaliseSimulation` (or `CancelSimulation`) ends it.

Every response carries the protocol version in a `protocol-version` trailer. Server reflection is
enabled, so `grpcurl -plaintext localhost:50051 list` shows the API.

## Development

```
./gradlew build              # compile and run all tests
./gradlew buildDockerImage   # build the Docker image locally
```

The scenario served to SURIMI, `inputs/northwestern_med.yaml`, is generated from
`NorthwesternMedScenario.java` by `./gradlew writeNorthwesternMedScenario`; don't edit it by hand.
The data files in `inputs/northwestern_med/` come from the SURIMI data-preprocessing pipelines.

## Funding

<p>
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/images/funded_by_the_eu_dark.svg">
    <img src=".github/images/funded_by_the_eu.svg" alt="Funded by the European Union" height="60">
  </picture>
  &nbsp;
  <img src=".github/images/ukri_logo.png" alt="UK Research and Innovation" height="60">
</p>

Funded by the European Union under the Horizon Europe Program, Grant Agreement No. 101157456
(SURIMI). Views and opinions expressed are however those of the author(s) only and do not
necessarily reflect those of the European Union or the European Climate, Infrastructure and
Environment Executive Agency (CINEA). Neither the European Union nor the granting authority can be
held responsible for them.

UK participants in SURIMI are funded by UK Research and Innovation (UKRI) under the UK government’s
Horizon Europe funding Guarantee [grant number 10132993].
