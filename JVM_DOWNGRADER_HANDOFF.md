# JVM Downgrader integration handoff

## Current state

The scoped integration is implemented on `algent/java`, building on baseline `65d0cd0`. The three regression fixes
are preserved. `build.gradle` adds two tasks; `StackAnalyserLogic.visitInsn` uses the validated Java 21 pattern switch.
The nine Scala sources, Scala 2.11.5 dependency, source layout, and normal Gradle entry points remain in place.
Production tasks do not read a frozen jar or any files under `run/jvmdg-trial/`.

The subsequent `StackAnalyser` initializer extraction is recorded in `JAVA_MIGRATION_HANDOFF.md`. The exact-byte
comparisons and frozen-version reproduction below describe checkpoint `5f0e329`; later helper edits need their own
reference comparisons. The current extraction's evidence is in `run/migration-stack-initialization-reference/`.

## Build arrangement

1. Normal Java and joint Scala/Java compilation remain on Java 8. `compileScala` excludes the helper from javac's
   inputs, while scalac resolves its declaration through `-sourcepath`. This handles the circular Scala references
   without asking Java 8 javac to compile the modern method body.
2. `compileModernJava` uses JDK 25 with `--release 21`, against the fresh Java and Scala output directories.
3. `downgradeModernJava` converts the helper to Java 8. Only the downgraded directory joins the main class outputs,
   so tests, Forge, dev/reobfuscated jars, and downstream compilation receive Java 8 bytecode.

The source-path declaration is an explicit Scala-task input. The existing `scalaCompileOptions.force = true` guard
remains to prevent stale joint-compiled Java annotations after generated `Tags.VERSION` changes.

The bundled `DowngradeFiles` task initially declares outputs only for inputs that already exist during configuration.
`outputs.dirs(outputMap.values())` explicitly declares the directory for clean builds. Raw and downgraded output use
separate directories and normal task dependencies. No stub sources or replacement Scala compiler are needed.

## Evidence

The actual production patch passes normal and clean builds with 398 freshly compiled JVM tests, 398 frozen JVM
consumer tests, and 237 Java 8 Forge tests, with zero failures/errors/skips. Forge's nested build includes both new
tasks. Spotless and checkstyle pass.

All 445 dev-jar classes remain version 52. With matching version metadata, only `StackAnalyserLogic.class` changes;
all retained Scala classes, ScalaSignature payloads, bridges, models, and other classes are byte-for-byte identical.
The helper itself exactly matches the isolated prototype. All 116 generated ASM dump names and hashes match.
The helper has no JVM Downgrader runtime-stub references, and no runtime dependency was added.
The release jar also contains 445 Java 8 classes, and the sources jar contains the exact modern helper source.

Evidence and runnable checks are under ignored `run/jvmdg-trial/`:

- `production-candidate.log`, `production-clean.log`: actual build/Forge verification.
- `final-normal-build.log`: ordinary build and toolchain inventory without the frozen-version override.
- `integrated-comparison.json`, `verify-integration.ps1`: bytecode, packaging, dump, and test-count checks.
- `frozen-consumers.gradle`: tests using the original compiled JVM consumers.
- `src/`, `reference/`, `artifacts/`: original prototype, frozen baseline, and experiment jars.
- `artifacts/integrated/`, `integrated-test-results/`, `integrated-forge-test-results/`: preserved clean-build evidence.
- `production-integration.patch`: the production changes captured for review.
- `initial-root-handoff.md`: the original handoff before this takeover.

## Limits

Sequence the remaining work as behavior extraction, compilation-boundary cleanup, then selective modern-syntax
changes. The bulk is already Java: 224 Java files and nine Scala files / 782 nonblank Scala lines. Of the 221 Java
sources in the Scala source tree, only this helper bypasses joint compilation. Retained models, trait metadata,
synthetic super accessors, and downstream Scala consumers prevent treating the last nine files as a mechanical
deletion queue. Modern GTNH runtime support does not remove the retained Scala compiler's Java 8 requirement.

Keep `enableModernJavaSyntax = false`. The global GTNHGradle setting still moves Scala 2.11.5 onto Java 25; the new
helper stage selects its modern compiler explicitly. This supports modern method bodies with declarations that the
old Scala parser understands. Records, sealed declarations, modern API types at Scala boundaries, and references
from joint-compiled Java into the later modern stage need separate compatibility work before expanding the scope.
Modern APIs that downgrade to runtime stubs also require a deliberate runtime-provider decision.

The installed GTNHGradle 2.0.24 build classloader uses JVM Downgrader engine/plugin **1.3.5**. The earlier **1.3.6**
number identifies the API dependency configured by global mode, not the engine observed in this build. The integrated
helper's exact match with the original prototype confirms that its transformation is preserved.

## Reproduce

Normal commands require no init script or special property:

```powershell
.\gradlew.bat --offline build
.\gradlew.bat --offline runFunctionalTestServer
```

For exact frozen-consumer comparison, use the original version string: two frozen tests inlined it. A branch-derived
version otherwise fails those two version assertions. Do not change or recompile the frozen tests to conceal this.

```powershell
$previousTrialVersion = $env:VERSION
try {
    $env:VERSION = '1.7.12-git.225+65d0cd0090-dirty'
    .\gradlew.bat --stop
    .\gradlew.bat --offline -I run/jvmdg-trial/frozen-consumers.gradle clean build jvmdgIntegrationFrozenTest runFunctionalTestServer
    .\run\jvmdg-trial\verify-integration.ps1
} finally {
    $env:VERSION = $previousTrialVersion
}
```

`sourcepath-probe.gradle` and `integration.gradle` are historical investigation harnesses. The latter duplicates the
adopted task names and must not be loaded alongside the production integration. An init-script-only trial also does
not automatically reach `runFunctionalTestServer`'s nested build; the production patch does.
