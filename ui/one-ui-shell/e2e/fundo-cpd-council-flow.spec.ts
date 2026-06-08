import { test, expect } from "./fixtures";

const PROVIDER_NUMERIC_ID = "42";
const CANDIDATE_ID = "9001";

test.describe("Fundo CPD evidence → council acceptance", () => {
  test.beforeEach(async ({ page }) => {
    await page.route("**/internal/v1/learning/v11/cpd/evidence**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            items: [
              {
                courseTitle: "Introduction to Impilo EHR",
                certificateId: "cert-cpd-e2e-001",
                completedAt: "2026-06-08T10:00:00Z",
                verifiedState: "PENDING_REVIEW",
              },
            ],
          },
        }),
      });
    });

    await page.route(`**/internal/v1/registry/provider-council/fundo-cpd-candidates?providerId=${PROVIDER_NUMERIC_ID}**`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: CANDIDATE_ID,
              courseTitle: "Introduction to Impilo EHR",
              status: "PENDING",
              cpdPoints: 2,
            },
          ],
        }),
      });
    });

    await page.route(`**/internal/v1/registry/provider-council/fundo-cpd-candidates/${CANDIDATE_ID}/accept**`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: { id: CANDIDATE_ID, status: "ACCEPTED", ledgerEventId: "cpd-evt-001" },
        }),
      });
    });
  });

  test("CPD evidence page links to council self-service", async ({ page }) => {
    await page.goto("/learning/cpd");
    await expect(page.getByTestId("fundo-cpd-evidence-list")).toContainText("Introduction to Impilo EHR");
    await expect(page.getByRole("link", { name: /council self-service/i })).toBeVisible();
  });

  test("council self-service shows Fundo candidates from Varapi bridge", async ({ page }) => {
    await page.goto(`/registry/provider-council/self-service?providerId=${PROVIDER_NUMERIC_ID}`);
    await expect(page.locator("body")).toContainText(/Fundo CPD candidates/i);
    await expect(page.locator("body")).toContainText(/Introduction to Impilo EHR|PENDING/i);
  });
});
