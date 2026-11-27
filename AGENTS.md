# AGENTS.md

Longer topic-specific docs live in `docs/agents/` — read the relevant one before
working in the area it covers:

- `docs/agents/scenario-generation.md` — before changing or regenerating a
  scenario YAML, or reviewing a regenerated one.
- `docs/agents/protocol-and-contract.md` — before touching a gRPC handler, the
  contract, or the protocol version.
- `docs/agents/ewe-models.md` — before touching the `ewe` package or the Anchovy
  Bay scenario.
- `docs/agents/testing.md` — before writing or running service tests.
- `docs/agents/docker-image.md` — before building or pushing the image.

## What this is

SURIMI-POSEIDON is a gRPC service (Java 25) that wraps the **POSEIDON**
agent-based fisheries model and exposes it to the SURIMI controller as the
`FisheryService` gRPC API. This repo is the _service layer_: gRPC handlers,
request/response mapping, TAC-quota tracking, and the concrete scenarios that
get served (Northwestern Mediterranean in production, a minimal scenario for
tests). The simulation framework itself (agents, biology, regulations engine,
YAML scenario (de)serialization, GUI) lives in the `POSEIDON/` submodule.

Read before working here:

- `POSEIDON/AGENTS.md` and the `POSEIDON/docs/agents/` docs it lists — before
  touching code under `POSEIDON/`, and for the Factory/Scenario pattern this
  repo's scenarios and factories also follow.
  `POSEIDON/docs/agents/conceptual-correctness.md` applies here too.
- `POSEIDON_architecture.md` — gRPC method tables, interceptor stack, error
  handling, k8s/env config, CI. It is hand-maintained and lags the code; where
  they disagree, the code wins.

## Composite build

The root project (`src/main`, `src/test`, package `eu.project.surimi.poseidon`)
is a single Gradle project that `includeBuild("POSEIDON")`s the submodule,
substituting `POSEIDON:<module>` coordinates for its subprojects (see
`settings.gradle.kts`). Consequences:

- Root tests run **without a module prefix**: `./gradlew test`.
- Submodule tests need the included-build prefix from the root:
  `./gradlew :POSEIDON:core:test`. The `./gradlew :core:test` form in
  `POSEIDON/AGENTS.md` only works from inside `POSEIDON/`.
- `./gradlew :POSEIDON:build` from the root runs the submodule's tests but not
  its SpotBugs, Checkstyle or Spotless checks. For the full check, run
  `./gradlew build` from inside `POSEIDON/`; a module's
  `./gradlew :POSEIDON:<module>:build` from the root runs its SpotBugs and
  Checkstyle but not Spotless.

## Commands

```
./gradlew build                                          # root + POSEIDON: compile, tests, spotbugs, checkstyle
./gradlew test                                           # root tests (~3 min, mostly NW Med runs)
./gradlew test --tests "eu.project.surimi.poseidon.server.SimulationServiceTest"            # one class
./gradlew :POSEIDON:<module>:test                        # a submodule's tests (core, io, agents, …)
./gradlew spotlessApply                                  # format src/ and Markdown with Prettier, remove unused imports, add missing license headers
./gradlew :POSEIDON:spotlessApply                        # the same for the submodule
./gradlew writeNorthwesternMedScenario                   # regenerate scenarios/northwestern_med.yaml
./gradlew writeAnchovyBayScenario                        # regenerate scenarios/anchovy_bay.yaml
./gradlew run --args="-p 50051"                          # run the server locally
./gradlew runNorthwesternMedGui                          # open the NW Med GUI (local inputs)
./gradlew runAnchovyBayGui                               # open the Anchovy Bay GUI (local only, not served)
./gradlew runNorthwesternMedCalibration                  # calibrate NW Med (local inputs, heap 75% of RAM)
./gradlew runNorthwesternMedCalibration --args="--population 40"  # size the population to the machine
```

## Code style

Follow `POSEIDON/docs/agents/code-style.md`; its scenario-class conventions
apply to `NorthwesternMedScenario` and `AnchovyBayScenario`. Run
`./gradlew spotlessApply` after editing Java or Markdown. Checkstyle reads the
submodule's `POSEIDON/config/checkstyle/checkstyle.xml`; the formatting setup
and the `checkstyle` version are duplicated in `POSEIDON/`, so change both repos
together.

## Submodules

Two submodules (`.gitmodules`), each its own repo:

- `POSEIDON/` — framework code, `main` branch of `poseidon-fisheries/POSEIDON`.
- `inputs/` — scenario data (`SURIMI-Project/SURIMI-POSEIDON_inputs`, `master`
  branch); large files are in Git LFS.

After cloning or pulling: `git submodule update --init --recursive`.

- `M POSEIDON` / `M inputs` in `git status` means the submodule's checked-out
  commit differs from the recorded pointer, not uncommitted work here. Stage
  files by path so an unrelated pointer bump doesn't ride along.
- A change that touches a submodule is two commits: one inside the submodule,
  then a pointer bump here. Push the submodule commit before pushing a
  superproject commit that points to it.

## Merging and licensing

Merging (fast-forward only) and license headers follow `POSEIDON/AGENTS.md`.
