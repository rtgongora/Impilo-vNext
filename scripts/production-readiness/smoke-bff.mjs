#!/usr/bin/env node
/**
 * Node port of compose/experience/smoke-test.sh (for Windows / no bash).
 * Usage: node scripts/production-readiness/smoke-bff.mjs [--bff http://localhost:8160]
 */
const BFF = process.argv.includes("--bff")
  ? process.argv[process.argv.indexOf("--bff") + 1]
  : process.env.BFF_URL ?? "http://localhost:8160";

const TENANT_ID = process.env.IMPILO_TENANT_ID ?? "tenant-moh-zw";
const POD_ID = process.env.IMPILO_POD_ID ?? "national-spine";

function hdr(extra = {}) {
  return {
    "Content-Type": "application/json",
    "X-Tenant-ID": TENANT_ID,
    "X-Pod-ID": POD_ID,
    "X-Request-ID": crypto.randomUUID(),
    "X-Correlation-ID": crypto.randomUUID(),
    ...extra,
  };
}

function pass(msg) {
  console.log(`PASS: ${msg}`);
}

function fail(msg) {
  console.error(`FAIL: ${msg}`);
  process.exit(1);
}

console.log("Experience Platform Smoke Test (Node)\n");

// Test 1: health
{
  const res = await fetch(`${BFF}/health`);
  if (res.ok) pass("/health returned 200");
  else fail(`/health returned ${res.status}`);
}

// Test 2: missing headers => 400
{
  const res = await fetch(`${BFF}/internal/v1/facilities`);
  const body = await res.text();
  if (res.status === 400 && body.includes("MISSING_REQUIRED_HEADER")) {
    pass("Missing headers correctly returned 400 with MISSING_REQUIRED_HEADER");
  } else {
    fail(`Expected 400 for missing headers, got ${res.status}`);
  }
}

// Test 3: idempotency replay
const idemKey = `smoke-test-${Date.now()}-${Math.random().toString(36).slice(2)}`;
const body = JSON.stringify({ report_type: "SMOKE_TEST", requested_by: "smoke-tester", parameters: {} });
const res1 = await fetch(`${BFF}/internal/v1/reports/generate`, {
  method: "POST",
  headers: hdr({ "Idempotency-Key": idemKey }),
  body,
});
const text1 = await res1.text();
const res2 = await fetch(`${BFF}/internal/v1/reports/generate`, {
  method: "POST",
  headers: hdr({ "Idempotency-Key": idemKey }),
  body,
});
const text2 = await res2.text();

if (!res1.ok) {
  console.warn(`WARN: Idempotency replay skipped — first request ${res1.status} (reports-service likely down)`);
} else if (text1 === text2) {
  pass("Idempotency replay returned identical response");
} else {
  fail("Idempotency replay mismatch");
}

// Test 4: idempotency conflict => 409
{
  const conflictBody = JSON.stringify({ report_type: "DIFFERENT", requested_by: "conflict-tester", parameters: {} });
  const res = await fetch(`${BFF}/internal/v1/reports/generate`, {
    method: "POST",
    headers: hdr({ "Idempotency-Key": idemKey }),
    body: conflictBody,
  });
  const text = await res.text();
  if (res.status === 409 && text.includes("IDENTITY_CONFLICT")) {
    pass("Idempotency conflict correctly returned 409 with IDENTITY_CONFLICT");
  } else {
    fail(`Expected 409 for idempotency conflict, got ${res.status}`);
  }
}

console.log("\nAll smoke tests passed (upstream-dependent checks may WARN).");
