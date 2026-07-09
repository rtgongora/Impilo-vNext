import { test, expect } from "@playwright/test";
import {
  PREVIEW_FACILITY_ID,
  RUN_PREVIEW,
  installPreviewSession,
  uniqueMarker,
  gotoAppPath,
  bffPostFromBrowser,
  bffGetFromBrowser,
  waitForBffGet,
  isPreviewLoginScreen,
} from "./preview-sandbox-helpers";

function extractReqId(json: unknown): string | null {
  if (!json || typeof json !== "object") return null;
  const data = (json as { data?: Record<string, unknown> }).data;
  if (!data) return null;
  const id = data.reqId ?? data.req_id ?? data.id;
  return typeof id === "string" ? id : null;
}

/**
 * Preview sandbox runtime persistence — POST via BFF, re-navigate, assert persisted state.
 * Write-path failures are hard failures (no test.skip on POST).
 */
test.describe("Preview sandbox persistence proofs", () => {
  test.beforeAll(() => {
    test.skip(!RUN_PREVIEW, "Set PREVIEW_SANDBOX_E2E=1 or PLAYWRIGHT_BASE_URL to preview ingress");
  });

  test.beforeEach(async ({ context, baseURL }) => {
    await installPreviewSession(context, baseURL ?? process.env.PLAYWRIGHT_BASE_URL ?? "https://impilo.mohcc.gov.zw");
  });

  async function openStableShell(page: import("@playwright/test").Page) {
    await installPreviewSession(page.context(), process.env.PLAYWRIGHT_BASE_URL ?? "https://impilo.mohcc.gov.zw");
    await gotoAppPath(page, "/enterprise");
    test.skip(await isPreviewLoginScreen(page), "Preview redirected to login");
    await expect(page.getByTestId("module-workspace-hero")).toBeVisible({ timeout: 30_000 });
  }

  test("governance: policy POST persists across re-navigation", async ({ page }) => {
    const policyName = uniqueMarker("e2e-governance-policy");
    await installPreviewSession(page.context(), process.env.PLAYWRIGHT_BASE_URL ?? "https://impilo.mohcc.gov.zw");
    await gotoAppPath(page, "/");

    const post = await bffPostFromBrowser(page, "/internal/v1/governance/access/policies", {
      name: policyName,
      description: "Preview E2E persistence policy",
      resourceType: "CLINICAL_RECORD",
      effect: "ALLOW",
      conditions: "purpose=TREATMENT",
    });
    expect(post.ok, `Governance POST failed (${post.status}): ${JSON.stringify(post.json)}`).toBeTruthy();

    const list = await bffGetFromBrowser(page, "/internal/v1/governance/access/policies");
    expect(list.ok).toBeTruthy();
    expect(list.text).toContain(policyName);

    await gotoAppPath(page, "/");
    const listAfter = await bffGetFromBrowser(page, "/internal/v1/governance/access/policies");
    expect(listAfter.ok).toBeTruthy();
    expect(listAfter.text).toContain(policyName);
  });

  test("inventory: requisition POST persists across re-navigation", async ({ page }) => {
    const notes = uniqueMarker("e2e-requisition-notes");
    await installPreviewSession(page.context(), process.env.PLAYWRIGHT_BASE_URL ?? "https://impilo.mohcc.gov.zw");
    await gotoAppPath(page, "/");

    const post = await bffPostFromBrowser(page, "/internal/v1/inventory/requisitions", {
      facility_id: PREVIEW_FACILITY_ID,
      requested_by: "preview-persist-e2e",
      item_count: 2,
      notes,
    });
    expect(post.ok, `Inventory POST failed (${post.status}): ${JSON.stringify(post.json)}`).toBeTruthy();

    const reqId = extractReqId(post.json);
    expect(reqId, "POST response must include reqId").toBeTruthy();

    const list = await bffGetFromBrowser(
      page,
      `/internal/v1/inventory/requisitions?facility_id=${PREVIEW_FACILITY_ID}`,
    );
    expect(list.ok).toBeTruthy();
    expect(list.text).toContain(reqId!);

    await gotoAppPath(page, "/");
    const listAfter = await bffGetFromBrowser(
      page,
      `/internal/v1/inventory/requisitions?facility_id=${PREVIEW_FACILITY_ID}`,
    );
    expect(listAfter.ok).toBeTruthy();
    expect(listAfter.text).toContain(reqId!);
  });

  test("enterprise: dashboard content survives re-navigation", async ({ page }) => {
    await openStableShell(page);
    const snippetBefore = (await page.locator("body").innerText()).slice(0, 400);

    await gotoAppPath(page, "/enterprise");
    await expect(page.getByTestId("module-workspace-hero")).toBeVisible({ timeout: 30_000 });
    const snippetAfter = (await page.locator("body").innerText()).slice(0, 400);
    expect(snippetAfter.length).toBeGreaterThan(40);
    expect(snippetAfter).not.toMatch(/502 Bad Gateway|503 Service Unavailable/i);
    if (/Enterprise resources|Geography scope/i.test(snippetBefore)) {
      expect(snippetAfter).toMatch(/Enterprise resources|Geography scope/i);
    }
  });

  test("learning: library GET payload stable across re-navigation", async ({ page }) => {
    await openStableShell(page);

    const getBefore = await bffGetFromBrowser(
      page,
      "/internal/v1/learning/v11/library/resources?page=0&size=20",
    );
    expect(getBefore.ok, `Learning GET failed (${getBefore.status})`).toBeTruthy();

    await gotoAppPath(page, "/enterprise");
    const getAfter = await bffGetFromBrowser(
      page,
      "/internal/v1/learning/v11/library/resources?page=0&size=20",
    );
    expect(getAfter.ok).toBeTruthy();
    expect(getAfter.text.length).toBeGreaterThan(10);
    if (getBefore.text.length > 20) {
      expect(getAfter.text).toBe(getBefore.text);
    }
  });

  test("registry: identity BFF list survives re-navigation", async ({ page }) => {
    await gotoAppPath(page, "/registry");
    test.skip(await isPreviewLoginScreen(page), "Registry requires live auth on preview");
    await waitForBffGet(page, "/internal/v1/");

    await gotoAppPath(page, "/registry");
    await waitForBffGet(page, "/internal/v1/");
    await expect(page.locator("body")).toBeVisible();
    await expect(page.locator("body")).not.toContainText(/502 Bad Gateway|503 Service Unavailable/i);
  });
});
