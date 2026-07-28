# AGENTS.md

Guidance for coding agents working in this repository, in particular for the
recurring task of **updating the bundled libddwaf version**.

## Project shape

- `libddwaf/` is a git submodule pointing at
  [DataDog/libddwaf](https://github.com/DataDog/libddwaf) (the C++ WAF
  engine). It has no `.gitmodules` branch/tag pin: the version in use is
  whatever commit the submodule gitlink currently points to.
- `src/main/c/` is the JNI bridge in C (`waf_jni.c` is the core). Headers
  under `src/main/c/jni/*.h` are generated but checked in.
- `src/main/java/com/datadog/ddwaf/` is the public Java API.
- `.github/workflows/actions.yml` is the single CI workflow. It builds and
  tests native binaries for Linux (glibc/musl, x86_64/aarch64), macOS
  (x86_64/aarch64), and Windows (x86_64).

## Task: bump the libddwaf version

There is a detailed internal guide with full context, historical PR
references and known pitfalls at:
<https://datadoghq.atlassian.net/wiki/spaces/SAAL/pages/5678497795/Upgrade+libddwaf+version+in+libddwaf-java>
(Datadog-internal Confluence; not reachable outside the corporate network).
If you can reach it, read it before starting. If you cannot, the checklist
below covers the same ground.

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
   `v` prefix, e.g. `1.30.0` not `v1.30.0`):
   ```bash
   cd libddwaf
   git fetch --tags
   git checkout <new-version>
   cd ..
   git add libddwaf
   ```
   Verify `git -C libddwaf rev-parse HEAD` matches the tag commit exactly —
   never leave it pointing at an arbitrary `master` commit.
3. Update `libddwafVersion` in `.github/workflows/actions.yml` (currently
   line 14). This is a **second, independent** source of truth from the
   submodule: it controls which precompiled release tarballs CI downloads
   for the native-binary jobs. It must always match the submodule version —
   nothing enforces this automatically.
4. Update `LIB_VERSION` in `src/main/java/com/datadog/ddwaf/Waf.java`
   (currently line 23).
5. Bump the artifact `version` in `build.gradle` (currently line 31). A
   libddwaf minor bump conventionally maps to a Java artifact minor bump.
   **Do not bump the Java artifact's major version just because libddwaf
   did** — the two version schemes are unrelated; only bump major on an
   actual Java-facing API break in this project.
6. Verify the new libddwaf release actually publishes all 4 tarballs the
   workflow expects (each with a `.sha256`):
   `libddwaf-<v>-darwin-x86_64.tar.gz`, `-darwin-arm64.tar.gz`,
   `-windows-x64.tar.gz`, and `-{x86_64,aarch64}-linux-musl.tar.gz`. Note
   that all Linux jobs, including glibc ones, consume the `-linux-musl`
   asset (a static build).
7. Build and test locally:
   ```bash
   ./gradlew check                # builds libddwaf from the submodule + JNI + all tests
   ./gradlew check -PwithASAN     # optional, mirrors the CI ASAN job
   ./gradlew spotlessCheck
   clang-format-18 -n -Werror $(find src/main/c -type f)
   ```
   To build against a separately-built libddwaf without touching the
   submodule checkout, use
   `./gradlew buildNativeLibDebug -PlibddwafConfig=/path/to/dir/of/libddwaf-config-debug.cmake`
   (the `README.md` currently says `-PlibddwafDir`; that property name is
   stale — `-PlibddwafConfig` is what `build.gradle` actually implements).
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

- `libddwaf` submodule commit and `.github/workflows/actions.yml`
  `libddwafVersion` refer to the exact same version.
- `Waf.LIB_VERSION` matches both of the above.
- `./gradlew check` passes locally, including
  `BasicTests.groovy`'s `assert Waf.version =~ Waf.LIB_VERSION` (this is a
  regex match, not equality — it catches a forgotten bump but is not a
  strict guarantee).
- You checked whether a newer libddwaf patch release already exists before
  opening the PR (patch releases often land within hours to days of a
  release).

## General conventions

- Formatting: `./gradlew spotlessCheck` (Java/Groovy) and
  `clang-format-18 -n -Werror` (C), matching `CONTRIBUTING.md`.
- CI is a single workflow, `.github/workflows/actions.yml` ("Build Native
  Libraries"); the `TestsPass` job is the merge gate and fans in the native
  binary, test, coverage, ASan and static-analyzer jobs.
- For feature work beyond a version bump, open an issue to discuss the
  approach before submitting a PR (see `CONTRIBUTING.md`).
