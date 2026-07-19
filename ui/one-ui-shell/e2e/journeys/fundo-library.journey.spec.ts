import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, logout, gotoAs } from "./honest-auth";
import { AcceptanceChecklist, expectActionable, expectSaved } from "./acceptance";

/**
 * Golden journey — Fundo library resource authoring → cross-user consumption.
 *
 * trainer.chikafu registers a governed learning resource (title + type +
 * reference URL) through the Library Uploads surface; then learner.tembo — a
 * different real user — browses the Fundo Library and opens the resource detail.
 *
 * Honest scope: the upload surface registers metadata + an HTTPS reference URL
 * (the page says so). Binary streaming into document-service MinIO is the
 * documented QUEUED partial and is NOT claimed here. What IS proven: the create
 * persists via a real 2xx POST and a second user reads it back live.
 */

const RESOURCE_MARKER = `Journey Library Resource ${Date.now()}`;

test.describe.serial("Fundo library author → consumer journey (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("author: register a governed library resource", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-library-author");

    await checklist.point("A1_access", "author reaches Library Uploads", async () => {
      await loginAs(page, PERSONAS.trainer);
      await gotoAs(page, PERSONAS.trainer, "/learning/library/uploads");
      await expect(page.getByTestId("fundo-library-upload-form")).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A3_formOpens", "upload form carries title/type/reference fields", async () => {
      await expect(page.getByTestId("fundo-upload-title")).toBeVisible();
      await expect(page.getByTestId("fundo-upload-type")).toBeVisible();
      await expect(page.getByTestId("fundo-upload-content-ref")).toBeVisible();
    });

    await checklist.point("A2_actionEnabled", "Save enables once titled", async () => {
      const save = page.getByTestId("fundo-upload-save");
      await expect(save).toBeDisabled();
      await page.getByTestId("fundo-upload-title").fill(RESOURCE_MARKER);
      await page.getByTestId("fundo-upload-type").selectOption("SOP");
      await page.getByTestId("fundo-upload-content-ref").fill("https://docs.impilo.example/ipc-refresher-2026.pdf");
      await expectActionable(save);
    });

    await checklist.point("A4_saves", "resource persists via real POST", async () => {
      await expectSaved(page, "/internal/v1/learning/v11/library/resources", async () => {
        await page.getByTestId("fundo-upload-save").click();
      });
      await expect(page.getByTestId("fundo-upload-success")).toBeVisible({ timeout: 15_000 });
    });

    await checklist.point("A5_stateChange", "the resource is listed in the library", async () => {
      await gotoAs(page, PERSONAS.trainer, "/learning/library");
      await expect(page.getByRole("link", { name: RESOURCE_MARKER }).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A10_logicalEnd", "author receives a persisted, listed resource", async () => {
      await expect(page.getByRole("link", { name: RESOURCE_MARKER }).first()).toBeVisible();
    });

    await checklist.notApplicable("A6_crossUserVisibility", "proven by the learner-consumer half of this serial journey");
    await checklist.notApplicable("A7_notification", "library registration does not notify learners; assignment/notification flows own that");
    await checklist.notApplicable("A8_audit", "library writes are recorded in lrn_library_resource with uploaded_by; no learner-facing audit surface");
    await checklist.notApplicable("A9_resume", "resource persistence across sessions is exercised by the consumer half");
    await checklist.finalize(testInfo);
  });

  test("learner: browse library and open the resource", async ({ page }, testInfo) => {
    const checklist = new AcceptanceChecklist("fundo-library-consumer");

    await checklist.point("A1_access", "learner reaches the Fundo Library", async () => {
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, "/learning/library");
      await expect(page.getByText(/fundo library/i).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A6_crossUserVisibility", "another user sees the trainer's resource", async () => {
      await expect(page.getByRole("link", { name: RESOURCE_MARKER }).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A2_actionEnabled", "the resource link is actionable", async () => {
      await expectActionable(page.getByRole("link", { name: RESOURCE_MARKER }).first());
    });

    await checklist.point("A3_formOpens", "the resource detail opens with its title", async () => {
      await page.getByRole("link", { name: RESOURCE_MARKER }).first().click();
      await page.waitForURL(/\/learning\/library\/.+/, { timeout: 20_000 });
      await expect(page.getByText(RESOURCE_MARKER).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A9_resume", "logout → login → resource still consumable", async () => {
      await logout(page);
      await loginAs(page, PERSONAS.learner);
      await gotoAs(page, PERSONAS.learner, "/learning/library");
      await expect(page.getByRole("link", { name: RESOURCE_MARKER }).first()).toBeVisible({ timeout: 20_000 });
    });

    await checklist.point("A10_logicalEnd", "journey ends on a cross-user consumable resource", async () => {
      await expect(page.getByRole("link", { name: RESOURCE_MARKER }).first()).toBeVisible();
    });

    await checklist.notApplicable("A4_saves", "consumption is read-only; the write is proven by the author half");
    await checklist.notApplicable("A5_stateChange", "reading a resource does not transition state");
    await checklist.notApplicable("A7_notification", "browsing the library does not notify");
    await checklist.notApplicable("A8_audit", "read access is not surfaced as a learner-facing audit trail");
    await checklist.finalize(testInfo);
  });
});
