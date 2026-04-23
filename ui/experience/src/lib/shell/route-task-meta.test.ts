import { describe, expect, it } from "vitest";
import { deriveRouteTaskMeta } from "@/lib/shell/route-task-meta";

describe("deriveRouteTaskMeta", () => {
  it("tags DICOM viewer under EHR as dicom_viewer task", () => {
    const meta = deriveRouteTaskMeta("/ehr/patient-42/imaging/viewer");
    expect(meta.taskType).toBe("dicom_viewer");
    expect(meta.title).toBe("DICOM viewer");
    expect(meta.contextRef).toBe("patient:patient-42:dicom-viewer");
  });

  it("keeps generic EHR paths as patient_chart", () => {
    const meta = deriveRouteTaskMeta("/ehr/patient-42/imaging");
    expect(meta.taskType).toBe("patient_chart");
    expect(meta.title).toContain("imaging");
  });
});
