# Bulk `System.arraycopy` optimization for `readTextSecondary`

This branch applies a **Java-8-compatible** optimization to
`BasicStreamReader.readTextSecondary` (the character-data *copying* path, used
whenever text contains entities/character references or needs LF conversion):
instead of copying text one character at a time, it finds the next character
that needs handling (`firstSpecialTextChar`, a plain scalar scan) and bulk-copies
the intervening run of "pure" text with a single `System.arraycopy`.

No Vector API, no `jdk.incubator.vector`, no `pom.xml` / Java-version changes —
this compiles and runs on Java 8. `readTextPrimary` (the zero-copy buffer-sharing
path) is untouched.

> This is the portable half of a larger SIMD experiment (branch
> `simd-character-scanning`). That experiment showed the entity/copy-path
> speedup came almost entirely from this bulk copy — a scalar win — while the
> Vector-API scan only helped long delimiter-free runs. This branch keeps just
> the broadly-applicable scalar part.

## Correctness (differential vs master baseline)

The optimized `readTextSecondary` was proven **behavior-preserving** by two
independent differential harnesses (optimized build vs master baseline, compared
event-for-event):

- **Structured battery** — 9 tricky documents × {coalescing on/off} × {default,
  61, 128, 512, 4096}-char buffers = 90 configs: **byte-identical** output
  (matching MD5), including entities, `&#x1F600;` surrogates, mixed CR/CRLF/LF,
  CDATA pseudo-terminators, high Unicode, and identical exceptions for the
  malformed cases (`]]>` in text, control char `0x01`).
- **Randomized fuzz** — 3000 random documents, 274,496 events, across buffer
  sizes {31,61,128,509,4096} and both coalescing modes: **identical** strict
  (per-event) and content digests, 0 divergences.

## Results on this laptop (JDK 25; ratio is what matters)

End-to-end parse throughput (ops/s, `-gc true`; optimized shadow vs master
baseline, same session). Which `BasicStreamReader` runs is decided by the
classpath.

| profile | baseline | arraycopy | speedup |
|---------|---------:|----------:|--------:|
| **entity** (large text w/ `&amp;` entities → copy path) | 1420 ± 70  | 1848 ± 155 | **1.30x (clear; CIs disjoint)** |
| prose (large plain text → share path) | 3329 ± 273 | 3231 ± 177 | ~1.0x (parity) |
| records (many small elements → share path) | 357 ± 25 | 360 ± 32 | ~1.0x (parity) |

The win is concentrated in the **copy path** (`entity`): text with entities or
CDATA/character references, where the reader must materialize characters into its
output buffer. `prose` and `records` go through the untouched share path, so they
are neutral (`records` measured back-to-back to control for thermal drift).
Baseline throughput drifts run-to-run, so only compare numbers gathered in the
same session with identical settings.

## Benchmarks

- **`ParseBench`** — end-to-end throughput; profiles `entity` (copy path, where
  the win is), `prose` and `records` (share path, neutral controls). No Vector API.
- **`ParseSanity`** — non-JMH wall-clock cross-check (ms/parse, MB/s).

Requires JMH 1.37 (`jmh-core`, `jopt-simple`, `commons-math3`) on the classpath.

## How to build & run

```powershell
$JDK   = "C:\develop\jdk-25\bin"
$REPO  = "C:\develop\.m2\repo"
$STAX2 = "$REPO\org\codehaus\woodstox\stax2-api\4.3.0\stax2-api-4.3.0.jar"
$JMHC  = "$REPO\org\openjdk\jmh\jmh-core\1.37\jmh-core-1.37.jar"
$JMHA  = "$REPO\org\openjdk\jmh\jmh-generator-annprocess\1.37\jmh-generator-annprocess-1.37.jar"
$JOPT  = "$REPO\net\sf\jopt-simple\jopt-simple\5.0.4\jopt-simple-5.0.4.jar"
$MATH  = "$REPO\org\apache\commons\commons-math3\3.6.1\commons-math3-3.6.1.jar"
$JMH   = "$JMHC;$JOPT;$MATH"

# 1) Build the optimized reader into a shadow dir (target\classes is the baseline)
$SHADOW = "$env:TEMP\ac_shadow"
& "$JDK\javac.exe" -implicit:none -d $SHADOW -cp "target\classes;$STAX2" `
    "src\main\java\com\ctc\wstx\sr\BasicStreamReader.java"

# 2) Compile the benchmark (annotation processing must be explicit on JDK 23+)
$BENCH = "$env:TEMP\ac_bench"
& "$JDK\javac.exe" -proc:full -cp "target\classes;$STAX2;$JMHC;$JMHA" -d $BENCH `
    benchmark\ParseBench.java

# 3a) BASELINE (target\classes only)
& "$JDK\java.exe" -cp "$BENCH;target\classes;$STAX2;$JMH" `
    org.openjdk.jmh.Main "ParseBench" -f 2 -wi 4 -i 8 -gc true

# 3b) OPTIMIZED (shadow first so it wins)
& "$JDK\java.exe" -cp "$BENCH;$SHADOW;target\classes;$STAX2;$JMH" `
    org.openjdk.jmh.Main "ParseBench" -f 2 -wi 4 -i 8 -gc true
```

No `--add-modules` anywhere: this build has no Vector-API dependency.
