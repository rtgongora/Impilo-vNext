import { describe, expect, it } from "vitest";
import { ENTERPRISE_NAV_ITEMS, filterEnterpriseNavItems } from "../enterprise-resource-nav";

describe("filterEnterpriseNavItems", () => {
  it("hides facility-scoped items when no facility is selected", () => {
    const hasRole = () => true;
    const filtered = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, hasRole, false);
    const hrefs = filtered.map((i) => i.href);
    expect(hrefs).toContain("/enterprise");
    expect(hrefs).not.toContain("/inventory");
    expect(hrefs).not.toContain("/pharmacy/stock");
    expect(hrefs).not.toContain("/inventory/requisitions");
    expect(hrefs).not.toContain("/enterprise/charge-sheet");
  });

  it("shows facility-scoped links when a facility is active", () => {
    const hasRole = () => true;
    const filtered = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, hasRole, true);
    const hrefs = filtered.map((i) => i.href);
    expect(hrefs).toContain("/inventory");
    expect(hrefs).toContain("/inventory/requisitions");
  });

  it("restricts pharmacy stock to dispenser-capable roles", () => {
    const hasRole = (r: string) => r === "CLINICIAN";
    const filtered = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, hasRole, true);
    expect(filtered.some((i) => i.href === "/pharmacy/stock")).toBe(false);
  });

  it("shows pharmacy stock for pharmacists", () => {
    const hasRole = (r: string) => r === "PHARMACIST";
    const filtered = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, hasRole, true);
    expect(filtered.some((i) => i.href === "/pharmacy/stock")).toBe(true);
  });

  it("shows charge sheet only for clinical-capable roles", () => {
    const clinician = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, (r) => r === "CLINICIAN", true);
    expect(clinician.some((i) => i.href === "/enterprise/charge-sheet")).toBe(true);

    const financeOnly = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, (r) => r === "FINANCE", true);
    expect(financeOnly.some((i) => i.href === "/enterprise/charge-sheet")).toBe(false);
  });

  it("shows finance workspace links only for finance-capable roles", () => {
    const hasRole = (r: string) => ["FINANCE", "FACILITY_ADMIN"].includes(r);
    const filtered = filterEnterpriseNavItems(ENTERPRISE_NAV_ITEMS, hasRole, true);
    expect(filtered.some((i) => i.href === "/finance")).toBe(true);
    expect(filtered.some((i) => i.href === "/finance/claims")).toBe(true);
  });
});
