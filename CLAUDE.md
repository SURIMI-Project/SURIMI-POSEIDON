# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

SURIMI-POSEIDON is a gRPC service (Java 25) that wraps the **POSEIDON** agent-based fisheries
model (a Git submodule) and exposes it to the SURIMI controller as the `FisheryService` gRPC API.
This repo is the *service layer*: gRPC handlers, request/response mapping, TAC-quota tracking,
and the concrete scenarios (Northwestern Mediterranean, minimal test scenario) that get served.
The simulation framework itself (agents, biology, regulations engine, YAML scenario
(de)serialization, GUI) lives in the `POSEIDON/` submodule — see `POSEIDON/CLAUDE.md` for its
conventions (Factory/Scenario pattern, module graph, "YAML is generated, never hand-edited") before
touching code under `POSEIDON/`.

For full protocol/architecture detail (gRPC method tables, interceptor stack, error-handling,
message-flow diagrams, k8s/env config, CI steps) see **`POSEIDON_architecture.md`** at the repo
root — this file only covers what that doc doesn't: day-to-day commands and things specific to
working across the composite build. That doc's test-class table is a hand-maintained snapshot
(it says so) — if it and `src/test/java/eu/project/surimi/poseidon/` ever disagree, the source
tree is authoritative.

## Composite build shape

This is a **Gradle composite build**: the root project (`src/main`, `src/test`, package
`eu.project.surimi.poseidon`) is a single Gradle project that `includeBuild("POSEIDON")`s the
submodule, substituting `POSEIDON:<module>` dependency coordinates for the submodule's real
subprojects (`agents`, `biology`, `calibration`, `examples`, `gui`, `io`, `regulations` — see
`settings.gradle.kts`). This has two practical consequences:

