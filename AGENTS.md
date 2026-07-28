# AGENTS.md

Guidance for coding agents working in this repository.

## Project shape

- `libddwaf/` is a git submodule pointing at
  [DataDog/libddwaf](https://github.com/DataDog/libddwaf) (the C++ WAF
  engine). It has no `.gitmodules` branch/tag pin: the version in use is
  whatever commit the submodule gitlink currently points to.
- `src/main/c/` is the JNI bridge in C (`waf_jni.c` is the core). Headers
  under `src/main/c/jni/*.h` are generated but checked in.
- `src/main/java/com/datadog/ddwaf/` is the public Java API.
- `src/test/groovy/com/datadog/ddwaf/` holds the JUnit4/Groovy test suite.
- `.github/workflows/actions.yml` is the single CI workflow. It builds and
  tests native binaries for Linux (glibc/musl, x86_64/aarch64), macOS
  (x86_64/aarch64), and Windows (x86_64).

## Build, format, test

Requirements: JDK 8+, CMake 3.15+.

```bash
./gradlew check                # standard suite: compiles libddwaf from the submodule + JNI + runs tests
./gradlew check -PwithASAN     # optional, mirrors the CI ASan job
./gradlew check -PuseZGC       # required to also run the GC race regression, see below
./gradlew spotlessCheck        # Java/Groovy formatting check
./gradlew format               # auto-fix Java/Groovy formatting
clang-format-18 -n -Werror $(find src/main/c -type f)   # C formatting check
```

Plain `./gradlew check` does **not** run the full suite: `ReachabilityFenceTest`
is excluded from the standard `test` task and only runs via the dedicated
`testGCRace` task, which is wired into `check` exclusively when `-PuseZGC` is
passed (see `build.gradle`, `testGCRace`/`useZGC` handling). This test
reproduces a GC-race SIGSEGV regression (APPSEC-62784) that the CI Alpine
JDK 21/25 matrix entries exercise. If your change touches `WafContext` or
arena/native-memory lifetime, run `./gradlew check -PuseZGC` — plain `check`
passing is not sufficient evidence of correctness for that kind of change.

To build the native JNI lib against a separately-built libddwaf without
touching the submodule checkout, use
`./gradlew buildNativeLibDebug -PlibddwafConfig=/path/to/libddwaf/out/share/cmake/libddwaf`
— this must be the **directory** containing `libddwaf-config-debug.cmake`
(the default is `$libddwafInstallPrefix/share/cmake/libddwaf`, see the
`cmakeNativeLibDebug` task in `build.gradle`), not the `.cmake` file itself.
(the `README.md` currently says `-PlibddwafDir`; that property name is
stale — `-PlibddwafConfig` is what `build.gradle` actually implements. If
you fix one, fix the other.).

CI (`.github/workflows/actions.yml`, "Build Native Libraries") is the source
of truth for what must pass. The `TestsPass` job (the branch-protection gate)
fans in `Test`, `Dev_Tests`, `Jmh_Build`, `Spotless`, and
`Jar_File_Stage_build_jar` (which itself needs every `Native_binaries_Stage_*`
job, including ASan and the static analyzer). `Coverage` and `ClangFormat`
run as independent checks and are **not** part of that fan-in — a green
`TestsPass` does not imply they passed. Reproduce the relevant job locally
before opening a PR rather than guessing from the workflow file alone.

## Conventions

- Branch naming: `<you>/<short-description>`, or
  `<you>/<TICKET>-<short-description>` when there's a tracking ticket.
- For feature work (not a mechanical version bump), open an issue to
  discuss the approach before submitting a PR (see `CONTRIBUTING.md`).
- When a doc (this file, `README.md`, Confluence) and the actual code
  disagree, the code wins — but fix the stale doc in the same PR instead of
  just working around it silently.

## Task: bump the libddwaf version

There is a detailed internal guide with full context, historical PR
references and known pitfalls at:
<https://datadoghq.atlassian.net/wiki/spaces/SAAL/pages/5678497795/Upgrade+libddwaf+version+in+libddwaf-java>
(Datadog-internal Confluence; not reachable outside the corporate network).
If you can reach it, read it before starting. If you cannot, the checklist
below covers the same ground. Note the guide can drift from the code over
time — if you find a discrepancy, trust the code and consider fixing the
guide.

### Before touching any file

Read the new libddwaf release's `UPGRADING.md` and `CHANGELOG.md`
(`https://github.com/DataDog/libddwaf/blob/master/UPGRADING.md` and
`.../CHANGELOG.md` on the target tag). Specifically look for:

