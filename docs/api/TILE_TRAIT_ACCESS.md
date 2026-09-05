# Calling generated tile capabilities from Java

[API index](../API.md) · [Composite generation](COMPOSITE_GENERATION.md)

Call generated tiles through `TileMultipart` or a stable public capability interface. Several dev-jar classes under
`scalatraits`, and `TileMultipartClient`, are raw inputs to FMP's transformer: Forge changes them into interfaces
before generating a concrete tile. A Java signature that compiles against those classes can still fail at runtime.

## Why raw trait calls fail

These consumer expressions compile against the current dev jar but are unsafe:

```java
((TRedstoneTile) tile).openConnections(side); // javac emits invokevirtual for a class
((TSlottedTile) tile).v_partMap;              // javac emits getfield
```

At runtime `TRedstoneTile` is an interface, so the first call throws `IncompatibleClassChangeError`. `TSlottedTile`
has generated accessor methods instead of that field, so the second throws `NoSuchFieldError`. A cast or an
`instanceof` check alone can still succeed; that does not make subsequent class-method/field bytecode valid.
Merely adding Scala to the compile classpath does not turn these raw Java classes into interface stubs.

Already compiled consumers using the original interface/accessor descriptors remain supported. Recompiling source
against the raw Java port is a separate gate. Do not instantiate or subclass raw trait inputs in consumer code, or
call their generated `$class` helpers as a new integration strategy. Reflection is not the migration target.

## Stable entry points

| Need | Compile against | Contract |
| --- | --- | --- |
| Parts, slot reads, placement, lifecycle | `TileMultipart` | `jPartList()`, `partMap(slot)`, placement and documented notifications dispatch through generated overrides |
| Open redstone geometry | `IRedstoneTile` | `openConnections(side)`; capability is present when the tile hosts a redstone part |
| Offered redstone connections or masked power | `IRedstoneConnector` | `getConnectionMask(side)`, `weakPowerLevel(side, mask)`; also used by non-FMP connectors |
| Ordinary tile power queries | `TileMultipart` | `strongPowerLevel(side)`, `weakPowerLevel(side)`; preserve each method's side convention |
| Inventory or sided inventory | Minecraft `IInventory` / `ISidedInventory` | Use the interface implemented by the tile, rather than raw `JInventoryTile` or its internal caches |
| Fluid operations | Forge `IFluidHandler` | Use the interface rather than raw `TFluidHandlerTile` or `tankList` |
| A consumer-owned pass-through capability | The original ordinary Java interface | Register with `MultipartGenerator.registerPassThroughInterface`; generated calls forward to the implementing part |

Test the capability when it is optional. Keep using the tile returned by placement/removal because adding or removing
capabilities can replace the tile instance. These interfaces do not give a snapshot, reserve a tile, or add thread
safety. Apply the owning method's lifecycle and side rules. Client-only rendering calls remain client-only even when
a stable base method exists; a headless success does not establish physical-client rendering.

## Compiling redstone example

[TileTraitAccessExample.java](../../src/functionalTest/java/codechicken/multipart/examples/TileTraitAccessExample.java)
uses only `TileMultipart` and `IRedstoneTile` and compiles as Java 8 without Scala on the classpath:

```java
boolean open = TileTraitAccessExample.hasOpenConnection(tile, side, mask);
```

The example returns false for a null tile or absent capability. Its caller supplies a Minecraft side in 0..5 and
a mask using bits 0..3 for rotations around that side and bit 4 for the center. `openConnections` describes openings
after face/edge obstruction; `getConnectionMask` describes the connections offered by contained parts. They are not
interchangeable. The API does not validate the side or add a cache.

ProjectRed's `transmission/redwires.scala` already knows its tile supports redstone. Its migration can change only
the cast type, preserving the surrounding mask and rotation calculation:

```scala
tile.asInstanceOf[IRedstoneTile].openConnections(absDir)
```

That emits an interface call with the stable owner. ProjectRed can remain Scala internally. Keep its legacy
`TRedstoneTile.openConnections` binary contract until release and pack adoption.

## Slot mutation and custom extensions still need separate work

`TileMultipart.partMap(slot)` is sufficient for reads. OpenComputers' `PrintPart.toggleState` also clears slots equal
to itself from the live `v_partMap` array, then calls `tile.bindPart(this)`. The read API does not replace that mutation,
and `bindPart` alone does not clear old slots. A whole `loadPartList` reconstruction would run additional lifecycle and
cache work. A focused supported slot-refresh operation, preserving equality and override behavior, is the next API
candidate; do not replace the old integration with reflection or claim that it is already migrated.

For custom generated tiles, keep consumer-callable methods on an ordinary Java capability interface and use the
stable base for existing tile hooks. Register raw trait inputs by name before class loading, during initialization.
Pass-through registration is sufficient when a tile should forward one interface to one part; it rejects a second
implementor through occlusion checks. Writing custom aggregation/lifecycle traits still needs the compiler's
[Java trait restrictions](../../JAVA_MIGRATION_HANDOFF.md#retained-compiler-constraints), including helper placement
and inherited access. The [microblock extension](MICROBLOCK_EXTENSIONS.md) demonstrates those helper constraints,
but is not a complete custom tile-trait authoring example.

No transformed compile-stub artifact is introduced here: the redstone query already has a stable public interface.
Assess remaining trait-only requirements individually before adding build machinery. Custom tile-trait authoring,
OpenComputers slot mutation and other audited private/reflection contracts remain open.

## Validation

[Forge fixtures](../../src/functionalTest/java/codechicken/multipart/test/TileTraitAccessFunctionalTest.java) execute
[actual javac-compiled unsafe callers](../../src/functionalTest/java/codechicken/multipart/test/RawTileTraitCalls.java)
against transformed traits and assert both linkage failures. Stable interface/base calls succeed on the same tiles,
and the example checks every five-bit mask plus absent capabilities. Packaged-jar compilation and bytecode inspection
separately verify that the example uses `invokeinterface` with `IRedstoneTile`, without Scala, reflection or raw
trait-class references. Existing binary APIs and generated executable bodies remain unchanged; two helper dumps
only shift source line numbers after Javadoc additions.

This is consumer source-access coverage. It does not validate physical-client trait selection/rendering, supply all
custom extension examples, or establish that any consumer has released and adopted a migration.
