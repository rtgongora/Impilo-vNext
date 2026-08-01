#!/usr/bin/env node
/**
 * Field-by-field / enum-by-enum semantic parity check for tshepo.trust.v1 across:
 *   1. Java records   libs/tshepo-contracts/.../contracts/v1/*.java
 *   2. TypeScript     contracts/trust-decision/v1.ts
 *   3. JSON Schema    contracts/schemas/trust-decision-v1.schema.json
 *   4. OpenAPI        contracts/openapi/tshepo-trust-decision-v1.openapi.yaml
 *
 * Any drift (missing type, missing/extra field, enum constant mismatch) fails
 * the run with a per-type diff. Wire names are compared: a Java @JsonProperty
 * override wins over the record component name.
 */
import fs from "node:fs";
import path from "node:path";
import yaml from "js-yaml";

const ROOT = path.resolve(import.meta.dirname, "..", "..");
const JAVA_DIR = path.join(
  ROOT,
  "libs/tshepo-contracts/src/main/java/zw/gov/mohcc/impilo/tshepo/contracts/v1",
);
const TS_FILE = path.join(ROOT, "contracts/trust-decision/v1.ts");
const SCHEMA_FILE = path.join(ROOT, "contracts/schemas/trust-decision-v1.schema.json");
const OPENAPI_FILE = path.join(ROOT, "contracts/openapi/tshepo-trust-decision-v1.openapi.yaml");

const OBJECT_TYPES = [
  "TrustChallengeOutcome",
  "TrustDecisionInput",
  "IdentityAssurance",
  "AuthenticationAssurance",
  "WorkloadSubject",
  "WorkloadAssurance",
  "HumanSubjectRef",
  "ActiveContext",
  "AuthorityBinding",
  "DelegatedHumanContext",
  "DelegationChain",
  "ServiceRequestContext",
  "ConsentDecision",
  "LawfulBasisDecision",
  "AsyncExecutionReference",
  "TrustAuditEvent",
];

const ENUM_TYPES = [
  "TrustChallengeDecision",
  "RecoveryAuthenticationState",
  "LawfulBasisType",
  "OperatingMode",
];

function stripComments(src) {
  return src.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/[^\n]*/g, "");
}

// ---------- Java ----------
function parseJavaRecord(typeName) {
  const file = path.join(JAVA_DIR, `${typeName}.java`);
  const src = stripComments(fs.readFileSync(file, "utf8"));
  const start = src.indexOf(`public record ${typeName}(`);
  if (start < 0) throw new Error(`no record declaration in ${file}`);
  const open = src.indexOf("(", start);
  let depth = 0;
  let end = -1;
  for (let i = open; i < src.length; i++) {
    if (src[i] === "(") depth++;
    else if (src[i] === ")") {
      depth--;
      if (depth === 0) {
        end = i;
        break;
      }
    }
  }
  const body = src.slice(open + 1, end);
  const fields = [];
  for (const raw of body.split(",")) {
    const part = raw.trim();
    if (!part) continue;
    const jsonProp = part.match(/@JsonProperty\("([^"]+)"\)/);
    const nameMatch = part.match(/([A-Za-z_$][\w$]*)\s*$/);
    if (!nameMatch) continue;
    fields.push(jsonProp ? jsonProp[1] : nameMatch[1]);
  }
  return fields;
}

function parseJavaEnum(typeName) {
  const file = path.join(JAVA_DIR, `${typeName}.java`);
  const src = stripComments(fs.readFileSync(file, "utf8"));
  const m = src.match(new RegExp(`enum\\s+${typeName}\\s*\\{([\\s\\S]*?)[;}]`));
  if (!m) throw new Error(`no enum body in ${file}`);
  return m[1]
    .split(",")
    .map((s) => s.trim())
    .filter((s) => /^[A-Z][A-Z0-9_]*$/.test(s));
}

// Java splits on "," which breaks generic parameters like Map<String, String>.
// Re-parse line-wise instead for records whose components use generics.
function parseJavaRecordLinewise(typeName) {
  const file = path.join(JAVA_DIR, `${typeName}.java`);
  const src = stripComments(fs.readFileSync(file, "utf8"));
  const lines = src.split("\n");
  const startIdx = lines.findIndex((l) => l.includes(`public record ${typeName}(`));
  if (startIdx < 0) throw new Error(`no record declaration in ${file}`);
  const fields = [];
  for (let i = startIdx + 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith(")")) break;
    if (!line) continue;
    const jsonProp = line.match(/@JsonProperty\("([^"]+)"\)/);
    const nameMatch = line.match(/([A-Za-z_$][\w$]*)\s*,?\s*$/);
    if (!nameMatch) continue;
    fields.push(jsonProp ? jsonProp[1] : nameMatch[1]);
  }
  return fields;
}

