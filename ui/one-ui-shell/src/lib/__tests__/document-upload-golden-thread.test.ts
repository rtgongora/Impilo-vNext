import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("document upload golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("documents page uses clinical document hooks and file upload mutation", () => {
    const page = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/ehr/[patientId]/documents/page.tsx"),
      "utf8",
    );
    const hooks = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/hooks/queries/useClinicalDocuments.ts"),
      "utf8",
    );
    expect(page).toContain("useClinicalDocuments");
    expect(page).toContain("useUploadDocumentFile");
    expect(page).toContain("type=\"file\"");
    expect(hooks).toContain("/internal/v1/clinical-documents");
    expect(hooks).toContain("postForm");
  });

  it("BFF clinical documents controller chains document-store upload to PCT index", () => {
    const controller = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ClinicalDocumentsController.java",
      ),
      "utf8",
    );
    expect(controller).toContain("/internal/v1/clinical-documents");
    expect(controller).toContain("/upload");
    expect(controller).toContain("document_object_id");
  });
});
