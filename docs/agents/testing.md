# Testing

- JUnit 5, AssertJ, Mockito, jqwik. Assertions follow the AssertJ rules in `POSEIDON/AGENTS.md`
  ("Build & test commands").
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
- Two simulations of the same scenario are not reproducible against each other unless they share
  a seed (`startNewSimulation(seed)`; with no seed, the current time in milliseconds). With
  the same seed, they are, in one JVM or across JVMs, as long as model code never iterates a
  collection in identity-hash order (POSEIDON `docs/agents/architecture.md`, "Determinism");
  `ScenarioReproducibilityTest` checks it. Otherwise, compare values within one simulation, not
  across runs.
- SpotBugs: suppress a one-off false positive in place with
  `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "...", justification = "...")` (see
  `WithSimulationRequestHandler.java`). `spotbugs_exclude.xml` is for broad, structural exclusions
  only.
