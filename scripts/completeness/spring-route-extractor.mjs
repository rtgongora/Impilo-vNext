/**
 * Extract Spring MVC routes from Java sources for contract completeness tooling.
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

/**
 * Set of absolute paths git tracks (committed or staged), memoized per process.
 *
 * Hermeticity: {@link walkFiles} must reflect the BRANCH, not the physical working directory.
 * Worktrees accumulate untracked/stale `.java` files from prior branch checkouts (and sibling
 * worktrees share a parent dir), so a raw filesystem walk would count debt for code that is not in
 * the branch being scanned — making the gap count vary by worktree state and even flag files absent
 * from the branch. Restricting to git-tracked files makes the scan reproducible and branch-local.
 * Returns `null` (→ no filtering, prior behaviour) when git is unavailable.
 */
let _trackedFiles; // undefined = not computed; null = git unavailable; Set = tracked abs paths
function trackedFiles() {
  if (_trackedFiles !== undefined) return _trackedFiles;
  try {
    const root = spawnSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' });
    if (root.status !== 0) { _trackedFiles = null; return _trackedFiles; }
    const top = root.stdout.trim();
    const ls = spawnSync('git', ['ls-files', '-z'], { cwd: top, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
    if (ls.status !== 0) { _trackedFiles = null; return _trackedFiles; }
    _trackedFiles = new Set(
      ls.stdout.split('\0').filter(Boolean).map((relPath) => path.resolve(top, relPath)),
    );
  } catch {
    _trackedFiles = null;
  }
  return _trackedFiles;
}

export const STUB_PATTERNS = [
  /HttpStatus\.NOT_IMPLEMENTED/,
  /ResponseEntity\.status\s*\(\s*501/,
  /status\s*\(\s*501/,
  /UnsupportedOperationException/,
  /NotImplementedException/,
  /\bTODO\b.*implement/i,
  /return\s+ResponseEntity\.ok\s*\(\s*Collections\.emptyList\s*\(\)/,
];

export function walkFiles(dir, filter = () => true, acc = []) {
  if (!fs.existsSync(dir)) return acc;
  const tracked = trackedFiles(); // null when git unavailable → no hermetic filtering
  for (const name of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, name.name);
    if (name.isDirectory()) {
      if (name.name === 'node_modules' || name.name === 'target' || name.name === '.git') continue;
      walkFiles(p, filter, acc);
    } else if (filter(p) && (tracked === null || tracked.has(path.resolve(p)))) {
      // Only count files the current branch tracks — see trackedFiles() (hermeticity).
      acc.push(p);
    }
  }
  return acc;
}

export function normalizePathPattern(p) {
  return p
    .replace(/\/+/g, '/')
    .replace(/\{[^}]+\}/g, '{}')
    .replace(/\/$/, '')
    .toLowerCase();
}

function joinPaths(base, sub) {
  if (!sub) return (base || '/').replace(/\/+/g, '/');
  if (!base) {
    return sub.startsWith('/') ? sub.replace(/\/+/g, '/') : `/${sub}`.replace(/\/+/g, '/');
  }
  const combined = sub.startsWith('/') ? `${base}${sub}` : `${base}/${sub}`;
  return combined.replace(/\/+/g, '/');
}

/** Pull quoted path literals from a mapping annotation slice. */
export function extractPathsFromMappingSlice(slice) {
  const parenStart = slice.indexOf('(');
  if (parenStart < 0) return [''];
  const inner = slice.slice(parenStart + 1, slice.lastIndexOf(')'));
  const paths = [...inner.matchAll(/["']([^"']+)["']/g)].map((m) => m[1]);
  if (paths.length > 0) return paths;
  return [''];
}

/** Strip comments and string literals so keyword scans cannot match inside them. */
function stripCommentsAndStrings(text) {
  return text
    .replace(/\/\*[\s\S]*?\*\//g, (m) => ' '.repeat(m.length))
    .replace(/\/\/[^\n]*/g, (m) => ' '.repeat(m.length))
    .replace(/"(?:\\.|[^"\\])*"/g, (m) => ' '.repeat(m.length));
}

/**
 * The base path contributed by a file's class-level `@RequestMapping`, computed ONCE
 * per file.
 *
 * The original implementation began its search at the newline BEFORE the `class`
 * keyword and ran forward to the method annotation. A class-level `@RequestMapping`
 * sits ABOVE that keyword, so it was outside the searched window by construction:
 * the function returned '' for essentially every handler in every controller, and
 * each route was emitted without its prefix. Measured on AccessChannelsController:
 * `@RequestMapping("/internal/v1/access")` on line 22, `public class` on line 23,
 * and the extractor produced `get /landela/templates` where the real route is
 * `/internal/v1/access/landela/templates`.
 *
 * Scanning BACKWARDS from each mapping to the nearest preceding `class` keyword was
 * the obvious repair and is not reliable: WalletController carries the comment
 * "see class javadoc" inside its body, and `\bclass\s+\w` matches it, so every
 * handler below that line lost its prefix again. Nested records and inner classes
 * defeat it the same way.
 *
 * So resolve it per FILE instead of per mapping — which is also how Spring actually
 * behaves for the one-controller-per-file convention this repository follows: take
 * the first `@RequestMapping` that annotates a class declaration, on text with
 * comments and string literals blanked out.
 */
function fileClassBases(text) {
  const scan = stripCommentsAndStrings(text);
  for (const m of scan.matchAll(/@RequestMapping\s*\(/g)) {
    const close = scan.indexOf(')', m.index);
    if (close < 0) continue;
    if (!classDeclFollows(scan, close)) continue;
    // Read the paths from the ORIGINAL text: the literals were blanked in `scan`.
    // A class mapping may declare SEVERAL prefixes —
    // `@RequestMapping({"/internal/v1/ai", "/internal/v1/ai-governance"})` — and
    // Spring serves every handler under each of them. Keep them all, or references
    // to the second prefix look like routes that do not exist.
    const paths = extractPathsFromMappingSlice(text.slice(m.index, close + 1)).filter(Boolean);
    return paths.length ? paths : [''];
  }
  return [''];
}

/**
 * True when a class declaration stands between the annotation closing at `close` and
 * the block it annotates.
 *
 * The `{` must be looked for AFTER the closing paren, never from the annotation's
 * start: `@RequestMapping({"/a", "/b"})` opens a brace for its own ARRAY literal, and
 * searching from the start finds that one, concludes "no class here", and treats a
 * class-level mapping as a handler — which then gets its own prefix applied to
 * itself, producing `/internal/v1/ai/internal/v1/ai`.
 */
function classDeclFollows(scan, close) {
  const after = scan.slice(close + 1);
  const brace = after.indexOf('{');
  if (brace < 0) return false;
  return /\bclass\s+\w/.test(after.slice(0, brace));
}

/**
 * True when the mapping at `mappingIndex` is the class-level `@RequestMapping`
 * itself rather than a handler. Such an annotation is a path PREFIX, not an
 * endpoint, so consumers counting or resolving real routes must be able to exclude
 * it — otherwise every controller contributes one phantom route.
 */
function isClassLevelMapping(text, mappingIndex, annotation) {
  if (annotation !== 'Request') return false;
  const scan = stripCommentsAndStrings(text);
  const close = scan.indexOf(')', mappingIndex);
  if (close < 0) return false;
  return classDeclFollows(scan, close);
}

function mappingMethodFromAnnotation(ann, slice) {
  const annLower = ann.toLowerCase();
  if (annLower !== 'request') return annLower;
  const methodsMatch = slice.match(/method\s*=\s*(?:RequestMethod\.)?(\w+)/);
  return methodsMatch ? methodsMatch[1].toLowerCase() : 'all';
}

/**
 * @param {string} javaRoot
 * @param {{ stubPatterns?: RegExp[], fileFilter?: (p: string) => boolean }} [options]
 */
export function extractSpringRoutes(javaRoot, options = {}) {
  const stubPatterns = options.stubPatterns ?? STUB_PATTERNS;
  const fileFilter = options.fileFilter ?? ((p) => p.endsWith('.java'));
  const routes = [];
  const files = walkFiles(javaRoot, fileFilter);

  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    if (!text.includes('Mapping')) continue;

    // Resolved once per file, not once per mapping — see fileClassBases.
    const basesForFile = fileClassBases(text);

    const mappingRegex = /@(Get|Post|Put|Patch|Delete|Request)Mapping\b/g;
    let m;
    while ((m = mappingRegex.exec(text)) !== null) {
      const ann = m[1];
      const start = m.index;
      const classLevel = isClassLevelMapping(text, start, ann);
      // The class-level annotation contributes the prefix; it must not be prefixed
      // with itself.
      const classBases = classLevel ? [''] : basesForFile;
      const afterAnn = text.slice(start + m[0].length);
      const trimmedAfter = afterAnn.trimStart();
      let slice;
      let subPaths;
      if (!trimmedAfter.startsWith('(')) {
        slice = `@${ann}Mapping`;
        subPaths = [''];
      } else {
        const sliceEnd = text.indexOf(')', start);
        if (sliceEnd < 0) continue;
        slice = text.slice(start, sliceEnd + 1);
        subPaths = extractPathsFromMappingSlice(slice);
      }
      const method = mappingMethodFromAnnotation(ann, slice);

      for (const [classBase, sub] of classBases.flatMap((b) => subPaths.map((s) => [b, s]))) {
        const full = joinPaths(classBase, sub);
        const nextAnn = text.indexOf('@', start + 1);
        const bodySlice = text.slice(start, nextAnn > 0 ? nextAnn : start + 1200);
        const stubHit = stubPatterns.find((re) => re.test(bodySlice));
        routes.push({
          file,
          method,
          path: full || '/',
          normalized: normalizePathPattern(full || '/'),
          stubHit: stubHit ? stubHit.source : null,
          // A class-level @RequestMapping is a path PREFIX, not an endpoint. It is
          // still emitted (removing it would change every existing route count
          // silently) but it is now labelled, so anything resolving real endpoints
          // can drop it instead of treating it as an unreachable route.
          classLevel,
        });
      }
    }
  }
  return routes;
}
