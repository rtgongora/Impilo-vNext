import { describe, expect, it } from "vitest";
import { pickField, unwrapCollection } from "./normalize-collection";

describe("normalize-collection", () => {
  it("unwraps JSON:API data arrays", () => {
    const rows = unwrapCollection({
      data: [{ id: "1", attributes: { status: "OPEN", name: "Alpha" } }],
    });
    expect(rows).toHaveLength(1);
    expect(rows[0].status).toBe("OPEN");
    expect(rows[0].name).toBe("Alpha");
  });

  it("picks first available field", () => {
    expect(pickField({ employeeId: "E-1", name: "Jane" }, "employeeId", "id")).toBe("E-1");
    expect(pickField({}, "missing")).toBe("—");
  });
});
