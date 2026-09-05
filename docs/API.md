# ForgeMultipart Java API

Start here when integrating with ForgeMultipart for **Minecraft 1.7.10 / Forge**. This describes the `algent/java`
migration branch. Its new APIs are implemented and tested but do not yet have a minimum published dependency version.
Use a dev artifact containing the methods you need; source Javadocs are packaged with the sources artifact.

Consumers can use the Java surface without importing Scala types. FMP still retains Scala storage, runtime and
compatibility bridges, and some extension contracts still need migration. A Scala-authored mod can adopt the Java API
without converting the rest of its code to Java.

## Direct calls and optional integration

The intended consumer API uses direct typed public calls, without reflection, private-field mixins or Scala
companion lookup. Reuse existing entry points where they cover the task. For optional support, isolate FMP-typed
code in a compatibility class loaded only after mod-presence and supported-version checks; test absent and present
mod loading. Reflection snippets in older guides are legacy interoperability options, not the migration target.
Existing reflective binaries remain supported until consumer release and pack adoption.

## Choose a guide

| Task | API and guide |
| --- | --- |
| Register part factories during mod initialization | `registerPartFactory(IPartFactory2, String...)` — [factory timing, payload ownership and migration](api/PART_REGISTRATION.md) |
| Find the factory registered for a part type | `getPartFactory(String)` — [lookup ownership and Schematica reflection migration](api/FACTORY_LOOKUP.md) |
| Enumerate microblock materials by numeric ID | `materialCount()`, `materialName(int)`, `getMaterial(int)` — [material enumeration](api/MATERIAL_ENUMERATION.md) |
| Read a microblock material's block and metadata | `BlockMicroMaterial.block()`, `meta()` — [typed GuideNH query, identity and overrides](api/MATERIAL_ACCESS.md) |
| Create a microblock with the requested material and side | `MicroblockGenerator.create(MicroblockClass, int, boolean)` — [construction, material traits and GuideNH migration](api/MICROBLOCK_CREATION.md) |
| Read/index/search a tile's parts | `jPartList()` — [part collection ownership and order](api/PART_TRAVERSAL.md#collection-ownership-and-ordering) |
| Run callbacks while skipping detached parts | `forEachPart(Consumer)` — [callback and override behavior](api/PART_TRAVERSAL.md#callback-behavior) |
| Rebuild parts on an already prepared composite tile | `loadPartList(Collection)` — [part loading](api/PART_LOADING.md) |
| Select client/server tile capabilities before preparing state and loading | `MultipartGenerator.generateCompositeTile(TileEntity, Iterable, boolean)` — [staged generation and reflection migration](api/COMPOSITE_GENERATION.md) |
| Assign stored parts during reconstruction, without binding or notifications | `setPartList(List)` — [storage assignment](api/PART_LOADING.md#storage-assignment) |
| Test a candidate against a selected collection of parts | `testOcclusion(Collection, candidate)` — [occlusion queries and generated hooks](api/OCCLUSION.md) |
| Test two groups of bounding boxes directly | `NormalOcclusionTest.testBoxes(Iterable, Iterable)` — [box-versus-box queries](api/OCCLUSION.md#box-versus-box-queries) |
| Read FMP's global render registration ID | `TileMultipart.getRenderID()` — [render-ID meaning, lifecycle and setter](api/RENDER_ID.md) |
| Inspect an existing tile or converted placeholder with a named result | `getOrConvertTileResult(World, BlockCoord)` — [conversion outcomes and placement lifecycle](api/TILE_CONVERSION.md) |

Each guide explains ownership, lifecycle, legacy replacements and limitations, and links to a compiling Java example
exercised by the test suite. The loading/setter APIs are advanced reconstruction operations; ordinary placement and
removal should use the existing world APIs below.

## Existing Java entry points

These entry points already exist. The links lead to their source/Javadocs; detailed migration guides and checks for
compilation without Scala are still pending in these areas. A Java-typed overload can still require Scala on the
compile classpath for overload resolution, as the [loading guide](api/PART_LOADING.md#overrides-and-reflection) explains.

| Area | Starting points |
| --- | --- |
| Define a custom part | Extend [TMultiPart](../src/main/scala/codechicken/multipart/TMultiPart.java); implement the required capability interfaces. The [built-in parts](../src/main/scala/codechicken/multipart/minecraft) show Java implementations |
| Register block converters | [MultiPartRegistry](../src/main/scala/codechicken/multipart/MultiPartRegistry.java): `registerConverter(IPartConverter)`; part factories have a [separate Java guide](api/PART_REGISTRATION.md) |
| Find, place and remove parts | [TileMultipart](../src/main/scala/codechicken/multipart/TileMultipart.java): `getTile`, `canPlacePart`, `addPart`, `remPart`. Retain the tile returned by changes because generated capabilities can replace the instance |
| Construct a server composite tile from parts, or restore saved multipart NBT | [MultipartHelper](../src/main/scala/codechicken/multipart/MultipartHelper.java): `createTileFromParts(Iterable)`, `createTileFromNBT(World, NBTTagCompound)` |
| Register microblock materials | [MicroMaterialRegistry](../src/main/scala/codechicken/microblock/MicroMaterialRegistry.java) and [BlockMicroMaterial](../src/main/scala/codechicken/microblock/BlockMicroMaterial.java) |
| Register generated tile traits or pass-through interfaces | [MultipartGenerator](../src/main/scala/codechicken/multipart/MultipartGenerator.java); see the [extension constraints](../JAVA_MIGRATION_HANDOFF.md#retained-compiler-constraints) before migrating custom traits |

New API examples use Java 8 language/library features. The source path `src/main/scala` also contains Java classes
because of the current joint-compilation layout; it does not imply that those APIs require Scala source in a consumer.

## Compatibility and remaining work

Deprecated Scala-facing entry points remain callable, with their descriptors and supported override dispatch retained.
Follow the method-specific guide: a Java sibling is not automatically a replacement override hook, and reflection
must select the intended parameter types when a method is overloaded.

Converter documentation, remaining generator/reflection replacements
and complete external microblock extension guidance are still pending. In particular, ProjectRed's Scala microblock traits
remain a supported dependency; registration signatures alone do not prove a complete Java replacement.

All ten entries in the plan's Phase 9.1 API table have Java replacements. That table is a bounded list of signatures;
the broader API, extension and consumer adoption work above remains open.

FMP-side implementation is separate from consumer releases and target-pack adoption. Retiring legacy bridges or the
Scala dependency requires those gates and removal of FMP's remaining internal Scala users.

| Need | Document |
| --- | --- |
| What is complete and what comes next? | [Working handoff](../JAVA_MIGRATION_HANDOFF.md), [migration plan](../JAVA_MIGRATION.md) |
| Which consumers need changes, releases and pack adoption? | [Consumer adoption ledger](../JAVA_MIGRATION_CONSUMER_AUDIT.md#java-api-adoption-ledger) |
| Which binary names, reflective lookups and runtime contracts must survive? | [ABI inventory](../JAVA_MIGRATION_ABI_INVENTORY.md), [source consumer audit](../JAVA_MIGRATION_CONSUMER_AUDIT.md) |
| What has been tested and what still needs a client/pack run? | [Migration history](migration/HISTORY.md), [manual release checks](../JAVA_MIGRATION_MANUAL_CHECKS.md) |
| Why is some Scala or older Java syntax retained? | [Modern Java policy](../JAVA_MIGRATION.md#modern-java-readability-policy), [compiler/toolchain handoff](../JVM_DOWNGRADER_HANDOFF.md) |