- Root-level tests are run **without a module prefix**: `./gradlew test`, not `./gradlew :test`.
- POSEIDON submodule tests are reached from the root with the included-build prefix:
  `./gradlew :POSEIDON:<module>:test` (e.g. `./gradlew :POSEIDON:core:test`). Plain
  `./gradlew :core:test` (as used inside `POSEIDON/`'s own CLAUDE.md) only works when run from
  *inside* `POSEIDON/`, not from the repo root.
- Root's `build.gradle.kts` does **not** enable `-Werror`; that's a POSEIDON-submodule convention
  (`buildlogic.java-common-conventions`) that only applies to code under `POSEIDON/`. Don't assume
  it applies to `src/`.

## Build & test commands

```
./gradlew build                                                     # build root service + POSEIDON, run tests + spotbugs
./gradlew test                                                      # run this repo's unit/integration tests
./gradlew test --tests "eu.project.surimi.poseidon.server.SimulationServiceTest"          # single test class
./gradlew test --tests "eu.project.surimi.poseidon.server.SimulationServiceTest.methodName"  # single test method
./gradlew :POSEIDON:core:test                                       # tests for a POSEIDON submodule (core, io, geography, biology, agents, regulations, calibration, gui, examples)
./gradlew jacocoTestReport                                          # coverage report (build/reports/jacoco/test/…)
./gradlew spotbugsMain                                               # SpotBugs static analysis
./gradlew run --args="-p 50051 -s inputs"                            # run the gRPC server locally (-p port, -s scenario folder)
```

Notes:
- Tests are JUnit 5 (`useJUnitPlatform()`) plus AssertJ, Mockito, jqwik (property-based).
- The `test` task's JVM args are load-bearing, not incidental — don't strip them if touching
  `build.gradle.kts`: a Mockito Java agent (`-javaagent`, `-Xshare:off`) is wired via
  `MockitoAgentArgumentProvider`, and `--enable-native-access=ALL-UNNAMED`,
  `--sun-misc-unsafe-memory-access=allow`, and `-Dio.grpc.netty.shaded.io.netty.noUnsafe=true` are
  needed for protobuf/Netty to work under the JDK 25 module system.
- SpotBugs exclusions live in `spotbugs_exclude.xml` at the repo root.
- Integration tests (`*ServiceTest`) spin up a real in-process Netty gRPC server on a random port
  via `ServiceTest` and connect with a standard `ManagedChannel` — exercising the full interceptor
  chain (exception enrichment → OpenTelemetry → protocol-version trailer → protovalidate
  validation) and (de)serialization stack.

## Scenario generation

`inputs/northwestern_med.yaml` (and the `inputs/northwestern_med/` data alongside it) are
**generated**, not hand-written: `./gradlew writeNorthwesternMedScenario` runs
`uk.ac.ox.poseidon.io.ScenarioWriter` against
`eu.project.surimi.poseidon.scenarios.northwesternmed.NorthwesternMedScenario` to produce the YAML.
The same task also runs as a dependency of `stageForImage` (so the Docker image always embeds a
fresh copy). If the Northwestern Med scenario needs to change, edit `NorthwesternMedScenario.java`
(or the factories it composes) and regenerate — don't patch the `.yaml` directly, **including via a
scripted/sed find-and-replace that looks safe** (e.g. a class rename touching only a handful of
`!!fully.qualified.ClassName` tags). No hand edit is exempt just because it was verified against a
regenerated copy first — the committed file must come from actually running the writer task. If
regenerating surfaces a diff wider than the change you intended (the checked-in YAML had already
drifted from what the current Java produces, for unrelated reasons), stop and tell the user rather
than either hand-patching around the unrelated part or discarding it — that drift is real and its
resolution (regenerate and accept the full diff, in this commit or a separate one) is the user's
call, not something to route around silently.

## Docker image

```
./gradlew stageForImage      # assemble build/image/ (jar + runtime libs + logging.properties + inputs/northwestern_med*)
./gradlew buildDockerImage    # docker build -t ghcr.io/official-ewe/surimiposeidon:latest .
./gradlew pushDockerImage     # docker push — has external blast radius (GHCR), confirm before running
```

Entry point: `eu.project.surimi.poseidon.server.Server` (`application.mainClass` in
`build.gradle.kts`, and the Docker `ENTRYPOINT`). Default gRPC port `50051`; overridable with `-p`.

## Submodules & Git LFS

The repo has two submodules (`.gitmodules`): `POSEIDON/` (framework code, `SURIMI` branch of
`poseidon-fisheries/POSEIDON`) and `inputs/` (scenario data, Git LFS-backed, separate repo because
it's large and versioned independently). After cloning or pulling:

```
git submodule update --init --recursive
```

Seeing `M POSEIDON` or `M inputs` in `git status` is normal — it just means the submodule's checked
-out commit differs from what the superproject records; it isn't uncommitted work in this repo. Be
careful with a broad `git add -A`/`git add .` at the root: it will stage a submodule pointer bump
alongside unrelated changes if one happened to move.

## Code layout (`src/main/java/eu/project/surimi/poseidon/`)

- `server/` — gRPC plumbing: `Server` (entry point), `FisheryService` (delegates to handlers),
  `RequestHandler`/`WithSimulationRequestHandler` (uniform error handling; the latter for handlers
  needing a live simulation), `SimulationManager` (Caffeine cache of live `Simulation`s keyed by
  UUID), the interceptors (`ExceptionInterceptor`, `ValidationInterceptor`, `TrailerInterceptor`),
  and one subpackage per gRPC method group (`simulation/`, `ecology/`, `catchprovider/`,
  `regulations/`, `sales/`, `prices/`, `fleet/`, `mappers/`).
- `regulations/` — `TotalAllowableCatchQuotas` + its factory (TAC tracking exposed to POSEIDON's
  regulation engine).
- `calibration/` — `LandingsAccumulator` for calibration support.
- `scenarios/` — `minimal/` (used in tests) and `northwesternmed/` (production scenario + the
  calibration entry point).

Each gRPC method has exactly one `RequestHandler` subclass; when adding a new method, follow that
one-handler-per-method pattern and register it in `FisheryService`.

## Licensing

All source files carry a GNU GPLv3 header (University of Oxford copyright, 2025). Copy the header
from a neighboring file rather than retyping it when creating new files.
