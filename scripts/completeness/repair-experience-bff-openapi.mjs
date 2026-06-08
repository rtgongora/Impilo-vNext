#!/usr/bin/env node
/**
 * Repair experience-bff.openapi.yaml:
 * - Strip orphaned YAML after path-level $ref (#/paths/...)
 * - Dedupe duplicate HTTP methods under the same path (keep first)
 * - Merge duplicate path keys (union HTTP methods, prefer richer block)
 * - Quote inline backtick error-code descriptions
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TARGET = path.resolve(__dirname, '../../contracts/openapi/experience-bff.openapi.yaml');

const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);

function isPathLine(line) {
  return /^  \/.+:\s*$/.test(line);
}

function isPathLevelRef(line) {
  return /^    \$ref:\s+'#\/paths\//.test(line);
}

/** Remove lines after path-level $ref until next path or components. */
function stripRefOrphans(lines) {
  const out = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    out.push(line);
    if (isPathLevelRef(line)) {
      while (i + 1 < lines.length && !isPathLine(lines[i + 1]) && !lines[i + 1].startsWith('components:')) {
        i++;
      }
    }
  }
  return out;
}

function fixDescriptions(text) {
  return text.replace(/description: `([A-Z_]+)` ([^\n]+)/g, (_, code, rest) =>
    `description: ${JSON.stringify(`${code} ${rest}`)}`);
}

function splitPathBlocks(lines) {
  const blocks = [];
  let i = 0;
  while (i < lines.length) {
    if (lines[i].startsWith('components:')) break;
    if (!isPathLine(lines[i])) {
      i++;
      continue;
    }
    const path = lines[i].trim().replace(/:$/, '');
    const start = i;
    i++;
    while (i < lines.length && !isPathLine(lines[i]) && !lines[i].startsWith('components:')) i++;
    blocks.push({ path, lines: lines.slice(start, i) });
  }
  return blocks;
}

function methodsInBlock(blockLines) {
  const methods = new Set();
  for (const line of blockLines) {
    const m = line.match(/^    (\w+):\s*$/);
    if (m && HTTP_METHODS.has(m[1])) methods.add(m[1]);
  }
  return methods;
}

/** Extract one HTTP method slice from a path block. */
function extractMethodSlice(blockLines, method) {
  const out = [];
  let capturing = false;
  for (const line of blockLines) {
    const m = line.match(/^    (\w+):\s*$/);
    if (m && HTTP_METHODS.has(m[1])) {
      if (capturing) break;
      if (m[1] === method) capturing = true;
      continue;
    }
    if (capturing) out.push(line);
  }
  return capturing ? [`    ${method}:`, ...out] : [];
}

function mergePathBlocks(a, b) {
  const pathLine = a.find((l) => isPathLine(l)) ?? b.find((l) => isPathLine(l));
  const mergedMethods = new Map();
  for (const block of [a, b]) {
    for (const method of methodsInBlock(block)) {
      if (!mergedMethods.has(method)) {
        mergedMethods.set(method, extractMethodSlice(block, method));
      }
    }
  }
  return [pathLine, ...[...mergedMethods.values()].flat()];
}

function dedupePathBlocks(lines) {
  const header = [];
  let i = 0;
  while (i < lines.length && !lines[i].startsWith('paths:')) {
    header.push(lines[i]);
    i++;
  }
  if (i < lines.length) header.push(lines[i++]);

  const tailStart = lines.findIndex((l) => l.startsWith('components:'));
  const pathSection = tailStart >= 0 ? lines.slice(i, tailStart) : lines.slice(i);
  const tail = tailStart >= 0 ? lines.slice(tailStart) : [];

  const blocks = splitPathBlocks(pathSection);
  const byPath = new Map();
  const order = [];
  for (const block of blocks) {
    if (!byPath.has(block.path)) {
      byPath.set(block.path, block.lines);
      order.push(block.path);
    } else {
      byPath.set(block.path, mergePathBlocks(byPath.get(block.path), block.lines));
    }
  }

  const rebuilt = [...header];
  for (let idx = 0; idx < order.length; idx++) {
    if (idx > 0 || rebuilt[rebuilt.length - 1]?.trim()) rebuilt.push('');
    rebuilt.push(...byPath.get(order[idx]));
  }
  if (tail.length) {
    rebuilt.push('');
    rebuilt.push(...tail);
  }
  return rebuilt;
}

function dedupeMethodsPerPath(lines) {
  const out = [];
  const httpMethods = HTTP_METHODS;
  let inPaths = false;
  let methodsSeen = new Set();
  let skip = false;

  for (const line of lines) {
    if (line.startsWith('paths:')) {
      inPaths = true;
      out.push(line);
      continue;
    }
    if (inPaths && line.startsWith('components:')) {
      inPaths = false;
      methodsSeen = new Set();
      skip = false;
      out.push(line);
      continue;
    }
    if (!inPaths) {
      out.push(line);
      continue;
    }
    if (isPathLine(line)) {
      methodsSeen = new Set();
      skip = false;
      out.push(line);
      continue;
    }
    const mm = line.match(/^    (\w+):\s*$/);
    if (mm && httpMethods.has(mm[1])) {
      if (methodsSeen.has(mm[1])) {
        skip = true;
        continue;
      }
      methodsSeen.add(mm[1]);
      skip = false;
      out.push(line);
      continue;
    }
    if (skip) continue;
    out.push(line);
  }
  return out;
}

function repair(text) {
  let lines = text.split('\n');
  lines = stripRefOrphans(lines);
  lines = dedupeMethodsPerPath(lines);
  lines = dedupePathBlocks(lines);
  return fixDescriptions(lines.join('\n'));
}

const raw = fs.readFileSync(TARGET, 'utf8');
const fixed = repair(raw);
fs.writeFileSync(TARGET, fixed);
try {
  yaml.load(fixed);
  console.log(`repair-experience-bff-openapi: OK ${TARGET}`);
} catch (e) {
  console.error(`repair-experience-bff-openapi: still invalid — ${e.reason} line ${e.mark?.line}`);
  process.exit(1);
}
