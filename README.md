ForgeMultipart
==============

An API for dynamically handling different functional parts in the one block space.

Requirements:
* The latest version of CCL (files.minecraftforge.net/CodeChickenLib/)
* Forge
* JDK 8 and JDK 25. The mod targets Java 8, but a small set of helpers is compiled at Java 21 by the
  `compileModernJava` task and downgraded to Java 8, so both toolchains must be resolvable to build.
* A scala compatible IDE and project (For eclipse, download the scala eclipse plugin and right click on the project -> Add Scala Nature).
  Most of the codebase is Java now; the Java sources live under `src/main/scala` so they can be joint-compiled
  with the Scala files that remain.

If you only want to use the API and not modify it:
 * Download the latest dev version from files.minecraftforge.net/ForgeMultipart/
 * Download the latest dev version of CCL.
 * Place both mods in your /libs/ folder and link them as libraries in your IDE.

Implementation of all parts of the API should be clean and easy in Java. The minecraft multipart mod is implemented in java as a test case.

See the [Java API index](docs/API.md) for supported entry points, compiling examples, ownership/lifecycle contracts
and migration status. It links to factory registration, material enumeration, part traversal, tile loading and occlusion guides.
