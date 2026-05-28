#!/usr/bin/env node

/**
 * Wave 21 — wait for sovereign overlay services before running journey probes.
 *
 * Usage:
 *   node scripts/production-readiness/wait-sovereign-health.mjs [--bff http://localhost:8160]
 */

const BFF = process.argv.includes("--bff")
  ? process.argv[process.argv.indexOf("--bff") + 1]
  : process.env.BFF_URL ?? "http://localhost:8160";

const SERVICES = [
  { name: "BFF", url: `${BFF.replace(/\/$/, "")}/actuator/health` },
  { name: "Pharmacy", url: "http://localhost:8096/actuator/health" },
  { name: "MusheX", url: "http://localhost:8102/actuator/health" },
  { name: "Costa", url: "http://localhost:8101/actuator/health" },
  { name: "Dispatch", url: "http://localhost:8320/actuator/health" },
  { name: "Surveillance", url: "http://localhost:8180/actuator/health" },
  { name: "Ndila", url: "http://localhost:8155/actuator/health" },
  { name: "Nhume", url: "http://localhost:8210/actuator/health" },
  { name: "Wellness", url: "http://localhost:8161/actuator/health" },
];

const MAX_SECONDS = Number(process.env.SOVEREIGN_WAIT_SECONDS ?? 180);
const INTERVAL_MS = 3000;

async function healthy(url) {
  try {
    const res = await fetch(url, { signal: AbortSignal.timeout(4000) });
    if (!res.ok) return false;
    const body = await res.json().catch(() => null);
    if (body?.status === "UP") return true;
    return res.ok;
  } catch {
    return false;
  }
}

async function waitFor(label, url) {
  const deadline = Date.now() + MAX_SECONDS * 1000;
  while (Date.now() < deadline) {
    if (await healthy(url)) {
      console.log(`  OK   ${label}`);
      return true;
    }
    await new Promise((r) => setTimeout(r, INTERVAL_MS));
  }
  console.error(`  FAIL ${label} (timeout after ${MAX_SECONDS}s)`);
  return false;
}

console.log(`Waiting for sovereign stack (max ${MAX_SECONDS}s)…`);
let allOk = true;
for (const svc of SERVICES) {
  const ok = await waitFor(svc.name, svc.url);
  if (!ok) allOk = false;
}
process.exit(allOk ? 0 : 1);
