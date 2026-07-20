import { describe, expect, it } from "vitest";
import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Phase 6 golden-thread — proves web/BFF wiring for services that were
 * previously `mostly-real` (admin/web-first domains; mobile n/a by doctrine).
 */
describe("Phase 6 service completion golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (rel: string) => readFileSync(resolve(repoRoot, rel), "utf8");

  const cases: Array<{ service: string; hook: string; bff: string }> = [
    { service: "general-ledger-service", hook: "useGeneralLedger.ts", bff: "/internal/v1/erp/gl" },
    { service: "hr-payroll-service", hook: "useHrPayroll.ts", bff: "/internal/v1/erp/hr" },
    { service: "procurement-service", hook: "useProcurement.ts", bff: "/internal/v1/erp/procurement" },
    { service: "product-registry-service", hook: "useProductRegistry.ts", bff: "/internal/v1/product-registry" },
    { service: "data-access-governance-service", hook: "useDataAccessGovernance.ts", bff: "/internal/v1/governance/access" },
    { service: "scheduling-service", hook: "useAppointments.ts", bff: "/internal/v1/appointments" },
    { service: "document-service", hook: "useClinicalDocuments.ts", bff: "/internal/v1/clinical-documents" },
    { service: "forms-service", hook: "useExtensions.ts", bff: "/internal/v1/extensions/forms" },
    { service: "workforce-governance-service", hook: "organization-admin/governance/page.tsx", bff: "/internal/v1/workforce-governance" },
    { service: "tshepo-audit-service", hook: "useAudit.ts", bff: "/internal/v1/admin/audit" },
    { service: "tshepo-consent-service", hook: "useConsent.ts", bff: "/internal/v1/consent" },
    { service: "share-slip-service", hook: "shareSlipPublic.ts", bff: "share-slip" },
  ];

  it.each(cases)("$service hook reaches BFF", ({ hook, bff }) => {
    const hookPath = hook.startsWith("ui/")
      ? hook
      : hook.startsWith("organization-admin/")
        ? `ui/one-ui-shell/src/app/${hook}`
        : hook.includes("/") && !hook.endsWith(".ts")
          ? `ui/one-ui-shell/src/app/${hook}`
          : hook.includes("/")
            ? hook
            : hook.startsWith("share")
              ? `ui/one-ui-shell/src/lib/${hook}`
              : `ui/one-ui-shell/src/hooks/queries/${hook}`;
    expect(existsSync(resolve(repoRoot, hookPath))).toBe(true);
    const text = read(hookPath);
    expect(text).toContain(bff);
  });

  it("experience-bff is the composition layer (controllers + mobile surfaces)", () => {
    const bffRoot = "services/experience-bff/src/main/java";
    expect(existsSync(resolve(repoRoot, bffRoot))).toBe(true);
    const mobileRegistry = read("apps/mobile/packages/mobile-registry/src/wiring.ts");
    expect(mobileRegistry).toContain("experience-bff");
  });

  it("product-truth dataset marks phase6 complete for user-facing services", () => {
    const data = JSON.parse(read("reports/product/product-truth.json"));
    const phase6 = data.summary?.phase6;
    // abis-service became user-facing with the W3e enrolment/adjudication console
    // (90d0d626c) while the heuristic product-truth scanner still flags its
    // BFF/contract wiring — tracked in the identity-plane program. Keep this set
    // SHORT and documented: any OTHER user-facing service regressing fails here.
    const KNOWN_IN_FLIGHT = new Set(["abis-service"]);
    const services = (data.services ?? []) as Array<Record<string, unknown>>;
    const offenders = services
      .filter((s) => s.frontendExpected && s.productStatus !== "real")
      .map((s) => s.id as string);
    expect(offenders.filter((id) => !KNOWN_IN_FLIGHT.has(id))).toEqual([]);
    expect((phase6?.userFacing ?? 0) - (phase6?.userFacingReal ?? 0)).toBeLessThanOrEqual(KNOWN_IN_FLIGHT.size);
    expect(phase6?.complete).toBeGreaterThanOrEqual(phase6?.userFacing ?? 0);
  });
});
