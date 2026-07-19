import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Golden thread for surfacing Vashandi + reflecting the identity work.
 *
 * Discoverability: the orphaned /work/vashandi pages are now reachable — a sidebar
 * work-zone entry, registered routes, and a start-menu quick action.
 * ID work: the assignment engagement type + validity + provider-access provenance
 * thread from the vashandi API (create) through the UI type into the page.
 */
describe("vashandi surfacing + ID-work golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the sidebar work zone surfaces /work/vashandi (WORKFORCE_ROLES-gated)", () => {
    const zones = read("ui/one-ui-shell/src/components/navigation/sidebar-zones.ts");
    expect(zones).toContain('href: "/work/vashandi"');
    expect(zones).toContain("WORKFORCE_ROLES");
  });

  it("the vashandi pages are route-governed (via the admin-governance scaffold)", () => {
    // Route governance already exists through ...ADMINISTRATION_GOVERNANCE_ROUTES,
    // spread into ROUTES — the surfacing gap was nav, not registration.
    const registry = read("ui/one-ui-shell/src/lib/administration-governance/route-registry.ts");
    expect(registry).toContain("/work/vashandi");
    expect(registry).toContain("/work/vashandi/assignments");
  });

  it("cmd-vashandi is a start-menu quick action", () => {
    const registry = read("ui/one-ui-shell/src/lib/shell/app-registry.ts");
    expect(registry).toMatch(/QUICK_ACTION_IDS[\s\S]*?"cmd-vashandi"/);
  });

  it("the assignment type + engagement constants carry engagementType", () => {
    const types = read("ui/one-ui-shell/src/lib/vashandi/types.ts");
    expect(types).toContain("engagementType");
    expect(types).toContain("ENGAGEMENT_TYPES");
  });

  it("the assignments page shows engagement + provider-access provenance", () => {
    const page = read("ui/one-ui-shell/src/app/work/vashandi/assignments/page.tsx");
    expect(page).toContain("engagement-badge");
    expect(page).toContain("provider-access-provenance");
    expect(page).toContain("VARAPI_ACCESS_REQUEST:");
  });

  it("the hub explains the identity -> session chain and links the work-session hub", () => {
    const hub = read("ui/one-ui-shell/src/app/work/vashandi/page.tsx");
    expect(hub).toContain("/provider/workplace");
  });

  it("the vashandi API accepts engagementType on assignment create", () => {
    const dto = read(
      "services/vashandi-workforce-service/src/main/java/zw/gov/mohcc/impilo/vashandi/api/VashandiDtos.java",
    );
    expect(dto).toContain("engagementType");
    const svc = read(
      "services/vashandi-workforce-service/src/main/java/zw/gov/mohcc/impilo/vashandi/core/WorkforceAssignmentService.java",
    );
    expect(svc).toContain("setEngagementType(request.engagementType())");
  });
});