// ---------- TypeScript ----------
function parseTsInterfaces(src) {
  const result = new Map();
  const re = /export interface (\w+)\s*\{([\s\S]*?)\n\}/g;
  let m;
  while ((m = re.exec(src)) !== null) {
    const fields = [];
    for (const raw of m[2].split("\n")) {
      const line = raw.trim();
      const fm = line.match(/^(\w+)\??:\s*/);
      if (fm) fields.push(fm[1]);
    }
    result.set(m[1], fields);
  }
  return result;
}

function parseTsUnions(src) {
  const result = new Map();
  const re = /export type (\w+)\s*=\s*([\s\S]*?);/g;
  let m;
  while ((m = re.exec(src)) !== null) {
    const values = [...m[2].matchAll(/"([A-Z0-9_]+)"/g)].map((x) => x[1]);
    if (values.length > 0) result.set(m[1], values);
  }
  return result;
}

// ---------- main ----------
const tsSrc = stripComments(fs.readFileSync(TS_FILE, "utf8"));
const tsInterfaces = parseTsInterfaces(tsSrc);
const tsUnions = parseTsUnions(tsSrc);
const schema = JSON.parse(fs.readFileSync(SCHEMA_FILE, "utf8"));
const openapi = yaml.load(fs.readFileSync(OPENAPI_FILE, "utf8"));
const oaSchemas = openapi?.components?.schemas ?? {};

let failures = 0;
const report = [];

function compareSets(typeName, kind, byRepr) {
  const union = new Set();
  for (const values of Object.values(byRepr)) {
    if (values) for (const v of values) union.add(v);
  }
  const missing = [];
  for (const [repr, values] of Object.entries(byRepr)) {
    if (values === null) {
      missing.push(`${repr}: TYPE MISSING`);
      continue;
    }
    const set = new Set(values);
    for (const v of union) {
      if (!set.has(v)) missing.push(`${repr}: missing '${v}'`);
    }
  }
  if (missing.length > 0) {
    failures++;
    report.push(`DRIFT ${kind} ${typeName}:\n  ${missing.join("\n  ")}`);
  } else {
    report.push(
      `OK ${kind} ${typeName}: ${union.size} ${kind === "enum" ? "constants" : "fields"} identical across java/ts/schema/openapi`,
    );
  }
}

for (const typeName of OBJECT_TYPES) {
  let java = null;
  try {
    java = parseJavaRecordLinewise(typeName);
  } catch (e) {
    report.push(`ERROR parsing Java ${typeName}: ${e.message}`);
    failures++;
  }
  const ts = tsInterfaces.get(typeName) ?? null;
  const sch = schema.$defs?.[typeName]?.properties
    ? Object.keys(schema.$defs[typeName].properties)
    : null;
  const oa = oaSchemas[typeName]?.properties ? Object.keys(oaSchemas[typeName].properties) : null;
  compareSets(typeName, "object", { java, ts, schema: sch, openapi: oa });
}

for (const typeName of ENUM_TYPES) {
  let java = null;
  try {
    java = parseJavaEnum(typeName);
  } catch (e) {
    report.push(`ERROR parsing Java enum ${typeName}: ${e.message}`);
    failures++;
  }
  const ts = tsUnions.get(typeName) ?? null;
  const sch = schema.$defs?.[typeName]?.enum ?? null;
  const oa = oaSchemas[typeName]?.enum ?? null;
  compareSets(typeName, "enum", { java, ts, schema: sch, openapi: oa });
}

// Additional cross-checks: contract version constant must agree everywhere.
const versions = new Set([
  tsSrc.match(/TRUST_CONTRACT_VERSION_V1 = "([^"]+)"/)?.[1],
  schema.$defs?.contractVersion?.const,
  openapi?.info?.version,
  stripComments(fs.readFileSync(path.join(JAVA_DIR, "TrustContractVersion.java"), "utf8")).match(
    /V1\s*=\s*"([^"]+)"/,
  )?.[1],
]);
if (versions.size !== 1 || !versions.has("tshepo.trust.v1")) {
  failures++;
  report.push(`DRIFT contract version constant: ${JSON.stringify([...versions])}`);
} else {
  report.push("OK contract version constant: tshepo.trust.v1 in all four representations");
}

console.log(report.join("\n"));
console.log(
  `\ntrust-contract-parity: ${OBJECT_TYPES.length} object types + ${ENUM_TYPES.length} enums checked, ${failures} drift(s)`,
);
process.exit(failures === 0 ? 0 : 1);
