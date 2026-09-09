# JVM Downgrader integration handoff

## Current state

The scoped integration is implemented on `algent/java`, building on baseline `65d0cd0`. The three regression fixes
are preserved. `build.gradle` adds two tasks; `StackAnalyserLogic.visitInsn` uses the validated Java 21 pattern switch,
`JavaTraitRegistration` and `ClassInfoLookup` use Java 21 pattern variables, and `ScalaSignatureParser` uses switch
expressions. The nine Scala sources, Scala 2.11.5 dependency, source layout, and normal Gradle entry points remain in
place. Production tasks do not read a frozen jar or any files under `run/jvmdg-trial/`.

The subsequent `StackAnalyser` initializer extraction is recorded in `JAVA_MIGRATION_HANDOFF.md`. The exact-byte
comparisons and frozen-version reproduction below describe checkpoint `5f0e329`; later helper edits need their own
reference comparisons. The current extraction's evidence is in `run/migration-stack-initialization-reference/`.

## Build arrangement

1. Normal Java and joint Scala/Java compilation remain on Java 8. `compileScala` excludes the modern helpers from
   javac's inputs, while scalac resolves their declarations through `-sourcepath`. This handles the circular Scala
   references without asking Java 8 javac to compile the modern method bodies.
2. `compileModernJava` uses JDK 25 with `--release 21`, against the fresh Java and Scala output directories.
3. `downgradeModernJava` converts the helpers to Java 8. Only the downgraded directory joins the main class outputs,
   so tests, Forge, dev/reobfuscated jars, and downstream compilation receive Java 8 bytecode.

The source-path declaration is an explicit Scala-task input. The existing `scalaCompileOptions.force = true` guard
remains to prevent stale joint-compiled Java annotations after generated `Tags.VERSION` changes.

The per-file include/exclude pairs are temporary compiler routing. These Java files still live in the Scala source set,
whose joint compiler would otherwise send them to Java 8 javac. Once the retained Scala/Java dependency cycle is gone,
move the modern cohort behind one source-set or directory boundary; once Scala is gone, compile all Java with the
modern toolchain and remove the exclusions, `-sourcepath` bridge and force guard together.

The bundled `DowngradeFiles` task initially declares outputs only for inputs that already exist during configuration.
`outputs.dirs(outputMap.values())` explicitly declares the directory for clean builds. Raw and downgraded output use
separate directories and normal task dependencies. No stub sources or replacement Scala compiler are needed.

## Modern Java policy and eligibility audit

The consumer-migration checkpoint should follow a bounded modernization pass. The supported runtime goal is the
preferred Java 25 build (2.9.0 beta 3 also supports Java 17 through 26) plus the extended-support Java 8
build. Source modernization is for clearer, safer implementation code; the downgraded artifact remains Java 8
bytecode, so newer syntax alone is not a performance feature.

Use Java 21 as the source ceiling. JVM Downgrader 1.3.5 can accept Java 22 bytecode, but this build's validated
`compileModernJava` task uses `--release 21`, and Java 22 does not add enough here to justify moving the boundary.

Apply these rules:

- Prefer modern syntax in implementation bodies when it removes casts, duplicated branches or error-prone control
  flow. Do not rewrite working code solely for style.
- Keep public descriptors, generic signatures and Scala-facing declarations Java-8-shaped. In particular, do not put
  records, sealed declarations or modern JDK types on consumer or retained-Scala boundaries.
- Continue using Java 8 library APIs unless a newer API has a concrete benefit and its JVM Downgrader stub/shading and
  runtime-provider requirements are deliberately accepted and tested.
- Add source files to the modern task in small independently reviewable batches. Each batch must compare ABI, class
  inventory, generated ASM output and relevant behavior before accepting compiler-induced bytecode changes.
- Before marking the branch ready for consumer migration, verify all packaged classes are version 52, no unexpected
  JVM Downgrader API references exist, and run the JVM and Forge suites on Java 8 plus packaged smoke tests on the
  preferred modern runtime.

