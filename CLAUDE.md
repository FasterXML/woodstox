# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Behavioral

1. Don’t assume. Don’t hide confusion. Surface tradeoffs.
2. Minimum code that solves the problem. Limit speculative additions.
3. Touch only what you must, clean up only your own mess -- but do suggest additional related fixes.
4. Define success criteria. Loop until verified.

## Project Overview

Woodstox is a high-performance Java XML processor implementing the StAX (Streaming API for XML, `javax.xml.stream`), SAX, and extended Stax2 APIs. It optionally supports XML Schema and RelaxNG validation via MSV (Multi-Schema Validator). The library targets Java 8+ and produces an OSGi bundle JAR with shaded MSV dependencies.

Maven coordinates: `com.fasterxml.woodstox:woodstox-core` (Java package root is the legacy `com.ctc.wstx`).

## Build Commands

```bash
./mvnw clean install              # Build and run tests
./mvnw clean install -DskipTests  # Build without tests
./mvnw test                       # Run all tests
./mvnw test -Dtest=TestClassName  # Run a single test class
./mvnw test -Dtest=TestClassName#methodName  # Run a single test method
```

CI (`.github/workflows/main.yml`) tests against Java 8, 17, and 21 using `./mvnw -B -q -ff -ntp verify`; the Java 8 run is the "release build" that also produces JaCoCo coverage. There is no separate lint step.

Note that `package` runs shading + moditect, so `install`/`verify` exercise more than `test` does — a change that compiles and tests fine can still break the bundle/module step.

## Architecture

All main source is under `src/main/java/com/ctc/wstx/`.

### Reading pipeline

`WstxInputFactory` (in `stax/`) is the entry point; all of its `createXMLStreamReader`/`createXMLEventReader` overloads funnel into private `createSR(...)` methods. Those pick a *bootstrapper* (`io/StreamBootstrapper` for byte streams, `io/ReaderBootstrapper` for `Reader`s) which sniffs/validates the XML declaration and encoding, then construct the reader.

The reader class chain is a linear inheritance stack, each layer adding one concern:

```
StreamScanner            (sr/)  low-level tokenizing, entity expansion, input source stack
  └─ BasicStreamReader   (sr/)  the ~5800-line core: StAX event loop and state machine
      └─ TypedStreamReader (sr/) Stax2 typed access (getElementAsInt etc.)
          └─ ValidatingStreamReader (sr/)  validation hookup — this is what gets instantiated
```

Element/namespace/attribute state lives beside the reader in `InputElementStack` and `AttributeCollector` rather than in the reader itself; `InputElementStack` is also where validators are attached (`validateAgainst()`).

`io/` holds the encoding-specific readers (`UTF8Reader`, `UTF32Reader`, `AsciiReader`, `ISOLatinReader`, `EBCDICCodec`) and input-source plumbing (`WstxInputSource`, `BranchingReaderSource` for DTD-internal-subset capture, `DefaultInputResolver` for external entities). Bug fixes for malformed input (overlong UTF-8, out-of-range code points, bad `EncName`) usually land here or in the bootstrappers.

### Writing pipeline

`WstxOutputFactory.createSW(...)` builds a *two-layer* stack, which is the main thing to understand about `sw/`:

1. A low-level `XmlWriter` that owns encoding and character escaping. Which subclass depends on the target encoding: `AsciiXmlWriter`, `ISOLatin1XmlWriter` (both extending `EncodingXmlWriter`, byte-backed) or `BufferingXmlWriter` (char/`Writer`-backed).
2. An `XMLStreamWriter2` implementation on top, chosen by namespace configuration: `NonNsStreamWriter` (no namespaces), `SimpleNsStreamWriter` (namespaces, caller-supplied prefixes), or `RepairingNsStreamWriter` (auto-repairing). The latter two extend `BaseNsStreamWriter`; all extend `TypedStreamWriter` → `BaseStreamWriter`.

A consequence worth remembering: escaping/well-formedness bugs often must be fixed in *both* the byte-backed (`EncodingXmlWriter` subclasses) and char-backed (`BufferingXmlWriter`) writers, and tests should cover both. Output element/namespace state lives in `SimpleOutputElement`/`OutputElementBase`.

### Validation

Validation is pluggable through the Stax2 `XMLValidationSchema` / `XMLValidator` abstraction, so both DTD and MSV-based validation attach to readers and writers the same way.

