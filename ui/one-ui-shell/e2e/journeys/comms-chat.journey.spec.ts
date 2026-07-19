import { test, expect } from "@playwright/test";
import { PERSONAS } from "./personas";
import { RUN_PREVIEW, loginAs, gotoAs } from "./honest-auth";

/**
 * Golden journey — Khuluma text chat from the browser.
 *
 * trainer.chikafu opens the Comms Hub, starts a NEW conversation with
 * learner.tembo (by Health ID, via the newly-added composer), and sends a
 * message — all through the real ingress, real logins, real POSTs. This is the
 * "I've never sent a single chat" path: before this wave the hub had no way to
 * start a conversation and the inbox 500'd.
 */
const LEARNER_HID = "c0000000-0000-4000-8000-000000000012";

test.describe("Khuluma chat — start + send (live preview)", () => {
  test.beforeEach(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test("open hub → New conversation → send message", async ({ page }) => {
    await loginAs(page, PERSONAS.trainer);
    await gotoAs(page, PERSONAS.trainer, "/my/comms");

    // The hub renders (inbox no longer 500s) and the New entry point exists.
    await expect(page.getByTestId("comms-hub")).toBeVisible({ timeout: 20_000 });
    const newButton = page.getByTestId("comms-new-conversation");
    await expect(newButton).toBeVisible({ timeout: 20_000 });

    await newButton.click();
    await expect(page.getByTestId("comms-new-dialog")).toBeVisible({ timeout: 10_000 });

    await page.getByTestId("comms-recipient-id").fill(LEARNER_HID);

    const [createResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes("/internal/v1/khuluma/conversations") && r.request().method() === "POST" && r.status() >= 200 && r.status() < 300,
        { timeout: 20_000 },
      ),
      page.getByTestId("comms-create-conversation").click(),
    ]);
    expect(createResp.status(), "conversation created via real POST").toBeGreaterThanOrEqual(201);

    // Composer closes and a send box is reachable (was impossible before).
    await expect(page.getByTestId("comms-new-dialog")).toBeHidden({ timeout: 10_000 });
    // Scope to the textarea — the shell taskbar also exposes aria-label "…Messages" buttons.
    const msgBox = page.locator('textarea[aria-label="Message"]');
    await expect(msgBox).toBeVisible({ timeout: 10_000 });

    const text = `Live chat proof ${Date.now()}`;
    await msgBox.fill(text);
    const [sendResp] = await Promise.all([
      page.waitForResponse(
        (r) => /\/khuluma\/conversations\/.+\/messages$/.test(r.url()) && r.request().method() === "POST" && r.status() >= 200 && r.status() < 300,
        { timeout: 20_000 },
      ),
      page.getByRole("button", { name: /Send/ }).click(),
    ]);
    expect(sendResp.status(), "message sent via real POST").toBeGreaterThanOrEqual(201);

    await expect(page.getByText(text).first()).toBeVisible({ timeout: 10_000 });
  });
});
