#!/usr/bin/env node
/**
 * Remove COMPLETION_EVIDENCE entries that fail product-truth bar
 * (source-only golden threads, known stubs, or unresolved gap overrides).
 */
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), "../..");
const mapsPath = path.join(root, "scripts/product/generate-core-transaction-maps.mjs");
let content = fs.readFileSync(mapsPath, "utf8");

/** Journeys to downgrade — metric-only or failed product-truth walk/code audit */
const DOWNGRADE = new Set([
  // High-risk walk failures (stubs / RTC partial)
  "payment-billing-claim",
  "telemedicine-encounter",
  // Bulk metric-only promotions (source-file golden thread only)
  "chronic-care",
  "patient-search-selection",
  "facility-context-selection",
  "workspace-context-selection",
  "provider-login",
  "credential-verification",
  "provider-registry-onboarding",
  "registry-administration",
  "integration-sync-replay",
  "notification-comms",
  "reporting-dashboard",
  "marketplace-order",
  "dispatch-delivery",
  "offline-clinical-queue",
  "ai-guidance-nompilo",
  "surveillance-outbreak",
  "fundo-learning",
  "social-community",
  "public-health-outreach",
  "crvs-ubomi",
  "citizen-onboarding",
  // Consent still has mvumo admin depth gap
  "consent-capture",
]);

const blockRe = /  "([a-z0-9-]+)": \{\n[\s\S]*?\n  \},\n/g;
let removed = 0;
content = content.replace(blockRe, (match, id) => {
  if (!DOWNGRADE.has(id)) return match;
  // Only strip inside COMPLETION_EVIDENCE (bffEndpoints guard)
  if (!match.includes("bffEndpoints")) return match;
  removed++;
  console.log("downgraded", id);
  return "";
});

if (removed === 0) {
  console.error("No evidence entries removed — pattern mismatch?");
  process.exit(1);
}

fs.writeFileSync(mapsPath, content);
console.log(`Removed ${removed} COMPLETION_EVIDENCE entries`);
