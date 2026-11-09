# Docker image

```
./gradlew stageForImage      # build/image/: jar, runtime libs, logging.properties, scenarios/northwestern_med.yaml
./gradlew buildDockerImage    # docker build, tagged :latest (and :<version> with -PdockerImageVersion=<version>)
./gradlew pushDockerImage     # docker push of those tags to GHCR — confirm with the user before running
```

CI already rebuilds and pushes the image: on every push to `main`, though not on pull requests,
the Java CI workflow (`.github/workflows/gradle.yml`) computes a version with GitVersion, tags the
commit `v<version>`, then runs `pushDockerImage -PdockerImageVersion=<version>`. A merge to `main`
therefore publishes a new `:latest` and `:<version>` with no manual push. Never pass a hand-picked
`-PdockerImageVersion` to a local push: version numbers belong to GitVersion.

Entry point: `eu.project.surimi.poseidon.server.Server`. Default port `50051` (`-p`), scenario
folder `scenarios` (`-s`). The image has no input data: with `AWS_BUCKET_NAME` set, `S3Inputs`
downloads it into `inputs/` at startup (see `POSEIDON_architecture.md`, "S3 bucket").
