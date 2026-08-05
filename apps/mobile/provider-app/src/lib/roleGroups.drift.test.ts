/**
 * Keeps the mobile role-group mirror honest against the canonical shell map.
 *
 * `src/lib/roleGroups.ts` mirrors `RAW_ROLE_GROUPS` from
 * `ui/one-ui-shell/src/lib/auth/role-groups.ts`, which is itself aligned with
 * the BFF `SecurityConfig` role-group arrays. A hand-written role list is what
 * produced the dead ModeSwitcher in the first place; this test turns the copy
 * into a checked mirror, so either side changing goes RED here.
 */
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { RAW_ROLE_GROUPS } from "./roleGroups";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, "../../../../..");
const SHELL_ROLE_GROUPS = path.join(REPO_ROOT, "ui/one-ui-shell/src/lib/auth/role-groups.ts");
const SHELL_PRIVILEGED_ROLES = path.join(REPO_ROOT, "ui/one-ui-shell/src/lib/auth/privileged-roles.ts");

const source = readFileSync(SHELL_ROLE_GROUPS, "utf8");

/** Reads one `NAME: ["A", "B"]` entry out of the shell's RAW_ROLE_GROUPS literal. */
function parseShellGroup(name: string): string[] | null {
  const match = new RegExp(String.raw`\n\s*${name}:\s*\[([^\]]*)\]`).exec(source);
  if (!match) return null;
  return [...match[1].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
}

describe("mobile role-group mirror vs ui/one-ui-shell", () => {
  it("positive control: the parser really reads groups out of the shell source", () => {
    // If this ever returns null or something unexpected, every "no drift"
    // assertion below is passing on a parser that found nothing.
    expect(source).toContain("RAW_ROLE_GROUPS");
    expect(parseShellGroup("PRESCRIBER")).toEqual([
      "CLINICIAN",
      "FACILITY_ADMIN",
      "SYSTEM_ADMIN",
      "DEVELOPER",
    ]);
    expect(parseShellGroup("NO_SUCH_GROUP_SHOULD_EXIST")).toBeNull();
  });

  it.each(Object.keys(RAW_ROLE_GROUPS))("%s matches the shell definition exactly", (name) => {
    const shell = parseShellGroup(name);
    expect(shell, `${name} is no longer defined in ${SHELL_ROLE_GROUPS}`).not.toBeNull();
    expect(shell).toEqual([...RAW_ROLE_GROUPS[name as keyof typeof RAW_ROLE_GROUPS]]);
  });

  it("mirrors the shell's SUPER_ADMIN expansion rule", () => {
    const privileged = readFileSync(SHELL_PRIVILEGED_ROLES, "utf8");
    expect(privileged).toContain('"SUPER_ADMIN"');
    expect(privileged).toMatch(/role === "SYSTEM_ADMIN" \|\| role === "DEVELOPER"/);
  });
});
