#!/usr/bin/env node
/**
 * Bulk-import Zimbabwe Admin-2 / Admin-3 rows into Tuso from CSV (COD-AB / ZIMSTAT–aligned extracts).
 *
 * Usage:
 *   node scripts/registry/import-zw-admin-csv.mjs --tusoUrl=http://localhost:8084 --districts=./districts.csv --wards=./wards.csv
 *
 * DEV-only demo (non-authoritative):
 *   node scripts/registry/import-zw-admin-csv.mjs --districts=scripts/registry/fixtures/zimbabwe-districts.DEV-ONLY.sample.csv --wards=scripts/registry/fixtures/zimbabwe-wards.DEV-ONLY.sample.csv
 *
 * districts.csv columns: provinceCode,districtCode,name,sourceRef
 * wards.csv columns: districtCode,wardCode,name,sourceRef
 *
 * Requires trust headers accepted by Tuso (dev: permissive). Production: run behind mesh with service identity.
 */

import fs from "node:fs";
import path from "node:path";

function parseArgs(argv) {
  const out = {};
  for (const a of argv) {
    const m = /^--([^=]+)=(.*)$/.exec(a);
    if (m) out[m[1]] = m[2];
  }
  return out;
}

function parseCsv(text) {
  const lines = text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0 && !l.startsWith("#"));
  if (lines.length < 2) return [];
  const header = lines[0].split(",").map((h) => h.trim());
  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    const cols = lines[i].split(",");
    const row = {};
    header.forEach((h, idx) => {
      row[h] = (cols[idx] ?? "").trim();
    });
    rows.push(row);
  }
  return rows;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const tusoUrl = (args.tusoUrl || "http://localhost:8084").replace(/\/$/, "");
  const districtsPath = args.districts;
  const wardsPath = args.wards;
  if (!districtsPath || !wardsPath) {
    console.error("Provide --districts=path and --wards=path");
    process.exit(1);
  }
  const districtsRaw = fs.readFileSync(path.resolve(districtsPath), "utf8");
  const wardsRaw = fs.readFileSync(path.resolve(wardsPath), "utf8");
  const dRows = parseCsv(districtsRaw);
  const wRows = parseCsv(wardsRaw);
  const districts = dRows.map((r) => ({
    provinceCode: r.provinceCode || r.province_code,
    districtCode: r.districtCode || r.district_code,
    name: r.name,
    sourceRef: r.sourceRef || r.source_ref || "",
  }));
  const wards = wRows.map((r) => ({
    districtCode: r.districtCode || r.district_code,
    wardCode: r.wardCode || r.ward_code,
    name: r.name,
    sourceRef: r.sourceRef || r.source_ref || "",
  }));
  const url = `${tusoUrl}/v1/internal/geo/zw/bulk`;
  const body = JSON.stringify({ districts, wards });
  fetch(url, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Tenant-ID": args.tenantId || "00000000-0000-0000-0000-000000000001",
      "X-Request-ID": crypto.randomUUID(),
      "X-Correlation-ID": crypto.randomUUID(),
    },
    body,
  })
    .then(async (res) => {
      const j = await res.json().catch(() => ({}));
      if (!res.ok) {
        console.error("Import failed", res.status, j);
        process.exit(1);
      }
      console.log("OK", JSON.stringify(j, null, 2));
    })
    .catch((e) => {
      console.error(e);
      process.exit(1);
    });
}

main();
