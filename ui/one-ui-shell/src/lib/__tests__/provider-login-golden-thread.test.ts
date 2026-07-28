import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { resolvePostLoginDestination } from "@/lib/resolve-post-login-destination";

describe("provider-login golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("UI route wires bounded-context client and post-login resolver", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx"), "utf8");
    expect(page).toContain("provider");
    expect(page).toContain("buildPostLoginResolvingPath");
  });

  it("resolving page lands an activated provider on /work (Phase F6 — /provider-workspace is now a shim to it)", () => {
    // A plain string-presence check here would lie: this file's own Phase F6 comment still
    // mentions "/provider-workspace" by name, so grepping for that text would keep passing
    // even if the actual landing target regressed back to the old route. Exercise the real
    // function instead.
    const result = resolvePostLoginDestination({
      user: { id: "u1", actorType: "PROVIDER", roles: ["CLINICIAN"], providerActivated: true, providerId: "PRV-1" },
      linkedIds: { providerStatus: "ACTIVE", licenceValid: true },
      workAssignments: [
        {
          assignmentId: "A1",
          subjectId: "PRV-1",
          subjectType: "provider_worker",
          contextType: "facility_clinical",
          assignmentType: "facility_assignment",
          assignmentStatus: "active",
          facilityId: "F1",
        },
      ],
      hasFacility: true,
    });
    expect(result.href).toBe("/work");

    const resolver = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/lib/resolve-post-login-destination.ts"),
      "utf8",
    );
    expect(resolver).toContain("my_life");

    const resolving = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/auth/resolving/page.tsx"),
      "utf8",
    );
    expect(resolving).toContain("resolvePostLoginDestination");
  });

  it("hook calls BFF sovereign proxy", () => {
    const hook = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useAuth.ts"), "utf8");
    expect(hook).toContain("/internal/v1/auth");
  });

  it("BFF controller exposes journey endpoints", () => {
    const controller = readFileSync(resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AuthSessionController.java"), "utf8");
    expect(controller).toContain("/internal/v1");
    expect(controller).toContain("auth");
  });
});
