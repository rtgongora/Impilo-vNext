import { test, expect } from "@playwright/test";
import { loginPreviewPersona, PREVIEW_PERSONA, RUN_PREVIEW } from "./preview-sandbox-helpers";

test.describe("login probe", () => {
  test.beforeAll(() => {
    test.skip(!RUN_PREVIEW, "preview only");
    test.skip(process.env.PREVIEW_LOGIN_PROBE !== "1", "Set PREVIEW_LOGIN_PROBE=1 to run");
  });

  test("probe login", async ({ page }) => {
    test.setTimeout(120_000);
    // eslint-disable-next-line no-console
    console.log("persona", PREVIEW_PERSONA.username);
    try {
      await loginPreviewPersona(page);
      // eslint-disable-next-line no-console
      console.log("LANDING", page.url());
      await expect(page.locator("body")).toBeVisible();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.log("FAIL_URL", page.url());
      // eslint-disable-next-line no-console
      console.log("BODY_SNIP", (await page.locator("body").innerText()).slice(0, 400));
      throw error;
    }
  });
});
