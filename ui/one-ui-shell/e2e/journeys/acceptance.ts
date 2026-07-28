/**
 * The 10-point journey acceptance standard, as named test.step helpers.
 *
 *  A1  correct user can access it            A6  another user can see the result
 *  A2  button works (visible + enabled)      A7  notification is generated
 *  A3  form opens                            A8  audit trail is created
 *  A4  data saves (real 2xx observed)        A9  journey resumes after logout
 *  A5  state changes                         A10 journey reaches a logical end
 *
 * Journeys must invoke each point or explicitly record N/A with a reason —
 * silent omission is exactly the "false green" this harness exists to kill.
 * The collected checklist is attached to the Playwright report per journey.
 */

import type { Locator, Page, TestInfo } from "@playwright/test";
import { expect, test } from "@playwright/test";

export type AcceptancePoint =
  // ── The canonical checklist. ALL_POINTS below requires every one of these, and finalize()
  //    fails a journey that leaves any of them unaddressed.
  | "A1_access"
  | "A2_actionEnabled"
  | "A3_formOpens"
  | "A4_saves"
  | "A5_stateChange"
  | "A6_crossUserVisibility"
  | "A7_notification"
  | "A8_audit"
  | "A9_resume"
  | "A10_logicalEnd"
  // ── Supplementary points used by the theatre journeys.
  //
  // These are DELIBERATELY absent from ALL_POINTS. They are a richer framing the theatre specs
  // were written against — orientation, key fields, persisted truth, continuity, guidance,
  // feedback — and they are recorded here so those specs type-check honestly. Adding them to
  // ALL_POINTS would force every other journey to address them; mapping them onto the canonical
  // names would change what the theatre specs assert, which is not mine to decide.
  //
  // The consequence is intentional and should not be papered over: because these are not in
  // ALL_POINTS, a theatre journey recording only these still counts as NOT having addressed the
  // canonical points, and finalize() will fail it. That is the true state of affairs. It has been
  // invisible until now because the journey specs are excluded from the chromium project
  // (testIgnore: "journeys/**") and were excluded from every type-check — never compiled, never
  // run. Owner: the theatre wave.
  | "A2_orientation"
  | "A2b_reschedulingGuidance"
  | "A4_keyFields"
  | "A5_action"
  | "A6_persistedTruth"
  | "A7_continuity"
  | "A8_guidance"
  | "A9_feedback";

const ALL_POINTS: AcceptancePoint[] = [
  "A1_access",
  "A2_actionEnabled",
  "A3_formOpens",
  "A4_saves",
  "A5_stateChange",
  "A6_crossUserVisibility",
  "A7_notification",
  "A8_audit",
  "A9_resume",
  "A10_logicalEnd",
];

export class AcceptanceChecklist {
  private readonly results = new Map<AcceptancePoint, string>();

  constructor(private readonly journeyName: string) {}

  /** Run a checklist point as a named test.step. */
  async point(point: AcceptancePoint, description: string, body: () => Promise<void>) {
    await test.step(`${point} — ${description}`, async () => {
      await body();
      this.results.set(point, `PASS: ${description}`);
    });
  }

  /** Record a point as not applicable, with an honest reason. */
  async notApplicable(point: AcceptancePoint, reason: string) {
    await test.step(`${point} — N/A: ${reason}`, async () => {
      this.results.set(point, `N/A: ${reason}`);
    });
  }

  /** Attach the checklist to the report and fail if any point was skipped silently. */
  async finalize(testInfo: TestInfo) {
    const missing = ALL_POINTS.filter((p) => !this.results.has(p));
    const lines = ALL_POINTS.map((p) => `${p}: ${this.results.get(p) ?? "MISSING"}`);
    await testInfo.attach(`acceptance-${this.journeyName}`, {
      body: lines.join("\n"),
      contentType: "text/plain",
    });
    expect(missing, `acceptance points not addressed: ${missing.join(", ")}`).toEqual([]);
  }
}

/** Assert a control is present AND actionable — the anti-decorative-button check. */
export async function expectActionable(locator: Locator) {
  await expect(locator).toBeVisible({ timeout: 15_000 });
  await expect(locator).toBeEnabled();
}

/** Observe a real 2xx mutation while performing an action — proof data saved. */
export async function expectSaved(
  page: Page,
  pathFragment: string,
  action: () => Promise<void>,
  method: "POST" | "PUT" | "PATCH" = "POST",
  timeoutMs = 30_000,
) {
  const [response] = await Promise.all([
    page.waitForResponse(
      (r) =>
        r.request().method() === method &&
        r.url().includes(pathFragment) &&
        r.status() >= 200 &&
        r.status() < 300,
      { timeout: timeoutMs },
    ),
    action(),
  ]);
  return response;
}

/** Read the unread inbox count via the same-origin BFF as the logged-in user. */
export async function unreadCount(page: Page): Promise<number> {
  const result = await page.evaluate(async () => {
    const res = await fetch("/internal/v1/notifications/inbox/unread-count", {
      headers: { "X-Requested-With": "journey-acceptance" },
    });
    if (!res.ok) return -1;
    const body = await res.json().catch(() => null);
    const attrs = body?.data?.attributes ?? body?.data ?? body;
    const value = attrs?.unreadCount ?? attrs?.count ?? attrs?.unread;
    return typeof value === "number" ? value : -1;
  });
  return result;
}
