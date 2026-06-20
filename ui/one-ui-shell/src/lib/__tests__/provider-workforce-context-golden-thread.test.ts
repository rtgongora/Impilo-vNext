import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("provider-workforce-context cross-service golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("Vashandi workforce UI uses BFF vashandi surface", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/work/vashandi/workforce/page.tsx"), "utf8");
    const client = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/lib/vashandi/api/client.ts"), "utf8");
    expect(page).toContain("useWorkforceProfiles");
    expect(client).toContain("/internal/v1/vashandi");
  });

  it("provider registry UI reaches registry BFF", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/registry/providers/page.tsx"), "utf8");
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useRegistry.ts"), "utf8");
    expect(page).toContain("useProviders");
    expect(hook).toContain("/internal/v1/registry");
  });

  it("BFF wires Varapi and Vashandi downstream clients", () => {
    const vashandi = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/VashandiController.java"),
      "utf8",
    );
    const registry = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/RegistryController.java"),
      "utf8",
    );
    expect(vashandi).toContain("/internal/v1/vashandi");
    expect(registry).toContain("/internal/v1/registry");
  });
});
