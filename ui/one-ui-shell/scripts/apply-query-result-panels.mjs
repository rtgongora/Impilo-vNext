#!/usr/bin/env node
/**
 * Replaces JSX {JSON.stringify(..., null, 2)} display dumps with QueryResultPanel.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP = path.resolve(__dirname, "../src/app");

const TARGET_FILES = process.argv.slice(2);

function walk(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (entry.name === "page.tsx") acc.push(full);
  }
  return acc;
}

const files =
  TARGET_FILES.length > 0
    ? TARGET_FILES.map((f) => path.resolve(f))
    : walk(APP).filter((f) => fs.readFileSync(f, "utf8").includes("{JSON.stringify("));

const JSX_STRINGIFY =
  /\{JSON\.stringify\(\s*([^,]+?)\s*,\s*null\s*,\s*2\s*\)\}/gs;

function titleFromExpr(expr) {
  const m = expr.match(/([a-zA-Z0-9_]+)\.(data|error)/);
  if (m) return m[1].replace(/([A-Z])/g, " $1").replace(/^./, (c) => c.toUpperCase());
  return "Result";
}

function queryPropsFromExpr(expr) {
  const base = expr.trim().split(".")[0];
  return `isLoading={${base}.isLoading} isError={${base}.isError} error={${base}.error} data={${expr.trim()}}`;
}

function ensureImport(source) {
  if (source.includes("QueryResultPanel")) return source;
  const importLine = 'import { QueryResultPanel } from "@/components/common/QueryResultPanel";\n';
  if (source.startsWith('"use client";')) {
    return source.replace(/^"use client";\s*\n/, `"use client";\n${importLine}`);
  }
  return importLine + source;
}

let updated = 0;
for (const file of files) {
  let content = fs.readFileSync(file, "utf8");
  if (!/\{JSON\.stringify\([^)]*,\s*null\s*,\s*2\s*\)\}/.test(content)) continue;

  const preBlock =
    /<pre[^>]*>\s*\{JSON\.stringify\(\s*([^,]+?)\s*,\s*null\s*,\s*2\s*\)\}\s*<\/pre>/gs;
  content = content.replace(preBlock, (_, expr) => {
    const title = titleFromExpr(expr);
    return `<QueryResultPanel title="${title}" ${queryPropsFromExpr(expr)} />`;
  });

  content = content.replace(JSX_STRINGIFY, (_, expr) => {
    const title = titleFromExpr(expr);
    return `<QueryResultPanel title="${title}" ${queryPropsFromExpr(expr)} />`;
  });

  if (content.includes("QueryResultPanel")) {
    content = ensureImport(content);
    fs.writeFileSync(file, content);
    updated++;
    console.log("updated", path.relative(APP, file));
  }
}

console.log(`\nDone. Updated ${updated} files.`);