- Renamed or removed keys in the `ddwaf_run` result or in the diagnostics
  output (e.g. a past release renamed `block_id` to
  `security_response_id`). These are **not** caught by the compiler: this
  binding reads result/diagnostics fields by hardcoded string key in
  `src/main/c/waf_jni.c` and `src/main/c/output.c` (e.g. `"timeout"`,
  `"duration"`, `"actions"`, `"events"`, `"attributes"`, `"keep"`,
  `"ruleset_version"`, `"rules"`, ...). A rename compiles and links fine and
  silently returns null/0/default at runtime.
- Changed types in the WAF output (e.g. a past release changed status codes
  from string to long, breaking Groovy assertions like `is('403')` until
  changed to `is(403L)`).
- New or removed public API on `ddwaf.h` (e.g. the `ddwaf_builder` API
  replacing the older `ddwaf_init`/`RuleSetInfo` flow in a past major bump).
  A change of this size is not a simple version bump: expect to touch
  `WafBuilder`/`WafDiagnostics`-equivalent Java classes and the JNI glue.

If in doubt about the scope of the change, do NOT assume it's a trivial
version bump just because it looks like one in the diff — verify against
`UPGRADING.md` first.

### Steps

1. Create a branch named `<you>/update-to-<version>` (or
   `<you>/<TICKET>-libddwaf-update-<version>` if there's a tracking ticket).
2. Move the submodule to the exact release tag (libddwaf tags have **no**
   `v` prefix, e.g. `1.30.0` not `v1.30.0`). If `libddwaf/` was never
   initialized (e.g. a fresh clone without `--recurse-submodules`), it's an
   empty directory, not a git repo — running `cd libddwaf` and then `git`
   commands there would silently operate on the outer superproject instead.
   Initialize it first:
   ```bash
   git submodule update --init libddwaf
   cd libddwaf
   git fetch --tags
   git checkout <new-version>
   cd ..
   git add libddwaf
   ```
   Verify `git -C libddwaf rev-parse HEAD` matches the tag commit exactly —
   never leave it pointing at an arbitrary `master` commit.
3. Update the `libddwafVersion` env var, near the top of
   `.github/workflows/actions.yml` (`grep -n "libddwafVersion:" .github/workflows/actions.yml`
   to find its current line). This is a **second, independent** source of
   truth from the submodule: it controls which precompiled release tarballs
   CI downloads for the native-binary jobs. It must always match the
   submodule version — nothing enforces this automatically.
4. Update the `LIB_VERSION` constant in
   `src/main/java/com/datadog/ddwaf/Waf.java`
   (`grep -n "LIB_VERSION" src/main/java/com/datadog/ddwaf/Waf.java`).
5. Bump the artifact `version` at the top of `build.gradle`
   (`grep -n "^version" build.gradle`). A libddwaf minor bump conventionally
   maps to a Java artifact minor bump. **Do not bump the Java artifact's
   major version just because libddwaf did** — the two version schemes are
   unrelated; only bump major on an actual Java-facing API break in this
   project.
6. Verify the new libddwaf release actually publishes all 4 tarballs the
   workflow expects (each with a `.sha256`):
   `libddwaf-<v>-darwin-x86_64.tar.gz`, `-darwin-arm64.tar.gz`,
   `-windows-x64.tar.gz`, and `-{x86_64,aarch64}-linux-musl.tar.gz`. Note
   that all Linux jobs, including glibc ones, consume the `-linux-musl`
   asset (a static build).
7. Build and test locally (see "Build, format, test" above): at minimum
   `./gradlew check`, plus `-PwithASAN` if you touched the C bridge.
8. Only if you changed a `native` method signature in Java (not needed for
   a plain version bump): regenerate JNI headers with
   `./gradlew generateJniHeaders`, copy the result from
   `build/generated/jni` into `src/main/c/jni/*.h`, and fix inner-class
   naming by hand (see
   [JDK-8145897](https://bugs.openjdk.java.net/browse/JDK-8145897)).
9. Fix any failing Groovy tests under `src/test/groovy/com/datadog/ddwaf/`
   caused by behavior/type/schema changes in the new libddwaf version —
   this is expected and normal, not a sign something is broken in the
   binding.
10. Open the PR. If the WAF's behavior changed in a way that could affect
    `dd-trace-java` (the consumer of this library), say so explicitly in
    the PR description — this update is rarely self-contained, and a
    matching change is often needed downstream.

### Sanity checks before considering the work done

- `libddwaf` submodule commit and the `libddwafVersion` env var in
  `.github/workflows/actions.yml` refer to the exact same version.
- `Waf.LIB_VERSION` matches both of the above.
- `./gradlew check` passes locally, including
  `BasicTests.groovy`'s `assert Waf.version =~ Waf.LIB_VERSION` (this is a
  regex match, not equality — it catches a forgotten bump but is not a
  strict guarantee).
- You checked whether a newer libddwaf patch release already exists before
  opening the PR (patch releases often land within hours to days of a
  release).
