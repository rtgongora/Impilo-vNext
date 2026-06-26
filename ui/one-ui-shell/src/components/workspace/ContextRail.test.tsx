import { describe, expect, it } from "vitest";
import { activeAssignment, whereFromAssignment } from "./ContextRail";
import type { WorkContextAssignment } from "@/hooks/queries/useWorkContext";

function assignment(partial: Partial<WorkContextAssignment>): WorkContextAssignment {
  return {
    assignmentId: "a1",
    status: "ACTIVE",
    organisationId: null,
    facilityId: null,
    departmentId: null,
    unitId: null,
    workspaceId: null,
    programmeId: null,
    roleTemplateId: null,
    supervisorProfileId: null,
    scope: "FACILITY",
    eligibilityStatus: null,
    ...partial,
  };
}

describe("activeAssignment", () => {
  it("returns null when there are no assignments", () => {
    expect(activeAssignment([], "fac-1")).toBeNull();
  });

  it("returns the sole assignment regardless of facility", () => {
    const a = assignment({ assignmentId: "only" });
    expect(activeAssignment([a], null)?.assignmentId).toBe("only");
    expect(activeAssignment([a], "fac-x")?.assignmentId).toBe("only");
  });

  it("prefers the assignment that matches the entered facility", () => {
    const a = assignment({ assignmentId: "a", facilityId: "fac-1" });
    const b = assignment({ assignmentId: "b", facilityId: "fac-2" });
    expect(activeAssignment([a, b], "fac-2")?.assignmentId).toBe("b");
  });

  it("returns null when ambiguous (>1 candidate, no facility match)", () => {
    const a = assignment({ assignmentId: "a", facilityId: "fac-1" });
    const b = assignment({ assignmentId: "b", facilityId: "fac-2" });
    expect(activeAssignment([a, b], "fac-9")).toBeNull();
    expect(activeAssignment([a, b], null)).toBeNull();
  });
});

describe("whereFromAssignment", () => {
  it("maps every WHERE dimension and role from the C2 assignment", () => {
    const where = whereFromAssignment(
      assignment({
        scope: "WARD",
        departmentId: "dept-1",
        unitId: "ward-3",
        workspaceId: "sp-7",
        programmeId: "pool-2",
        roleTemplateId: "role-nurse",
      }),
    );
    expect(where).toEqual({
      scope: "WARD",
      departmentLabel: "dept-1",
      unitLabel: "ward-3",
      servicePointLabel: "sp-7",
      virtualPoolLabel: "pool-2",
      roleLabel: "role-nurse",
    });
  });

  it("omits unset dimensions rather than inventing labels", () => {
    const where = whereFromAssignment(assignment({ scope: "FACILITY", facilityId: "fac-1" }));
    expect(where.scope).toBe("FACILITY");
    expect(where.departmentLabel).toBeUndefined();
    expect(where.unitLabel).toBeUndefined();
    expect(where.servicePointLabel).toBeUndefined();
    expect(where.virtualPoolLabel).toBeUndefined();
    expect(where.roleLabel).toBeUndefined();
  });
});
