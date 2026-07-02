#!/usr/bin/env node
/**
 * Sync frontend_wiring_status from docs/registry/services-registry.yaml
 * into ui/one-ui-shell/src/generated/registry-maturity.json for badge overlays.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), "../..");
const REGISTRY = path.join(ROOT, "docs/registry/services-registry.yaml");
const OUT_DIR = path.join(ROOT, "ui/one-ui-shell/src/generated");
const OUT_FILE = path.join(OUT_DIR, "registry-maturity.json");

const source = fs.readFileSync(REGISTRY, "utf8");
const entries = {};
const idRe = /^\s*-\s*id:\s*(.+)$/gm;
const names = [...source.matchAll(idRe)].map((m) => m[1].trim());

for (const name of names) {
  const anchor = source.indexOf(`\n  - id: ${name}`);
  // Slice to the next service entry, not a fixed window — long entries pushed
  // frontend_wiring_status past a fixed cutoff and silently truncated values.
  const next = anchor >= 0 ? source.indexOf("\n  - id: ", anchor + 1) : -1;
  const slice = anchor >= 0 ? source.slice(anchor, next >= 0 ? next : source.length) : "";
  const wiring = slice.match(/frontend_wiring_status:\s*(\S+)/);
  if (wiring) {
    entries[name] = wiring[1];
  }
}

fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(
  OUT_FILE,
  `${JSON.stringify({ generatedAt: new Date().toISOString(), services: entries }, null, 2)}\n`,
);
console.log(`Wrote ${Object.keys(entries).length} service maturity rows → ${path.relative(ROOT, OUT_FILE)}`);
