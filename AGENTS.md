# AGENTS.md

Guidance for coding agents (Claude Code and others) working in this repository.

## What this is

SURIMI-POSEIDON is a gRPC service (Java 25) that wraps the **POSEIDON** agent-based fisheries
model and exposes it to the SURIMI controller as the `FisheryService` gRPC API. This repo is the
*service layer*: gRPC handlers, request/response mapping, TAC-quota tracking, and the concrete
scenarios that get served (Northwestern Mediterranean in production, a minimal scenario for
tests). The simulation framework itself (agents, biology, regulations engine, YAML scenario
(de)serialization, GUI) lives in the `POSEIDON/` submodule.

Read before working here:

- `POSEIDON/AGENTS.md` and the `POSEIDON/docs/agents/` docs it lists — before touching code under
  `POSEIDON/`, and for the Factory/Scenario pattern this repo's scenarios and factories also follow.
  `POSEIDON/docs/agents/conceptual-correctness.md` applies here too.
- `POSEIDON_architecture.md` — gRPC method tables, interceptor stack, error handling, k8s/env
  config, CI. It is hand-maintained and lags the code; where they disagree, the code wins.

## Composite build

The root project (`src/main`, `src/test`, package `eu.project.surimi.poseidon`) is a single Gradle
project that `includeBuild("POSEIDON")`s the submodule, substituting `POSEIDON:<module>` coordinates
for its subprojects (see `settings.gradle.kts`). Consequences:

- Root tests run **without a module prefix**: `./gradlew test`.
- Submodule tests need the included-build prefix from the root: `./gradlew :POSEIDON:core:test`.
  The `./gradlew :core:test` form in `POSEIDON/AGENTS.md` only works from inside `POSEIDON/`.
- `-Werror` is a submodule convention (`buildlogic.java-common-conventions`): it applies to code
  under `POSEIDON/`, not to `src/`.

## Commands

```
./gradlew build                                          # root + POSEIDON: compile, tests, spotbugs
./gradlew test                                           # root tests (~75 s)
./gradlew test --tests "eu.project.surimi.poseidon.server.SimulationServiceTest"            # one class
./gradlew test --tests "eu.project.surimi.poseidon.server.SimulationServiceTest.methodName"  # one method
./gradlew :POSEIDON:<module>:test                        # a submodule's tests (core, io, agents, …)
./gradlew spotbugsMain                                   # SpotBugs
./gradlew writeNorthwesternMedScenario                   # regenerate scenarios/northwestern_med.yaml
./gradlew run --args="-p 50051"                          # run the server locally
./gradlew runNorthwesternMedGui                          # open the NW Med GUI (local inputs)
```

## Testing

- JUnit 5, AssertJ, Mockito, jqwik.
- The `test` task's JVM args in `build.gradle.kts` are load-bearing: the Mockito agent
  (`MockitoAgentArgumentProvider`) and the flags protobuf/Netty need under JDK 25. Keep them when
  editing the build.
- `*ServiceTest` classes extend `ServiceTest`, which starts a real in-process gRPC server (full
  interceptor chain) and talks to it through a stub. Useful helpers:
  - `contractItems()` returns the default contract for `MinimalScenario` as an `Items.Builder`;
    adjust it and pass it to `initialiseSimulation(id, scenarioName, items[, massUnit])` to test
    contract handling.
  - `simulationManager.getSimulation(id)` reaches the live `Simulation`, e.g. to read an
    accumulator directly and compare it with what a handler reports.
- `ServiceTest` instantiates the scenario's Java class to write its YAML, then serves the YAML.
  That loads classes production never loads (the server only reads YAML), so the test JVM can hide
  class-loading-order bugs such as unit labels registered by static initialisers.
- `S3InputsIntegrationTest` downloads the inputs from the real SURIMI bucket and runs only when
  `AWS_ACCESS_KEY_ID` is set (CI sets it from repository secrets); it is skipped otherwise.
- Two simulations of the same scenario are not reproducible against each other. Compare values
  within one simulation, not across runs.
- SpotBugs: suppress a one-off false positive in place with
  `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "...", justification = "...")` (see
  `WithSimulationRequestHandler.java`). `spotbugs_exclude.xml` is for broad, structural exclusions
  only.

## Protocol and contract

- Generated protocol classes come from the buf registry: the `build.buf.gen:surimi_surimi-protocol_grpc_java`
  dependency in `build.gradle.kts`, whose version ends with the protocol commit hash. Bumping the
  protocol means changing that version. The `.proto` sources live in the separate SURIMI-protocol
  repo (`../SURIMI-protocol` in the usual workspace layout).
- Each gRPC method has exactly one `RequestHandler` subclass, registered in `FisheryService`.
  Handlers that need a live simulation extend `WithSimulationRequestHandler` and receive its
  `SimulationManager.SimulationProperties`.
