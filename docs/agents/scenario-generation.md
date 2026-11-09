# Scenario generation

`scenarios/northwestern_med.yaml` is **generated**: `./gradlew writeNorthwesternMedScenario` runs
`uk.ac.ox.poseidon.io.ScenarioWriter` on `NorthwesternMedScenario`, and `stageForImage` runs it too,
so the Docker image always embeds a fresh copy. It lives in this repo, not in the `inputs`
submodule, because it changes with the Java code. The data files in `inputs/northwestern_med/` are
produced upstream by the SURIMI-data_preprocessing pipelines, not by this repo.

The YAML is not meant to be edited by hand, by anyone: the framework does not guard against the
mistakes a hand edit can introduce (a cycle of anchors, say). `./gradlew run` still reads it as is.
For agents, the rule is strict:

To change the scenario, edit `NorthwesternMedScenario.java` (or the factories it composes) and
regenerate, committing the YAML with the Java change. The committed YAML must come from actually
running the writer task — a hand edit or scripted find-and-replace by an agent is never
acceptable, however safe it looks or however well it matches a regenerated copy.

`scenarios/anchovy_bay.yaml` follows the same rules, with `AnchovyBayScenario.java` and
`./gradlew writeAnchovyBayScenario`. It is local only for now: `stageForImage` does not stage it,
and the server does not serve it.

## Scenario packages

Under `src/main/java/eu/project/surimi/poseidon/scenarios/`: `minimal/` holds the scenario the
tests use, `northwesternmed/` the production scenario (with its UI and calibration entry point), and
`anchovybay/` the scenario built from an EwE model (local only, with a UI).

## Reviewing a regenerated YAML

One added or removed anchor renumbers every later `&idNNN` / `*idNNN`, so the raw diff looks huge.
Normalise before reading it:

```
diff <(git show HEAD:scenarios/northwestern_med.yaml | sed -E 's/id[0-9]+/idN/g') \
     <(sed -E 's/id[0-9]+/idN/g' scenarios/northwestern_med.yaml)
```

If the regenerated diff is wider than your change (the committed YAML had drifted from what the
Java produces), stop and tell the user; how to resolve the drift is their call. One clean way to
separate the drift from your own change: stash your Java changes, regenerate and commit the drift
alone, then unstash, regenerate and commit your change — each commit then comes from a real writer
run.
