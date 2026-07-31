# W16a — TeaVM IITT corpus spike (go/no-go)

**Verdict: GO**

TeaVM 0.11.0 can compile `libs/emergency-domain` for the browser and run the shared IITT JSON corpus headless with acceptable size and cold-start.

## Evidence

| Metric | Value |
|--------|--------|
| Status | `PASS scenarios=23 failures=0` |
| Bundle | `iitt-corpus.js` **152 834 bytes (~149 KiB)** |
| Cold-start (main + corpus) | **49 ms** (Playwright `performance.now`) |
| Wall (page load → result) | **225 ms** |
| TeaVM | `0.11.0`, profile `teavm-spike` |
| Runner | Chromium headless via Playwright (`file://` harness) |

Machine result: [`reports/journeys/emergency-pack-w16a/teavm-corpus-result.json`](../../../reports/journeys/emergency-pack-w16a/teavm-corpus-result.json).

Screenshot: [`reports/journeys/emergency-pack-w16a/teavm-corpus.png`](../../../reports/journeys/emergency-pack-w16a/teavm-corpus.png).

## How the corpus is embedded (plugin, not path hardcoding)

TeaVM omits classpath resources by default. Inclusion is via the TeaVM **ResourceSupplier** plugin:

1. `IittCorpusResourceSupplier` implements `org.teavm.classlib.ResourceSupplier` and returns `iitt-corpus/scenarios.json`.
2. ServiceLoader registration: `META-INF/services/org.teavm.classlib.ResourceSupplier`.
3. Runtime read uses `ClassLoader#getResourceAsStream` (TeaVM’s wired path).

Callers keep a normal parameterized resource path (`IittCorpusRunner.CORPUS_RESOURCE`); they do **not** rely on an LDC string-literal trick for embedding.

## Reproduce

```bash
cd libs/emergency-domain
mvn -Pteavm-spike clean package
cp src/teavm/iitt-corpus.html target/teavm/
NODE_PATH=../../ui/node_modules node -e '... playwright file:// harness ...'
```

## Scope notes

- Dropped unused `paediatric-domain` dependency (Jackson / `getResourceAsStream` hostile to browser compile; unused by imports).
- JVM `@TestFactory` (`IittCorpusTestFactory`) and TeaVM harness share `src/main/resources/iitt-corpus/scenarios.json`.
- Spike stays behind `-Pteavm-spike` so default CI does not pay TeaVM compile cost.

## Follow-on (W16b)

Offline writes / service worker can consume this GO as permission to keep clinical triage truth in shared Java (or a future JS binding), not re-implement IITT in TypeScript.