- `dtd/` (largest package) is a self-contained DTD implementation: `FullDTDReader`/`MinimalDTDReader` parse subsets into a `DTDSubset` of `DTDElement`s and `DTDAttribute` subclasses (one per attribute type); content models compile to DFAs (`DFAState`, `DFAValidator`, `StructValidator`). `DTDValidator`/`DTDValidatorBase` is the runtime validator, `DTDSchemaFactory` the Stax2 entry point.
- `msv/` is a thin bridge to MSV for W3C Schema and RelaxNG: `W3CSchemaFactory`/`RelaxNGSchemaFactory` produce schemas whose validator is `GenericMsvValidator`.

### Configuration

`api/ReaderConfig` and `api/WriterConfig` (both extending `api/CommonConfig`) hold all settings as packed int flag bits, with the constants in `cfg/`. Factories keep a template config and call `createNonShared(...)` per reader/writer, so per-instance property changes don't leak across instances; the same call passes down the shared `SymbolTable` used for name interning. Public property names live in `api/WstxInputProperties` and `api/WstxOutputProperties`.

### Other packages

`evt/` (event API over the cursor API), `sax/` (`WstxSAXParser` adapts the stream reader to SAX), `dom/` (DOM source/result), `ent/` (entity declarations), `exc/`, `util/`, `osgi/`.

## Testing

- **Framework**: JUnit 5 (Jupiter) with Mockito 4.11.0. Migrated from JUnit 4 in 7.2.1 (issue #286); Mockito stays at 4.x because 5.x dropped Java 8 support.
- **Assertions**: tests use a JUnit 4-style, **message-first** static assertion API (e.g. `assertEquals(message, expected, actual)`) provided by the `wstxtest.BaseJUnit4Test` shim over JUnit 5 `Assertions` — argument order is message, then expected, then actual. Use `org.junit.jupiter.api.Test` for `@Test`.
- **Test file patterns**: `*Test.java` and `Test*.java` (Surefire excludes `failing/`, `Abstract*`, `Base*`, and inner classes)
- **Test packages**:
  - `wstxtest/` — Woodstox-specific tests (`stream`, `vstream` for validation, `wstream` for writer, `evt` for events)
  - `stax2/` — Stax2 extended API tests
  - `org/codehaus/stax/test/` — Generic StAX compatibility tests
  - `failing/` — Known failing tests (excluded from runs via `<exclude>failing/*.java</exclude>` in `pom.xml`)
- **Base test classes**: `BaseStax2Test`, `BaseStreamTest` (one each in `wstxtest/stream/` and `org/codehaus/stax/test/stream/`), `BaseVStreamTest`, `BaseEventTest` — provide factory creation methods and test utilities
- **Test resources**: XML, DTD, and schema files in `src/test/resources/`
- **Reproducers for open bugs**: when writing a failing test for a GitHub issue that has no fix yet, place it under `src/test/java/failing/` so it documents the bug without breaking CI. Name it after the issue (e.g., `DTDXmlLang33Test.java` for issue #33) and reference the issue number in the class Javadoc. Once the bug is fixed, move the test out of `failing/` into the appropriate package.

## Release notes

`release-notes/VERSION` is maintained by hand and is part of the change, not an afterthought: every user-visible fix gets an entry under the current unreleased version in the form

```
#311: Reject/fix comment ending in '-' in byte-backed XML writers
 (contributed by @aizu-m)
```

Attribution lines are `(contributed by X)`, `(reported by X)`, or `(fix by X)`. Contributors are also listed in `release-notes/CREDITS`.

## Packaging specifics

Three `package`-phase plugins shape the artifact, and all three are easy to break unintentionally:

- **maven-bundle-plugin** builds the OSGi bundle, exporting `com.ctc.wstx.*`.
- **maven-shade-plugin** relocates MSV and friends into `com.ctc.wstx.shaded.*` (`com.sun.msv` → `com.ctc.wstx.shaded.msv_core`, etc.), partly for deployment simplicity and partly because MSV has no module-info. Because MSV resolves many classes dynamically by name, `minimizeJar` is off and specific `com.sun.msv.driver.textui` classes are excluded by hand.
- **moditect** injects the JPMS module descriptor from `src/moditect/module-info.java` (hand-written, since the build must work on JDK 8). Module name is `com.ctc.wstx` — it differs from the Maven group id. New public packages, or new `XMLInputFactory`/`XMLOutputFactory`/`XMLEventFactory`/`XMLValidationSchemaFactory` service implementations, must be added to that file manually.

## Key Dependencies

- `stax2-api:4.3.0` (mandatory) — Extended StAX API implemented by Woodstox
- `msv-core`, `xsdlib`, `isorelax`, `relaxngDatatype` (optional, MSV `2022.7`) — Validation support, shaded into the output JAR
