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

function extractClassBase(text, mappingIndex) {
  const before = text.slice(0, mappingIndex);
  const classPos = before.lastIndexOf('class ');
  const searchStart = classPos >= 0 ? before.lastIndexOf('\n', classPos) : 0;
  const preamble = before.slice(searchStart > 0 ? searchStart : 0, mappingIndex);
  const match = preamble.match(/@RequestMapping\s*\([\s\S]*?\)/);
  if (!match) return '';
  return extractPathsFromMappingSlice(match[0])[0] ?? '';
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

    const mappingRegex = /@(Get|Post|Put|Patch|Delete|Request)Mapping\b/g;
    let m;
    while ((m = mappingRegex.exec(text)) !== null) {
      const ann = m[1];
      const start = m.index;
      const classBase = extractClassBase(text, start);
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

      for (const sub of subPaths) {
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
        });
      }
    }
  }
  return routes;
}
