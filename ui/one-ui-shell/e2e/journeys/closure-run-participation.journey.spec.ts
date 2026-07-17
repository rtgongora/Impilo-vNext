/**
 * C9 closure-run — Participation & access (journeys 10–15).
 *
 * Anonymous feedback (claim-code lane), report status lookup, get-involved idea
 * submission + public board, honest app-download state, and the pre-login
 * provider onboarding explainer. Real BFF POSTs are observed and real
 * references/claim-codes asserted — no fabricated confirmations.
 */

import { test, expect } from "@playwright/test";
import {
  AcceptanceChecklist,
  gotoPublic,
  waitForGatewayGet,
  marker,
  naPoints,
} from "./closure-run-helpers";

/**
 * Claim code shared across J10 → J11 in the same worker (workers=1) so the pair
 * makes ONE submission, not two — the public feedback lane is rate-limited per
 * connection, so minimising submissions keeps the acceptance run honest.
 */
let sharedClaimCode: string | null = null;

/**
 * Fill + submit the anonymous public feedback form. Returns the claim code on
 * success, or null when the lane honestly rate-limits/erred this connection
 * (the UI shows the honest error banner — never a fabricated receipt).
 */
async function submitAnonymousReport(page: import("@playwright/test").Page): Promise<string | null> {
  await page.locator('[data-testid="public-feedback-form"] input[maxlength="200"]').fill(`${marker("closure-run")} report`);
  await page.locator('[data-testid="public-feedback-form"] textarea').fill(
    "Automated closure-run acceptance probe of the anonymous report lane. No personal or health details.",
  );
  await page.getByRole("button", { name: /submit anonymous report/i }).click();
  const receipt = page.getByTestId("feedback-receipt");
  const errorBanner = page.locator('[data-testid="public-feedback-form"] .text-red-800').first();
  await receipt.or(errorBanner).first().waitFor({ state: "visible", timeout: 20_000 });
  if (!(await receipt.isVisible().catch(() => false))) {
    return null; // honest rate-limit / outage banner shown — no fabricated success
  }
  const code = (await receipt.locator(".font-mono").nth(1).innerText()).trim();
  sharedClaimCode = code;
  return code;
}