### Eligibility result

A disposable clean-build experiment excluded all 224 Java files under `src/main/scala` from joint compilation and
compiled them together through the Java 21 task. All nine retained Scala sources resolved their declarations, all 224
Java sources compiled, JVM Downgrader completed, and packaging/checkstyle completed. This establishes broad technical
eligibility for Java 21 *method bodies*; it does not establish bytecode or behavior equivalence.

The experiment reached 575 passing tests out of 576. The remaining
`MultipartMixinFactoryCharacterizationTest.namesAndFillsThePassThroughTrait` check reported a missing standalone `T`
string constant after the newer javac/JVM Downgrader string-concatenation shape changed. That is exactly why moving all
sources at once is rejected: even source-identical classes can acquire observable bytecode differences that require
individual review. The normal production configuration remains unchanged.

Recommended order:

1. Start with package-private helpers called directly by retained Scala. `JavaTraitRegistration.java` and
   `ClassInfoLookup.java` are completed follow-ups; pattern variables remove their checked casts without changing the
   Scala-facing declarations. `StackAnalyserLogic.java` remains the original proven example.
2. Continue only where modern syntax produces a concrete control-flow gain. `ScalaSignatureParser.java` is complete:
   its two result-producing switches are now switch expressions. Prefer simplifying the remaining opcode switches in
   the already-modern `StackAnalyserLogic.java` before adding another build exclusion. `ScalaTraitRegistration.java`
   should not move merely to restyle its erased `Some` checks.
3. Consider public core implementations such as `RedstoneInteractions$.java`, `TileMultipart.java` and the registries
   only after the internal batches. Modern method bodies are technically possible, but their published ABI and frozen
   behavior make the review cost higher.
4. Defer registered Java trait inputs under `scalatraits/`. Their transformer forbids or rewrites several bytecode
   shapes, including inner classes, lambdas, string switches and primitive-array allocation; syntax changes need
   transformer-specific fixtures rather than ordinary compilation success.
5. Defer the six `src/main/java` bootstrap/API sources and the separate `src/mixin/java` source. They are not currently
   outputs of `downgradeModernJava`, and the simple API declarations have little modernization value. The mixin source
   needs an explicit downgraded-output arrangement before using post-Java-8 syntax.
6. Keep Scala companions, `$class` compatibility helpers and other descriptor-preservation facades conservative unless
   a specific method body warrants the compiler move. Their binary shape is more valuable than stylistic uniformity.

`RenderPartResolver.java` was initially shortlisted because it is new and package-private in practice, but an isolated
clean trial proved it cannot move alone: joint-compiled `MultipartRenderer$.java` calls it before the modern task runs.
Moving that caller pulls in the public renderer and client-proxy chain. Defer this group until source-layout cleanup can
move the dependency closure without turning one syntax improvement into a broad compiler migration.

The completed `JavaTraitRegistration` batch passes a clean build with all 576 JVM tests and the Java 8 Forge run with
all 289 functional tests. All 450 dev-jar classes remain version 52, the jar has no JVM Downgrader runtime API
references, and all 134 generated ASM dump names and hashes match the pre-change manifest. The helper is absent from
joint output and present only in the raw/downgraded modern directories. Its own class bytes changed as expected under
modern javac/JVM Downgrader, but no source signature or consumer-facing class is changed.

The completed `ClassInfoLookup` batch has the same clean-build, 576-test, Java 8 Forge, 450-class/version-52 and
134-dump results. Its source signatures and package-private visibility are unchanged. JVM Downgrader records nest
metadata as annotations for this helper's anonymous callbacks, but the packaged classes contain no executable JVM
Downgrader API reference and the Java 8 runtime needs no added dependency.

The completed `ScalaSignatureParser` batch also passes the clean compiler boundary, all 576 JVM tests, all 289 Java 8
Forge tests and the 134-dump comparison; all 450 packaged classes remain version 52. Its non-private ABI is unchanged
and it has no executable JVM Downgrader API reference. Modern string concatenation in the same internal class lowers
to six private helper methods; these are compiler implementation details, not consumer entry points.

