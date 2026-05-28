#!/usr/bin/env node

/**
 * Wave 20/21 — verify demo journey endpoints when Experience BFF is up.
 *
 * Sends mandatory v1.1 headers (same as one-ui-shell api-client / compose smoke-test).
 * Upstream sovereign services may be absent in the experience compose slice — 502/503/500
 * on delegated routes are reported as WARN (route wired, upstream down).
 *
 * Usage:
 *   node scripts/production-readiness/verify-demo-journeys.mjs [--bff http://localhost:8160]
 *   node scripts/production-readiness/verify-demo-journeys.mjs --sovereign
 */

const SOVEREIGN = process.argv.includes("--sovereign");
const BFF = process.argv.includes("--bff")
  ? process.argv[process.argv.indexOf("--bff") + 1]
  : process.env.BFF_URL ?? "http://localhost:8160";

const TENANT_ID = process.env.IMPILO_TENANT_ID ?? "00000000-0000-4000-8000-000000000001";
const POD_ID = process.env.IMPILO_POD_ID ?? "national-spine";

const GOLDEN_PATIENT = "CPID-ZW-00001";
const GOLDEN_FACILITY = "f1000000-0000-0000-0000-000000000001";
const HARARE_FACILITY = "a1b2c3d4-0001-4000-8000-000000000001";

const CHECKS = [
  { name: "BFF health", path: "/actuator/health", expectOk: true },
  { name: "Golden patient queue", path: `/internal/v1/queue/entries?facility_id=${GOLDEN_FACILITY}` },
  { name: "Inpatient admissions", path: "/internal/v1/inpatient/admissions", minRows: 1 },
  { name: "Integration hub routes", path: "/internal/v1/integration-hub/routes" },
  { name: "Core transactions", path: "/internal/v1/core-transactions" },
  { name: "Demo journey registry", path: "/internal/v1/demo-journeys", minRows: 7 },
];

const RX_PATH_CHECKS = [
  {
    name: "Pharmacy prescriptions (golden patient)",
    path: `/internal/v1/pharmacy/prescriptions?patient_id=${GOLDEN_PATIENT}`,
    minRows: SOVEREIGN ? 1 : 0,
    sovereignRequired: true,
  },
  {
    name: "MusheX payment intents (Rx)",
    path: `/internal/v1/finance/payer-ops/payment-intents?sourceType=ADHOC&sourceId=${GOLDEN_PATIENT}`,
    minRows: 1,
    sovereignRequired: true,
  },
  { name: "Costa billing", path: "/internal/v1/finance/billing?page=0&size=1", minRows: 1, sovereignRequired: true },
  { name: "Dispatch deliveries", path: "/internal/v1/dispatch/deliveries", sovereignRequired: true },
  {
    name: "Nhume deliveries",
    path: "/internal/v1/nhume/deliveries",
    minRows: SOVEREIGN ? 1 : 0,
    sovereignRequired: true,
  },
  {
    name: "Ndila tile config",
    path: "/internal/v1/ndila/tiles/config",
    sovereignRequired: true,
  },
];

/** Seven Wave 20 production-readiness journeys (Wave20g). */
const JOURNEY_CHECKS = [
  {
    journey: "1 Core clinical / Rx",
    name: "Queue (golden patient facility)",
    path: `/internal/v1/queue/entries?facility_id=${GOLDEN_FACILITY}`,
  },
  {
    journey: "2 Inpatient",
    name: "Inpatient admissions list",
    path: "/internal/v1/inpatient/admissions",
    minRows: 1,
  },
  {
    journey: "3 Wellness",
    name: "Wellness challenges",
    path: "/internal/v1/wellness/challenges",
    minRows: 1,
  },
  {
    journey: "4 Enterprise",
    name: "Inventory requisitions",
    path: `/internal/v1/inventory/requisitions?facility_id=${HARARE_FACILITY}`,
    minRows: 1,
  },
  {
    journey: "5 Telemedicine → dispatch",
    name: "Provider telemedicine sessions",
    path: `/internal/v1/mobile/provider/telemedicine/sessions?patient_id=${GOLDEN_PATIENT}`,
  },
  {
    journey: "6 Public health + geo",
    name: SOVEREIGN ? "Ndila tile config" : "Ndila tile config (host)",
    path: "/internal/v1/ndila/tiles/config",
    sovereignRequired: true,
  },
  {
    journey: "7 Data & intelligence",
    name: "Integration hub routes",
    path: "/internal/v1/integration-hub/routes",
  },
];

