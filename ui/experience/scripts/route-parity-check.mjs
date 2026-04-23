#!/usr/bin/env node

/**
 * Route Parity Test Script
 *
 * Asserts that the Next.js App Router file tree contains page files
 * for all routes defined in the route registry (src/lib/routes.ts).
 *
 * This is a static file-system check — it does not require the app to be running.
 */

import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const APP_DIR = path.resolve(__dirname, "../src/app");

// Route definitions (extracted from routes.ts — keep in sync)
const EXPECTED_ROUTES = [
  "/auth/login",
  "/auth/login/email",
  "/auth/login/provider-id",
  "/auth/login/biometric",
  "/auth/forgot-password",
  "/auth/reset-password",
  "/auth/mfa",
  "/auth/logout",
  "/",
  "/home",
  "/home/notifications",
  "/home/profile",
  "/home/preferences",
  "/facility",
  "/facility/[id]",
  "/workspace",
  "/workspace/[id]",
  "/shift",
  "/shift/active",
  "/shift/handover",
  "/scheduling/roster",
  "/scheduling/on-call",
  "/scheduling/noticeboard",
  "/queue",
  "/queue/triage",
  "/queue/waiting",
  "/queue/search",
  "/queue/walk-in",
  "/queue/scheduled",
  "/ehr/[patientId]",
  "/ehr/[patientId]/summary",
  "/ehr/[patientId]/vitals",
  "/ehr/[patientId]/history",
  "/ehr/[patientId]/conditions",
  "/ehr/[patientId]/medications",
  "/ehr/[patientId]/allergies",
  "/ehr/[patientId]/orders",
  "/ehr/[patientId]/results",
  "/ehr/[patientId]/notes",
  "/ehr/[patientId]/documents",
  "/ehr/[patientId]/encounters",
  "/ehr/[patientId]/encounter/[encounterId]",
  "/ehr/[patientId]/immunizations",
  "/ehr/[patientId]/referrals",
  "/ehr/[patientId]/timeline",
  "/admin",
  "/admin/users",
  "/admin/users/[id]",
  "/admin/roles",
  "/admin/policies",
  "/admin/audit",
  "/admin/audit/[id]",
  "/admin/consent",
  "/admin/devices",
  "/admin/keys",
  "/admin/federation",
  "/admin/tenants",
  "/admin/break-glass",
  "/registry",
  "/registry/clients",
  "/registry/trust",
  "/registry/providers",
  "/registry/providers/[id]",
  "/registry/provider-council/self-service",
  "/registry/provider-council/council-workspace",
  "/registry/facilities",
  "/registry/facilities/[id]",
  "/registry/terminology",
  "/registry/terminology/[id]",
  "/registry/products",
  "/registry/products/[id]",
  "/registry-admin",
  "/organization-admin",
  "/organization-admin/facility",
  "/organization-admin/staffing",
  "/organization-admin/governance",
  "/organization-admin/governance/[id]",
  "/marketplace",
  "/marketplace/catalog",
  "/marketplace/orders",
  "/marketplace/orders/[id]",
  "/marketplace/vendors",
  "/marketplace/bookings",
  "/finance",
  "/finance/claims",
  "/finance/claims/[id]",
  "/finance/billing",
  "/finance/payments",
  "/finance/tariffs",
  "/pharmacy",
  "/pharmacy/dispense",
  "/pharmacy/stock",
  "/pharmacy/prescriptions",
  "/inventory",
  "/inventory/movements",
  "/inventory/counts",
  "/inventory/requisitions",
  "/reports",
  "/reports/facility",
  "/reports/clinical",
  "/reports/operational",
  "/reports/custom",
  "/reports/[id]",
  "/settings",
  "/settings/account",
  "/settings/security",
  "/settings/notifications",
  "/settings/display",
  "/settings/integrations",
];

function routeToPagePath(route) {
  if (route === "/") return path.join(APP_DIR, "page.tsx");
  return path.join(APP_DIR, route, "page.tsx");
}

let passed = 0;
let failed = 0;
const missing = [];

for (const route of EXPECTED_ROUTES) {
  const pagePath = routeToPagePath(route);
  if (fs.existsSync(pagePath)) {
    passed++;
  } else {
    failed++;
    missing.push(route);
  }
}

console.log(`\nRoute Parity Check Results`);
console.log(`========================`);
console.log(`Total expected routes: ${EXPECTED_ROUTES.length}`);
console.log(`Pages found:          ${passed}`);
console.log(`Pages missing:        ${failed}`);

if (missing.length > 0) {
  console.log(`\nMissing routes:`);
  for (const route of missing) {
    console.log(`  - ${route}`);
  }
  process.exit(1);
} else {
  console.log(`\nAll routes have corresponding page files.`);
  process.exit(0);
}
