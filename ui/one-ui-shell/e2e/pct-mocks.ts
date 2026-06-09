import type { Page } from "@playwright/test";

export const PCT_E2E = {
  facilityId: "f1000000-0000-0000-0000-000000000001",
  queueId: "11111111-1111-1111-1111-111111111201",
  patientCpid: "CPID-ZW-00001",
  queueItemId: "22222222-2222-2222-2222-222222222201",
  journeyId: "01HARAREQUEUE00001CPIDZW",
} as const;

export async function installPctMocks(page: Page) {
  await page.route("**/internal/v1/queue/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/.*\/internal\/v1\/queue/, "");
    const method = route.request().method();

    if (method === "GET" && path.startsWith("/entries")) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              itemId: PCT_E2E.queueItemId,
              queueId: PCT_E2E.queueId,
              journeyId: PCT_E2E.journeyId,
              patientCpid: PCT_E2E.patientCpid,
              status: "WAITING",
              priority: 3,
              tokenNumber: 101,
            },
          ],
        }),
      });
    }

    if (method === "POST" && path.includes("/call")) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            itemId: PCT_E2E.queueItemId,
            status: "CALLED",
            patientCpid: PCT_E2E.patientCpid,
          },
        }),
      });
    }

    return route.continue();
  });
}
