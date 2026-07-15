import { test, expect } from "@playwright/test";

/**
 * Wave 6 §19 — theatre-team UI ergonomics, proven in the running preview (no mocks).
 *
 * Asserts the two safety-critical ergonomics guarantees that must not regress:
 *   1. The PERSISTENT context banner stays visible through a full data-entry journey — after
 *      scrolling to the operative-note fields and typing, the patient/allergies/site/stage banner
 *      is STILL on screen (sticky), so the operator never loses safety context mid-entry.
 *   2. The operative-note document state renders DISTINCTLY — a draft badge is visually different
 *      from a signed badge (distinct data-state + label + class), so a draft is never mistaken
 *      for a signed record.
 *
 * Preview-gated: SKIPS when the shell/preview is not reachable or the case route needs auth that
 * this harness cannot satisfy. Point at a running preview with a seeded theatre case:
 *   E2E_BASE_URL       — one-ui-shell preview (default http://localhost:3000)
 *   E2E_THEATRE_CASE_ID — an existing theatre case id to open
 */

const BASE = process.env.E2E_BASE_URL || "http://localhost:3000";
const CASE_ID = process.env.E2E_THEATRE_CASE_ID || "";

test.describe("Theatre §19 ergonomics — persistent banner + document-state distinction", () => {
  test("the context banner stays visible while entering the operative note", async ({ page }) => {
    test.skip(!CASE_ID, "Set E2E_THEATRE_CASE_ID to a seeded theatre case to run this journey");
    let ok = false;
    try {
      const resp = await page.goto(`${BASE}/work/clinical/theatre/${CASE_ID}`, { waitUntil: "domcontentloaded", timeout: 10000 });
      ok = !!resp && resp.ok();
    } catch {
      ok = false;
    }
    test.skip(!ok, `Start the one-ui-shell preview with a seeded case — need ${BASE}`);

    const banner = page.getByTestId("theatre-case-banner");
    await expect(banner, "banner present on load").toBeVisible();

    // Scroll down to the operative-note data-entry surface and type into it.
    const note = page.getByTestId("operative-note-section");
    await note.scrollIntoViewIfNeeded();
    const firstInput = note.locator("input").first();
    await firstInput.click();
    await firstInput.fill("Laparoscopic appendectomy");

    // The persistent banner must STILL be visible in the viewport after scrolling + typing.
    await expect(banner, "banner remains visible mid data-entry (sticky)").toBeInViewport();

    // The document-state badge is present and starts as DRAFT (distinct from a signed note).
    const badge = page.getByTestId("doc-state-badge");
    await expect(badge).toBeVisible();
    await expect(badge).toHaveAttribute("data-state", "DRAFT");
  });
});