### fastutil audit

GTNHLib 0.11.44 currently contributes `it.unimi.dsi:fastutil:8.5.18` to `compileClasspath`. It does **not** contribute
fastutil to this project's declared `runtimeClasspath`, because GTNHLib is `compileOnly`. Using fastutil from ordinary
FMP code would therefore turn the optional GTNHLib relationship into an undeclared runtime requirement. Do not do that
implicitly; require GTNHLib/fastutil explicitly first if a measured core use justifies it.

The present candidates do not justify that change:

- `JInventoryTile` could replace its temporary `List<Integer>` with `IntArrayList`, but slot lists are small, the code
  is a transformer-sensitive registered trait, and the dependency would cost more than the avoided boxing.
- `MultiPartRegistry.nameMap` and `MicroMaterialRegistry.nameMap` could use `Object2IntOpenHashMap`, but they are small,
  lifecycle-built registries. Their missing-key behavior is also deliberately characterized. Keep `HashMap`.
- `ControlKeyModifer.map` could use a reference-to-boolean map, but it is publicly exposed as a live `Map` and normally
  contains only the connected players. Keep `HashMap`.
- `PacketScheduler` is the only plausible future candidate for an object-to-long map because it accumulates masks and
  boxes `long` values. Its collection is private, so a later implementation change can preserve the existing consumer
  ABI. Its Scala-map iteration and callback-mutation behavior are intentionally preserved. Defer this completely from
  the Java conversion project; revisit only as separately profiled optimization work with a dedicated semantic test
  and an explicit runtime dependency decision.
- `TileCache` could only become primitive-keyed by replacing `BlockCoord` with an encoded coordinate. Its live map is
  published and its recovery semantics are characterized, so that would be an API/behavior redesign rather than a
  fastutil substitution.

Fastutil remains appropriate for a future GTNHLib-gated implementation with a genuinely large or hot primitive
collection, or after FMP deliberately makes it a direct runtime dependency. Its transitive compile availability alone
is not sufficient, and fastutil adoption is out of scope for the Java conversion project.

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

Prefer modern syntax where it improves readability and the compilation path supports it, as established by
`5f0e329b`. This can accompany consumer-facing API work without waiting for complete Scala removal. Expand the
scoped path selectively; if parsing, compilation order, ABI or downgrade support blocks a source unit, retain its
working syntax and record the blocker and revisit condition. Do not add fragile workarounds solely for syntax.

The production source tree now contains 231 Java files and nine Scala files / 782 nonblank Scala lines. Of the 224 Java
sources in the Scala source tree, only `StackAnalyserLogic`, `JavaTraitRegistration`, `ClassInfoLookup` and
`ScalaSignatureParser` bypass joint compilation. Retained models, trait
metadata, synthetic super accessors, and downstream Scala consumers prevent treating the last nine files as a
mechanical deletion queue. Modern GTNH runtime support does not remove the retained Scala compiler's Java 8
requirement. The main migration plan and working handoff carry the current API/adoption priorities and source counts.

Keep `enableModernJavaSyntax = false`. The global GTNHGradle setting still moves Scala 2.11.5 onto Java 25; the new
helper stage selects its modern compiler explicitly. This supports modern method bodies with declarations that the
old Scala parser understands. Records, sealed declarations, modern API types at Scala boundaries, and references
from joint-compiled Java into the later modern stage need separate compatibility work before expanding the scope.
Modern APIs that downgrade to runtime stubs also require a deliberate runtime-provider decision.

At checkpoint `5f0e329b`, the installed GTNHGradle 2.0.24 build classloader used JVM Downgrader engine/plugin **1.3.5**.
The earlier **1.3.6** number identifies the API dependency configured by global mode, not the engine observed in that
build. The integrated helper's exact match with the original prototype confirmed that its transformation was preserved.

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
