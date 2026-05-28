import { describe, expect, it } from "vitest";
import {
  classifyTelemedicineFacilityRole,
  filterTelemedicineSessionsByLens,
  partitionTelemedicineSessionsByFacilityHost,
} from "./telemedicine-facility-lens";
import type { TelemedicineSession } from "@/hooks/queries/useTelemedicine";

function session(
  attrs: Partial<TelemedicineSession["attributes"]>,
  id = "s1",
): TelemedicineSession {
  return {
    id,
    type: "TelemedicineSession",
    attributes: {
      encounter_id: null,
      patient_id: "p1",
      provider_id: "pr1",
      facility_id: null,
      session_type: "VIDEO",
      status: "SCHEDULED",
      room_url: null,
      scheduled_at: null,
      started_at: null,
      ended_at: null,
      duration_seconds: null,
      notes: null,
      referral_id: null,
      created_at: "",
      updated_at: "",
      ...attrs,
    },
  };
}

describe("telemedicine facility lens", () => {
  it("classifies hosted vs remote vs unknown", () => {
    expect(classifyTelemedicineFacilityRole(session({ facility_id: "f-a" }), "f-a")).toBe("hosted_here");
    expect(classifyTelemedicineFacilityRole(session({ facility_id: "f-b" }), "f-a")).toBe("remote_host");
    expect(classifyTelemedicineFacilityRole(session({ facility_id: null }), "f-a")).toBe("facility_unknown");
    expect(classifyTelemedicineFacilityRole(session({ facility_id: "f-a" }), undefined)).toBe("facility_unknown");
  });

  it("partitions lists", () => {
    const parts = partitionTelemedicineSessionsByFacilityHost(
      [session({ facility_id: "here" }, "1"), session({ facility_id: "there" }, "2"), session({ facility_id: null }, "3")],
      "here",
    );
    expect(parts.hostedHere.map((s) => s.id)).toEqual(["1"]);
    expect(parts.remoteHost.map((s) => s.id)).toEqual(["2"]);
    expect(parts.unknownFacility.map((s) => s.id)).toEqual(["3"]);
  });

  it("filters by lens", () => {
    const list = [session({ facility_id: "f1" }, "1"), session({ facility_id: "f2" }, "2")];
    expect(filterTelemedicineSessionsByLens(list, "all", "f1").map((s) => s.id)).toEqual(["1", "2"]);
    expect(filterTelemedicineSessionsByLens(list, "hosted_here", "f1").map((s) => s.id)).toEqual(["1"]);
    expect(filterTelemedicineSessionsByLens(list, "remote_host", "f1").map((s) => s.id)).toEqual(["2"]);
  });
});