function v11Headers() {
  return {
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-Tenant-ID": TENANT_ID,
    "X-Pod-ID": POD_ID,
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": crypto.randomUUID(),
  };
}

function classify(status, expectOk, check) {
  if (check?.hostOnly && !SOVEREIGN) return "skip";
  if (status >= 200 && status < 300) return "pass";
  if (expectOk) return "fail";
  if (status === 400 || status === 404) return "fail";
  if (status === 502 || status === 503 || status === 500) {
    if (check?.sovereignRequired && !SOVEREIGN) return "warn";
    if (check?.hostOnly) return "skip";
    return "warn";
  }
  return "fail";
}

function rowCount(body) {
  if (!body) return 0;
  if (Array.isArray(body.data)) return body.data.length;
  if (Array.isArray(body)) return body.length;
  if (body?.data?.items && Array.isArray(body.data.items)) return body.data.items.length;
  return 0;
}

async function probe(label, path, expectOk = false, minRows = 0, check = {}) {
  if (check.hostOnly && !SOVEREIGN) {
    return {
      label,
      grade: "skip",
      note: check.note ?? "host-only service — skipped in compose-only mode",
    };
  }

  const url = `${BFF.replace(/\/$/, "")}${path}`;
  try {
    const res = await fetch(url, { headers: v11Headers() });
    let grade = classify(res.status, expectOk, check);
    let note = `HTTP ${res.status}`;

    if (grade === "pass") {
      try {
        const body = await res.json();
        const count = rowCount(body);
        if (count > 0) note += ` · ${count} rows`;
        else if (body?.data && Array.isArray(body.data)) note += ` · ${body.data.length} rows`;
        else if (body?.data?.items && Array.isArray(body.data.items)) note += ` · ${body.data.items.length} items`;
        else if (body?.status) note += ` · ${body.status}`;
        if (minRows > 0 && count < minRows) {
          grade = check.sovereignRequired && !SOVEREIGN ? "warn" : "warn";
          note += ` (expected ≥${minRows} demo rows)`;
        }
      } catch {
        /* non-json health ok */
      }
    } else if (grade === "warn") {
      note += check.note ? ` · ${check.note}` : " · route reachable, upstream sovereign service likely down";
    } else if (grade !== "skip") {
      try {
        const text = await res.text();
        if (text.includes("MISSING_REQUIRED_HEADER")) note += " · missing v1.1 headers";
        else if (text.length < 120) note += ` · ${text.replace(/\s+/g, " ")}`;
      } catch {
        /* ignore */
      }
    }

    return { label, grade, note };
  } catch (err) {
    return { label, grade: "fail", note: err instanceof Error ? err.message : String(err) };
  }
}

console.log(`Wave 20/21 demo journey probe → ${BFF}${SOVEREIGN ? " (--sovereign)" : ""}`);
console.log(`  tenant=${TENANT_ID} pod=${POD_ID}\n`);

const results = [];
for (const check of CHECKS) {
  results.push(await probe(check.name, check.path, check.expectOk, check.minRows ?? 0, check));
}

console.log("\nRx → pay → dispatch path (Wave 20 slice 9):");
const rxResults = [];
for (const check of RX_PATH_CHECKS) {
  rxResults.push(await probe(check.name, check.path, false, check.minRows ?? 0, check));
}
results.push(...rxResults);

console.log("\nSeven production-readiness journeys (Wave 20 slice 11):");
const journeyResults = [];
for (const check of JOURNEY_CHECKS) {
  const row = await probe(
    `${check.journey}: ${check.name}`,
    check.path,
    false,
    check.minRows ?? 0,
    check,
  );
  journeyResults.push(row);
  results.push(row);
}

const ICON = { pass: "PASS", warn: "WARN", fail: "FAIL", skip: "SKIP" };
for (const row of results) {
  console.log(`${(ICON[row.grade] ?? row.grade.toUpperCase()).padEnd(4)}  ${row.label} — ${row.note}`);
}

console.log("\nWeb demo anchors:");
console.log("  Registry:       GET /internal/v1/demo-journeys");
console.log("  Rx journey:     /pharmacy/transaction-journey?patientId=CPID-ZW-00001");
console.log("  Inpatient:      /clinical/inpatient/admissions");
console.log("  Command centre: /production-command-centre");
console.log("  Data pipelines: /data-intelligence/pipelines");
console.log("  Mobile journeys: ProductionReadinessJourneyScreen (provider app)");

const hardFail = results.some((r) => r.grade === "fail");
process.exit(hardFail ? 1 : 0);
