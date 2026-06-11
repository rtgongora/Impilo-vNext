#!/usr/bin/env node
/**
 * Mobile service wiring guard — validates wiring metadata completeness.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");

const wiringPath = path.join(
  repoRoot,
  "apps/mobile/packages/mobile-registry/src/wiring.ts"
);

const STATE_FLAGS = [
  "supportsLoadingState: true",
  "supportsEmptyState: true",
  "supportsErrorState: true",
  "supportsUnauthorizedState: true",
  "supportsOfflineState: true",
];

let fail = 0;

function failMsg(msg) {
  console.error(`FAIL: ${msg}`);
  fail = 1;
}

function pass(msg) {
  console.log(`PASS: ${msg}`);
}

if (!fs.existsSync(wiringPath)) {
  failMsg(`missing wiring file: ${wiringPath}`);
  process.exit(1);
}

const wiringSrc = fs.readFileSync(wiringPath, "utf8");

for (const flag of STATE_FLAGS) {
  if (!wiringSrc.includes(flag)) {
    failMsg(`wiring helper missing default ${flag}`);
  }
}

const citizenNav = path.join(
  repoRoot,
  "apps/mobile/citizen-app/src/navigation/citizenServiceNavigation.ts"
);
const providerNav = path.join(
  repoRoot,
  "apps/mobile/provider-app/src/navigation/providerServiceNavigation.ts"
);

for (const navFile of [citizenNav, providerNav]) {
  if (!fs.existsSync(navFile)) {
    failMsg(`missing service navigation: ${navFile}`);
    continue;
  }
  const navSrc = fs.readFileSync(navFile, "utf8");
  if (!navSrc.includes("MOBILE_SERVICE_WIRING")) {
    failMsg(`${navFile} must import wiring from mobile-registry`);
  }
  if (!navSrc.includes("buildCitizenServiceCards") && !navSrc.includes("buildProviderServiceCards")) {
    failMsg(`${navFile} missing service card builder`);
  }
}

const homeScreen = path.join(repoRoot, "apps/mobile/citizen-app/src/screens/HomeScreen.tsx");
const providerDash = path.join(
  repoRoot,
  "apps/mobile/provider-app/src/screens/provider/ProviderDashboardScreen.tsx"
);

for (const screen of [homeScreen, providerDash]) {
  if (!fs.existsSync(screen)) {
    failMsg(`missing dashboard screen: ${screen}`);
    continue;
  }
  const src = fs.readFileSync(screen, "utf8");
  if (!src.includes("ServiceCard")) {
    failMsg(`${screen} must render registry-backed ServiceCard components`);
  }
  if (!src.includes("service-hub")) {
    failMsg(`${screen} missing service hub testID`);
  }
}

if (fail) {
  console.error("\nMobile service wiring guard: FAILED");
  process.exit(1);
}

pass("wiring metadata, navigation builders, and dashboard service hubs verified");
process.exit(0);
