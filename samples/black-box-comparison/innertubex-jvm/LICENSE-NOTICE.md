# License boundary

The adapter is a separate Gradle build and is not included in the eXcor SDK, examples, or application dependency graph. It uses only InnerTubeX's public API; no InnerTubeX implementation code is copied. Building it resolves the GPL-3.0 artifact and creates a linked executable. Treat that combined executable as separately licensed and obtain licensing review before distributing it. The repository's Apache-2.0 license continues to govern the original source files; this note does not authorize distribution of a linked binary.

The adapter has no code copied from InnerTubeX. It uses only the public extraction API documented by the pinned artifact. Treat the adapter and any linked binary as GPL-3.0-covered; do not distribute a linked binary until the repository owner has completed a licensing review. The eXcor project license does not change the license of this subproject or its linked output.

Pinned artifact: `com.github.MetrolistGroup.innertubex:innertubex:v0.11.6`.