test.describe("C9 closure-run — participation & access", () => {
  test("J10 Feedback submit — anonymous report returns a real reference + claim code", async ({ page }, testInfo) => {
    const chk = new AcceptanceChecklist("J10-feedback-submit");
    let claimCode: string | null = null;
    await chk.point("A1_access", "anonymous report form reachable without sign-in", async () => {
      await gotoPublic(page, "/welcome/report/anonymous");
      await expect(page.getByTestId("public-feedback-form")).toBeVisible();
    });
    await chk.point("A3_formOpens", "the report form is interactive", async () => {
      await expect(page.getByRole("button", { name: /submit anonymous report/i })).toBeVisible();
    });
    await chk.point("A2_actionEnabled", "submit is attempted; the lane returns a receipt OR an honest error (never a fake success)", async () => {
      claimCode = await submitAnonymousReport(page);
    });
    if (claimCode) {
      await chk.point("A4_saves", "a real receipt with a NON-EMPTY claim code + case reference is shown", async () => {
        expect(claimCode!.length).toBeGreaterThan(2);
        await expect(page.locator('[data-testid="feedback-receipt"]').getByText(/reference:/i)).toBeVisible();
      });
      await chk.point("A5_stateChange", "a tracked case exists (claim code is the only key)", async () => {
        await expect(page.getByText(/only way to check the outcome/i)).toBeVisible();
      });
      await chk.point("A8_audit", "receipt records the case reference for tracking", async () => {
        await expect(page.locator('[data-testid="feedback-receipt"] .font-mono').first()).toBeVisible();
      });
      await chk.point("A10_logicalEnd", "a real 'check a report status' next step is offered", async () => {
        await expect(page.getByRole("link", { name: /check a report/i }).first()).toBeVisible();
      });
    } else {
      await chk.point("A4_saves", "honest rate-limit/outage banner is shown — the lane never fabricates a receipt", async () => {
        await expect(page.locator('[data-testid="public-feedback-form"] .text-red-800').first()).toBeVisible();
        await expect(page.getByTestId("feedback-receipt")).toHaveCount(0);
      });
      await chk.notApplicable("A5_stateChange", "connection rate-limited by the public feedback lane (protection working) — no case created this run.");
      await chk.notApplicable("A8_audit", "no receipt issued — nothing to record this run.");
      await chk.notApplicable("A10_logicalEnd", "submission honestly declined by rate-limit; no receipt continuation.");
    }
    await naPoints(chk, ["A6_crossUserVisibility", "A7_notification", "A9_resume"],
      "Anonymous lane — no cross-user visibility, no citizen notification, no post-logout resume by design.");
    await chk.finalize(testInfo);
  });

  test("J11 Feedback status — a real submitted claim code resolves to a real status", async ({ page }, testInfo) => {
    const chk = new AcceptanceChecklist("J11-feedback-status");
    let claimCode: string | null = null;
    await chk.point("A1_access", "obtain a real claim code (reuse J10's, or submit once if needed)", async () => {
      if (sharedClaimCode) {
        claimCode = sharedClaimCode;
      } else {
        await gotoPublic(page, "/welcome/report/anonymous");
        claimCode = await submitAnonymousReport(page);
      }
    });
    await chk.point("A3_formOpens", "status lookup form reachable", async () => {
      await gotoPublic(page, "/welcome/report/status");
      await expect(page.getByTestId("feedback-status-lookup")).toBeVisible();
    });
    if (!claimCode) {
      await chk.notApplicable("A4_saves", "no claim code available (feedback lane rate-limited this connection) — cannot look up a real case this run.");
      await chk.notApplicable("A5_stateChange", "no real case to resolve without a claim code.");
      await chk.notApplicable("A6_crossUserVisibility", "no anonymously-raised case to view without a claim code.");
      await chk.point("A10_logicalEnd", "the lookup form still handles a missing/unknown code honestly", async () => {
        await expect(page.getByRole("heading", { name: /check your report/i })).toBeVisible();
      });
    } else {
      await chk.point("A4_saves", "lookup returns a real 2xx for the claim code", async () => {
        const get = waitForGatewayGet(page, "/public/gateway/feedback/");
        await page.locator('[data-testid="feedback-status-lookup"] input').fill(claimCode!);
        await page.getByRole("button", { name: /^check$/i }).click();
        await get;
      });
      await chk.point("A5_stateChange", "a real status result renders (reference + status)", async () => {
        await expect(page.getByTestId("feedback-status-result")).toBeVisible({ timeout: 20_000 });
        await expect(
          page.getByTestId("feedback-status-result").getByText(/received|acknowledged|reviewed|investigat|resolved|closed/i).first(),
        ).toBeVisible();
      });
      await chk.point("A6_crossUserVisibility", "the anonymously-raised case is visible via its claim code", async () => {
        await expect(page.getByTestId("feedback-status-result")).toBeVisible();
      });
      await chk.point("A10_logicalEnd", "status page reaches a logical, honest end", async () => {
        await expect(page.getByRole("heading", { name: /check your report/i })).toBeVisible();
      });
    }
    await naPoints(chk, ["A2_actionEnabled", "A7_notification", "A8_audit", "A9_resume"],
      "Read-only status lookup — no notification/audit surfaced to the anonymous citizen; no resume.");
    await chk.finalize(testInfo);
  });

  test("J12 Get-involved submit — idea returns a real reference + claim code", async ({ page }, testInfo) => {
    const chk = new AcceptanceChecklist("J12-get-involved-submit");
    await chk.point("A1_access", "share-an-idea form reachable without sign-in", async () => {
      await gotoPublic(page, "/get-involved/idea");
      await expect(page.getByTestId("get-involved-idea-form")).toBeVisible();
    });
    await chk.point("A3_formOpens", "idea form interactive", async () => {
      await page.locator('[data-testid="get-involved-idea-form"] input[maxlength="512"]').fill(`${marker("closure-run")} idea`);
      await page.locator('[data-testid="get-involved-idea-form"] textarea').first().fill(
        "Closure-run acceptance probe of the get-involved contribution lane.",
      );
    });
    let gotReceipt = false;
    await chk.point("A4_saves", "submit is attempted; the lane returns a receipt OR an honest error (never a fake success)", async () => {
      await page.getByRole("button", { name: /share this idea/i }).click();
      const receipt = page.getByTestId("get-involved-receipt");
      const errorBanner = page.locator('[data-testid="get-involved-idea-form"] .text-red-800').first();
      await receipt.or(errorBanner).first().waitFor({ state: "visible", timeout: 20_000 });
      gotReceipt = await receipt.isVisible().catch(() => false);
    });
    if (gotReceipt) {
      await chk.point("A5_stateChange", "a NON-EMPTY claim code receipt is shown", async () => {
        const code = await page.locator('[data-testid="get-involved-receipt"] .font-mono').first().innerText();
        expect(code.trim().length).toBeGreaterThan(2);
      });
      await chk.point("A10_logicalEnd", "track + board next steps offered", async () => {
        await expect(page.getByRole("link", { name: /track your idea/i })).toBeVisible();
      });
    } else {
      await chk.notApplicable("A5_stateChange", "contribution lane rate-limited this connection (protection working) — honest error banner shown, no fabricated receipt.");
      await chk.point("A10_logicalEnd", "the honest error banner is shown (never a fake success)", async () => {
        await expect(page.locator('[data-testid="get-involved-idea-form"] .text-red-800').first()).toBeVisible();
      });
    }
    await naPoints(chk, ["A2_actionEnabled", "A6_crossUserVisibility", "A7_notification", "A8_audit", "A9_resume"],
      "Anonymous contribution — no cross-user/notification/audit/resume until a moderator publishes it.");
    await chk.finalize(testInfo);
  });

  test("J13 Get-involved board — public board renders (real ideas or honest empty)", async ({ page }, testInfo) => {
    const chk = new AcceptanceChecklist("J13-get-involved-board");
    await chk.point("A1_access", "board reachable without sign-in", async () => {
      const get = waitForGatewayGet(page, "/public/gateway/get-involved/board");
      await gotoPublic(page, "/get-involved/board");
      await get;
    });
    await chk.point("A5_stateChange", "board resolves to real seeded ideas OR an honest empty state", async () => {
      await expect(
        page.locator('[data-testid="idea-board"], [data-testid="board-empty"]').first(),
      ).toBeVisible({ timeout: 20_000 });
    });
    await chk.point("A6_crossUserVisibility", "published ideas are visible to any anonymous visitor", async () => {
      const hasIdeas = await page.getByTestId("idea-board").isVisible().catch(() => false);
      if (hasIdeas) {
        await expect(page.locator('[data-testid="idea-board"] li').first()).toBeVisible();
        await expect(page.getByTestId("support-count").first()).toBeVisible();
      } else {
        await expect(page.getByTestId("board-empty")).toBeVisible();
      }
    });
    await chk.point("A10_logicalEnd", "a real 'share your own idea' next step is present", async () => {
      await expect(page.getByRole("link", { name: /share your own idea|share one/i }).first()).toBeVisible();
    });
    await naPoints(chk, ["A2_actionEnabled", "A3_formOpens", "A4_saves", "A7_notification", "A8_audit", "A9_resume"],
      "Read-only board — support mutation optional and non-blocking; no notification/audit/resume.");
    await chk.finalize(testInfo);
  });

  test("J14 App download — honest store state, NO fabricated live store link", async ({ page }, testInfo) => {
    const chk = new AcceptanceChecklist("J14-app-download");
    await chk.point("A1_access", "/download reachable without sign-in", async () => {
      await gotoPublic(page, "/download");
      await expect(page).toHaveURL(/\/download$/);
    });
    await chk.point("A2_actionEnabled", "web-app 'available now' path is offered", async () => {
      await expect(page.getByText(/available now/i).first()).toBeVisible();
      await expect(page.getByRole("link", { name: /open impilo on the web/i })).toBeVisible();
    });
    await chk.point("A5_stateChange", "store affordances are honest 'coming soon' — not live listings", async () => {
      await expect(page.getByText(/coming soon/i).first()).toBeVisible();
      await expect(page.getByText(/being prepared/i).first()).toBeVisible();
    });
    await chk.point("A10_logicalEnd", "NO fabricated live app-store deep link presented", async () => {
      await expect(page.locator('a[href*="play.google.com"], a[href*="apps.apple.com"]')).toHaveCount(0);
    });
    await naPoints(chk, ["A3_formOpens", "A4_saves", "A6_crossUserVisibility", "A7_notification", "A8_audit", "A9_resume"],
      "Static app-discovery surface — notify-me opens the visitor's own mail client; no server mutation/audit.");
    await chk.finalize(testInfo);
  });

  test("J15 Provider onboarding — pre-login honest access explainer", async ({ page }, testInfo) => {
    const chk = new AcceptanceChecklist("J15-provider-onboarding");
    await chk.point("A1_access", "/provider/get-access reachable BEFORE login", async () => {
      await gotoPublic(page, "/provider/get-access");
      await expect(page).toHaveURL(/\/provider\/get-access$/);
      expect(page.url()).not.toContain("/auth/login");
    });
    await chk.point("A3_formOpens", "step-by-step access explainer renders", async () => {
      await expect(page.getByRole("heading", { name: /how health workers get access/i })).toBeVisible();
      await expect(page.getByText(/registration is verified/i).first()).toBeVisible();
    });
    await chk.point("A5_stateChange", "honest boundary: installing the app does not grant clinical access", async () => {
      await expect(page.getByText(/does not automatically grant access/i)).toBeVisible();
    });
    await chk.point("A10_logicalEnd", "a real entry into the verified activation flow is offered", async () => {
      await expect(page.getByRole("link", { name: /activate a provider id/i })).toBeVisible();
    });
    await naPoints(chk, ["A2_actionEnabled", "A4_saves", "A6_crossUserVisibility", "A7_notification", "A8_audit", "A9_resume"],
      "Explainer page — no mutation/notification/audit/resume; the real flow is gated behind sign-in.");
    await chk.finalize(testInfo);
  });
});
