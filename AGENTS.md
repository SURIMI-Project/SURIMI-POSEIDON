# AGENTS.md

Guidance for coding agents (Claude Code and others) working in this repository. Longer
topic-specific docs live in `docs/agents/` — read the relevant one before working in the area it
covers:

- `docs/agents/scenario-generation.md` — before changing or regenerating a scenario YAML, or
  reviewing a regenerated one.
- `docs/agents/protocol-and-contract.md` — before touching a gRPC handler, the contract, or the
  protocol version.
- `docs/agents/ewe-models.md` — before touching the `ewe` package or the Anchovy Bay scenario.
- `docs/agents/testing.md` — before writing or running service tests.
- `docs/agents/docker-image.md` — before building or pushing the image.

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
- `./gradlew :POSEIDON:build` from the root runs the submodule's tests but not its SpotBugs,
  Checkstyle or Spotless checks. For the full check, run `./gradlew build` from inside
  `POSEIDON/`; a module's `./gradlew :POSEIDON:<module>:build` from the root runs its SpotBugs and
  Checkstyle but not Spotless.

## Commands

```
./gradlew build                                          # root + POSEIDON: compile, tests, spotbugs, checkstyle
./gradlew test                                           # root tests (~3 min, mostly NW Med runs)
./gradlew test --tests "eu.project.surimi.poseidon.server.SimulationServiceTest"            # one class
./gradlew :POSEIDON:<module>:test                        # a submodule's tests (core, io, agents, …)
./gradlew spotlessApply                                  # format src/ with Prettier, remove unused imports, add missing license headers
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

Follow `POSEIDON/AGENTS.md` ("Code style") and `POSEIDON/docs/agents/code-style.md`, including the
scenario-class conventions, which apply to `NorthwesternMedScenario` and `AnchovyBayScenario`.

`build` runs `spotlessCheck`, which fails on any Java file `spotlessApply` would change: run
`./gradlew spotlessApply` after editing Java. Compilation and Checkstyle follow POSEIDON's rules
(see "Commands" in `POSEIDON/AGENTS.md`): `-Werror -Xlint:all,-serial`, and Checkstyle with
`POSEIDON/config/checkstyle/checkstyle.xml`, which this repo reads from the submodule rather than
keeping a copy. The formatting setup and the `checkstyle` version are duplicated in `POSEIDON/`:
change both repos together (the files are listed in `POSEIDON/docs/agents/code-style.md`).

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

## Merging

Merge into `main` as `POSEIDON/AGENTS.md` ("Merging") says: fast-forward only, never a merge
commit. The same holds in the `POSEIDON` submodule.

## Licensing

Every source file carries the GNU GPLv3 header (University of Oxford copyright). The year-range
rule is in `POSEIDON/AGENTS.md` ("Licensing"): end the year range of every file you modify at the
current year yourself, since `spotlessApply` adds the header to new files but never changes a year.

## Design docs

Design and spec documents (e.g. under `docs/superpowers/specs/`) stay uncommitted: write them and
leave them for the user to commit, even when a skill's process says to commit.
