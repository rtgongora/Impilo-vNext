import type { Page } from "@playwright/test";

export const COVERAGE_E2E = {
  planId: "bbbbbbbb-0001-0000-0000-000000000001",
  planCode: "COV-MOHCC-CORE",
  planName: "MOHCC National Core Scheme",
  memberId: "mem-e2e-coverage-001",
  memberNumber: "MOH-E2E-0001",
  clientId: "e2e-provider-001",
} as const;

const emptyList = { data: [] as unknown[] };

export async function installCoverageMocks(page: Page) {
  const enrolledMembers: Record<string, unknown>[] = [];

  const json = (body: unknown, status = 200) => ({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });

  const memberPlanRow = () => ({
    id: COVERAGE_E2E.memberId,
    clientId: COVERAGE_E2E.clientId,
    planId: COVERAGE_E2E.planId,
    planCode: COVERAGE_E2E.planCode,
    planName: COVERAGE_E2E.planName,
    memberNumber: COVERAGE_E2E.memberNumber,
    relationship: "SELF",
    status: "ACTIVE",
  });

  await page.route("**/internal/v1/coverage/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/.*\/internal\/v1\/coverage/, "");
    const method = route.request().method();

    if (method === "GET" && path === "/plans") {
      const memberCpid = url.searchParams.get("member_cpid");
      if (memberCpid) {
        const rows =
          enrolledMembers.length > 0
            ? enrolledMembers.map((m) => ({
                ...memberPlanRow(),
                memberNumber: (m.memberNumber as string) ?? COVERAGE_E2E.memberNumber,
              }))
            : [memberPlanRow()];
        return route.fulfill(json({ data: rows }));
      }
      return route.fulfill(
        json({
          data: [
            {
              id: COVERAGE_E2E.planId,
              planCode: COVERAGE_E2E.planCode,
              planName: COVERAGE_E2E.planName,
              payerId: "PAYER-MOHCC",
              planType: "GOVERNMENT",
              status: "ACTIVE",
            },
          ],
        }),
      );
    }

    if (method === "GET" && path === "/eligibility") {
      return route.fulfill(emptyList);
    }

    if (method === "GET" && path === "/claims") {
      return route.fulfill(emptyList);
    }

    if (method === "GET" && path === "/contributions") {
      return route.fulfill(emptyList);
    }

    if (method === "GET" && path === "/preauths") {
      return route.fulfill(emptyList);
    }

    if (method === "GET" && path === "/utilization") {
      return route.fulfill(emptyList);
    }

    if (method === "GET" && path === "/appeals") {
      // Non-empty so the test exercises the render path (the section previously
      // errored live). The BFF returns a plain array under `data` after unwrapping
      // the coverage-service Page envelope.
      return route.fulfill(
        json({
          data: [
            {
              id: "eeeeeeee-0001-0000-0000-000000000001",
              claimId: "cccccccc-0001-0000-0000-000000000001",
              appellantId: COVERAGE_E2E.clientId,
              reason: "Claim partially declined — requesting review",
              status: "PENDING",
              decision: null,
              createdAt: "2026-07-20T09:00:00Z",
            },
          ],
        }),
      );
    }

    if (method === "POST" && path === "/eligibility/enrollment") {
      return route.fulfill(
        json({
          data: {
            resultCode: "ELIGIBLE",
            resultMessage: "Member is eligible to enroll on the selected plan",
            planId: COVERAGE_E2E.planId,
            planCode: COVERAGE_E2E.planCode,
            clientId: COVERAGE_E2E.clientId,
            eligible: true,
          },
        }),
      );
    }

    if (method === "POST" && path === "/members") {
      const body = route.request().postDataJSON() as Record<string, string>;
      const member = {
        ...memberPlanRow(),
        memberNumber: body.memberNumber ?? COVERAGE_E2E.memberNumber,
      };
      enrolledMembers.length = 0;
      enrolledMembers.push(member);
      return route.fulfill(json({ data: member }, 201));
    }

    if (method === "GET" && path.startsWith("/member/")) {
      return route.fulfill(
        json({
          data: enrolledMembers.length > 0 ? enrolledMembers : [memberPlanRow()],
        }),
      );
    }

    if (method === "GET" && path === "/subsidies") {
      return route.fulfill(
        json({
          data: [
            {
              id: "dddddddd-0001-0000-0000-000000000001",
              programCode: "SUB-MOHCC-PRIMARY",
              programName: "MOHCC Primary Care Subsidy",
              subsidyType: "GOVERNMENT",
              annualCap: 1500,
              currency: "USD",
              status: "ACTIVE",
            },
          ],
        }),
      );
    }

    if (method === "GET") {
      return route.fulfill(json(emptyList));
    }

    return route.fulfill(json({ data: {} }));
  });
}
