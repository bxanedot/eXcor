# Historical implementation

`modules/cleanroom/` is the active clean-room library. The earlier implementation and its old EJS bundle are retained under `modules/legacy/src/` and `modules/legacy/ejs/` for provenance review. The root Gradle build does not compile these files, and they are not included in published artifacts. Historical service captures and their handling notes are under `modules/legacy/fixtures/`; synthetic inputs for the active implementation are under `modules/cleanroom/fixtures/`.