- `InitialiseSimulation` delivers the **contract**: the species, markets, price categories, fleet
  segments, and units the models in the ensemble exchange information about. `SimulationProperties`
  holds what POSEIDON keeps of it. Invariants every handler upholds:
  - Outputs only mention contract items. POSEIDON may model more; anything else is skipped.
  - Fleet segments: only those with `model` equal to `Server.MODEL_NAME` count; there must be at
    least one and no two may overlap. Blank segment fields are wildcards (`FleetSegment.covers` /
    `overlaps`). Each vessel is reported under the contract segment covering it
    (`SimulationProperties.findContractFleetSegment`, with the vessel's segment from the
    scenario's `FleetSegmentMapper` component); uncovered vessels are skipped.
  - Masses cross the wire in the contract's mass unit. Convert with
    `SimulationProperties.convertKgToStandardMassUnit` / `convertMassInStandardUnitToKg`; parse
    unit strings with POSEIDON's `Measurements`, which registers the si-units labels (`t`, `kn`, …)
    first.
- `Simulation.getComponent(Class)` only finds top-level scenario components (and members of
  collections among them), not objects nested inside other components. To reach something from a
  handler, register it as its own component. Factories cache their product per scope, so one
  factory referenced both as a component and inside another component yields a single instance.

## Scenario generation

`scenarios/northwestern_med.yaml` is **generated**: `./gradlew writeNorthwesternMedScenario` runs
`uk.ac.ox.poseidon.io.ScenarioWriter` on `NorthwesternMedScenario`, and `stageForImage` runs it too,
so the Docker image always embeds a fresh copy. It lives in this repo, not in the `inputs`
submodule, because it changes with the Java code. The data files in `inputs/northwestern_med/` are
produced upstream by the SURIMI-data_preprocessing pipelines, not by this repo.

Users may edit the YAML by hand to try things out locally; `./gradlew run` reads it as is. The rule
below is for agents only.

To change the scenario, edit `NorthwesternMedScenario.java` (or the factories it composes) and
regenerate, committing the YAML with the Java change. The committed YAML must come from actually
running the writer task — a hand edit or scripted find-and-replace by an agent is never
acceptable, however safe it looks or however well it matches a regenerated copy.

Reviewing a regenerated YAML: one added or removed anchor renumbers every later `&idNNN` /
`*idNNN`, so the raw diff looks huge. Normalise before reading it:

```
diff <(git show HEAD:scenarios/northwestern_med.yaml | sed -E 's/id[0-9]+/idN/g') \
     <(sed -E 's/id[0-9]+/idN/g' scenarios/northwestern_med.yaml)
```

If the regenerated diff is wider than your change (the committed YAML had drifted from what the
Java produces), stop and tell the user; how to resolve the drift is their call. One clean way to
separate the drift from your own change: stash your Java changes, regenerate and commit the drift
alone, then unstash, regenerate and commit your change — each commit then comes from a real writer
run.

## Submodules

Two submodules (`.gitmodules`), each its own repo:

- `POSEIDON/` — framework code, `main` branch of `poseidon-fisheries/POSEIDON`.
- `inputs/` — scenario data (`SURIMI-Project/SURIMI-POSEIDON_inputs`, `master` branch); large files
  are in Git LFS.

After cloning or pulling: `git submodule update --init --recursive`.

- `M POSEIDON` / `M inputs` in `git status` means the submodule's checked-out commit differs from
  the recorded pointer, not uncommitted work here. Stage files by path so an unrelated pointer
  bump doesn't ride along.
- A change that touches a submodule is two commits: one inside the submodule, then a pointer bump
  here. Push the submodule commit before pushing a superproject commit that points to it.

## Docker image

```
./gradlew stageForImage      # build/image/: jar, runtime libs, logging.properties, scenarios/northwestern_med.yaml
./gradlew buildDockerImage    # docker build -t ghcr.io/surimi-project/surimiposeidon:latest .
./gradlew pushDockerImage     # docker push to GHCR — confirm with the user before running
```

Entry point: `eu.project.surimi.poseidon.server.Server`. Default port `50051` (`-p`), scenario
folder `scenarios` (`-s`). The image has no input data: with `AWS_BUCKET_NAME` set, `S3Inputs`
downloads it into `inputs/` at startup (see `POSEIDON_architecture.md`, "S3 bucket").

## Code layout (`src/main/java/eu/project/surimi/poseidon/`)

- `server/` — gRPC plumbing (`Server`, `FisheryService`, request handlers, interceptors,
  `SimulationManager`), one subpackage per method group, plus `fleet/` (fleet segments and their
  mapper) and `mappers/` (proto ↔ POSEIDON conversions).
- `regulations/` — SURIMI-specific regulations: TAC quotas, MPA closures and fleet restrictions,
  port closures.
- `calibration/` — `LandingsAccumulator` for calibration.
- `scenarios/` — `minimal/` (tests) and `northwesternmed/` (production scenario, UI, calibration
  entry point).

## Licensing

Every source file carries the GNU GPLv3 header (University of Oxford copyright). Copy it from a
neighbouring file when creating a new one.

## Design docs

Design and spec documents (e.g. under `docs/superpowers/specs/`) stay uncommitted: write them and
leave them for the user to commit, even when a skill's process says to commit.
