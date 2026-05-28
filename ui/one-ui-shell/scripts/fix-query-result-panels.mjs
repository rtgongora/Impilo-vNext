#!/usr/bin/env node
/**
 * Repairs codemod fallout: missing imports, broken ?? {}.isLoading patterns, mutation isPending.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP = path.resolve(__dirname, "../src/app");

const IMPORT_LINE =
  'import { QueryResultPanel } from "@/components/common/QueryResultPanel";\n';

function walk(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (entry.name.endsWith(".tsx")) acc.push(full);
  }
  return acc;
}

function ensureImport(source) {
  if (source.includes('@/components/common/QueryResultPanel"')) return source;
  if (!source.includes("QueryResultPanel")) return source;
  if (source.startsWith('"use client";')) {
    return source.replace(/^"use client";\s*\n/, `"use client";\n${IMPORT_LINE}`);
  }
  return IMPORT_LINE + source;
}

const BROKEN_NULLISH = [
  [
    /isLoading=\{(\w+)\s*\?\?\s*\{\}\.isLoading\}\s*isError=\{\1\s*\?\?\s*\{\}\.isError\}\s*error=\{\1\s*\?\?\s*\{\}\.error\}\s*data=\{\1\s*\?\?\s*\{\}\}/g,
    'data={$1 ?? {}}',
  ],
  [
    /isLoading=\{(\w+)\s*\?\?\s*\[\]\.isLoading\}\s*isError=\{\1\s*\?\?\s*\[\]\.isError\}\s*error=\{\1\s*\?\?\s*\[\]\.error\}\s*data=\{\1\s*\?\?\s*\[\]\}/g,
    'data={$1 ?? []}',
  ],
];

/** Plain state / API objects mis-tagged as queries — drop bogus loading props. */
const PLAIN_DATA_PANELS = [
  ["summary", "Wellness summary"],
  ["currentPayload", "Coverage payload"],
  ["lastActionResponse", "Last action"],
  ["lastRunResponse", "Last run"],
  ["anchoredResult", "Anchored result"],
  ["workContext", "Work context"],
  ["commerceOrder", "Commerce order"],
  ["result", "Result"],
];

function fixPlainDataPanels(source) {
  let out = source;
  for (const [varName, title] of PLAIN_DATA_PANELS) {
    const re = new RegExp(
      `<QueryResultPanel title="[^"]*" isLoading=\\{${varName}\\.isLoading\\} isError=\\{${varName}\\.isError\\} error=\\{${varName}\\.error\\} data=\\{${varName}\\} />`,
      "g",
    );
    out = out.replace(
      re,
      `<QueryResultPanel title="${title}" data={${varName}} />`,
    );
    const re2 = new RegExp(
      `<QueryResultPanel title="Result" isLoading=\\{${varName}\\.isLoading\\} isError=\\{${varName}\\.isError\\} error=\\{${varName}\\.error\\} data=\\{${varName}\\} />`,
      "g",
    );
    out = out.replace(re2, `<QueryResultPanel title="${title}" data={${varName}} />`);
  }
  return out;
}

function fixMutations(source) {
  return source.replace(
    /isLoading=\{(\w+)\.isLoading\}/g,
    "isPending={$1.isPending} isLoading={$1.isPending}",
  );
}

let touched = 0;
for (const file of walk(APP)) {
  let content = fs.readFileSync(file, "utf8");
  if (!content.includes("QueryResultPanel")) continue;
  const before = content;
  content = ensureImport(content);
  for (const [re, rep] of BROKEN_NULLISH) {
    content = content.replace(re, `<QueryResultPanel title="Result" ${rep} />`);
  }
  content = fixPlainDataPanels(content);
  content = fixMutations(content);
  if (content !== before) {
    fs.writeFileSync(file, content);
    touched++;
    console.log("fixed", path.relative(APP, file));
  }
}

console.log(`\nRepaired ${touched} files.`);
