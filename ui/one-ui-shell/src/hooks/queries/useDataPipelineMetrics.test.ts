import { describe, expect, it, vi } from "vitest";
import { extractDeadLetterTotal } from "@/hooks/queries/useDataPipelineMetrics";

describe("useDataPipelineMetrics helpers", () => {
  it("extracts dead letter totals from page payloads", () => {
    expect(extractDeadLetterTotal({ totalElements: 7 })).toBe(7);
    expect(extractDeadLetterTotal({ content: [{ id: 1 }] })).toBe(1);
    expect(extractDeadLetterTotal(null)).toBeNull();
  });
});

// export helper for test — need to export extractDeadLetterTotal from hook file
