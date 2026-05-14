import fs from "node:fs";
import path from "node:path";

const root = path.resolve(process.cwd(), "..", "..");

const requiredFiles = [
  "docs/learning/LEARNING_PLATFORM.md",
  "docs/learning/PRODUCTION_ROLLOUT_EVIDENCE.md",
  "contracts/openapi/learning.openapi.yaml",
  "helm/learning/Chart.yaml",
  "helm/learning/values.yaml",
  "helm/learning/values.minimal.yaml",
];

let failed = false;

for (const rel of requiredFiles) {
  const abs = path.join(root, rel);
  if (!fs.existsSync(abs)) {
    failed = true;
    console.error(`FAIL: missing required rollout evidence file: ${rel}`);
  } else {
    console.log(`PASS: required rollout evidence file exists: ${rel}`);
  }
}

const evidence = fs.readFileSync(path.join(root, "docs/learning/PRODUCTION_ROLLOUT_EVIDENCE.md"), "utf8");
const requiredMarkers = [
  "Environment UAT Sign-Off Matrix",
  "Monitoring and SLO Drill Evidence",
  "Go-Live Decision Gate",
];
for (const marker of requiredMarkers) {
  if (!evidence.includes(marker)) {
    failed = true;
    console.error(`FAIL: rollout evidence document missing marker: ${marker}`);
  } else {
    console.log(`PASS: rollout evidence marker present: ${marker}`);
  }
}

if (failed) {
  process.exit(1);
}
console.log("LMS rollout evidence checks passed.");
