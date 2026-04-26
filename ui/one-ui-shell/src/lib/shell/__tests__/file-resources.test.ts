import { describe, expect, it } from "vitest";
import {
  mergeFileResources,
  personalDocumentsToFileResources,
  recentItemsToFileResources,
} from "../file-resources";
import type { RecentItem } from "../types";

describe("file-resources", () => {
  it("maps personal documents to FileResource", () => {
    const rows = personalDocumentsToFileResources([
      {
        id: "d1",
        title: "Lab result",
        originalFilename: "lab.pdf",
        documentTypeCode: "LAB",
        mimeType: "application/pdf",
        lifecycleState: "ACTIVE",
        createdAt: "2026-01-01",
        fileSize: 100,
      },
    ]);
    expect(rows[0].resourceType).toBe("personal_document");
    expect(rows[0].id).toBe("personal:d1");
  });

  it("maps resource-like recent items", () => {
    const items: RecentItem[] = [
      {
        id: "r1",
        kind: "resource",
        title: "Export",
        href: "/home/documents",
        refKey: "download:abc",
        recordedAt: "2026-01-01",
      },
    ];
    const rows = recentItemsToFileResources(items);
    expect(rows.length).toBeGreaterThanOrEqual(1);
  });

  it("mergeFileResources dedupes by id and resourceType", () => {
    const a = personalDocumentsToFileResources([
      {
        id: "x",
        title: "Same",
        originalFilename: "s.pdf",
        documentTypeCode: "T",
        mimeType: "application/pdf",
        lifecycleState: "ACTIVE",
        createdAt: "2026-01-01",
        fileSize: 1,
      },
    ]);
    const merged = mergeFileResources(a, a);
    expect(merged).toHaveLength(1);
  });
});
