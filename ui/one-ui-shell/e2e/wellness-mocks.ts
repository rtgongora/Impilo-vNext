import type { Page } from "@playwright/test";

export const WELLNESS_E2E = {
  tenantId: "00000000-0000-4000-8000-000000000001",
  cpid: "e2e-wellness-citizen-001",
  clubId: "aaaaaaaa-0003-0001-0000-000000000001",
  clubName: "Harare Morning Walkers",
  challengeId: "aaaaaaaa-0003-0002-0000-000000000001",
  challengeTitle: "10K Steps Daily — June",
} as const;

export async function installWellnessMocks(page: Page) {
  const goals: Record<string, unknown>[] = [];
  const activities: Record<string, unknown>[] = [];
  const diet: Record<string, unknown>[] = [];
  const joinedClubs = new Set<string>();
  const joinedChallenges = new Set<string>();

  const json = (body: unknown, status = 200) => ({
    status,
    contentType: "application/json",
    body: JSON.stringify(body),
  });

  await page.route("**/internal/v1/wellness/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/.*\/internal\/v1\/wellness/, "");
    const method = route.request().method();

    if (method === "GET" && path === "/goals") {
      return route.fulfill(json(goals));
    }
    if (method === "POST" && path === "/goals") {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      goals.push({
        id: goals.length + 1,
        goalType: body.goal_type ?? body.goalType ?? "STEPS",
        personCpid: body.person_cpid ?? WELLNESS_E2E.cpid,
        targetValue: body.target_value ?? 10000,
        currentValue: body.current_value ?? 0,
        unit: body.unit ?? "steps",
        status: "ACTIVE",
      });
      return route.fulfill(json(goals[goals.length - 1], 201));
    }

    if (method === "POST" && path === "/activities") {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      activities.push(body);
      return route.fulfill(json({ id: activities.length, ...body }, 201));
    }
    if (method === "GET" && path === "/activities") {
      return route.fulfill(json(activities));
    }

    if (method === "POST" && path === "/diet") {
      const body = route.request().postDataJSON() as Record<string, unknown>;
      diet.push(body);
      return route.fulfill(json({ id: diet.length, ...body }, 201));
    }
    if (method === "GET" && path === "/diet") {
      return route.fulfill(json(diet));
    }

    if (method === "GET" && path === "/clubs") {
      return route.fulfill(
        json([
          {
            clubId: WELLNESS_E2E.clubId,
            name: WELLNESS_E2E.clubName,
            clubType: "Walking",
            description: "Community walking club",
            joined: joinedClubs.has(WELLNESS_E2E.clubId),
          },
        ]),
      );
    }
    if (method === "POST" && path === `/clubs/${WELLNESS_E2E.clubId}/join`) {
      joinedClubs.add(WELLNESS_E2E.clubId);
      return route.fulfill(
        json({ clubId: WELLNESS_E2E.clubId, personCpid: WELLNESS_E2E.cpid, role: "MEMBER" }, 201),
      );
    }

    if (method === "GET" && path === "/challenges") {
      return route.fulfill(
        json([
          {
            challengeId: WELLNESS_E2E.challengeId,
            title: WELLNESS_E2E.challengeTitle,
            challengeType: "STEPS",
            targetValue: 10000,
            unit: "steps",
            startDate: "2026-06-01",
            endDate: "2026-06-30",
            status: "ACTIVE",
            joined: joinedChallenges.has(WELLNESS_E2E.challengeId),
          },
        ]),
      );
    }
    if (method === "POST" && path === `/challenges/${WELLNESS_E2E.challengeId}/join`) {
      joinedChallenges.add(WELLNESS_E2E.challengeId);
      return route.fulfill(
        json({ challengeId: WELLNESS_E2E.challengeId, personCpid: WELLNESS_E2E.cpid }, 201),
      );
    }

    if (method === "GET" && path === "/screening-programmes") {
      return route.fulfill(
        json([
          {
            programmeCode: "SCR-HIV-ANNUAL",
            title: "HIV screening (annual)",
            frequencyMonths: 12,
          },
        ]),
      );
    }

    if (method === "GET" && path === "/routes") {
      return route.fulfill(
        json([
          {
            routeCode: "ROUTE-AVONDALE-LOOP",
            title: "Avondale wellness loop",
            distanceKm: 4.2,
            elevationM: 45,
            difficulty: "EASY",
          },
        ]),
      );
    }

    if (method === "GET" && path.startsWith("/coaching/nudges")) {
      return route.fulfill(
        json([
          {
            nudgeType: "HABIT",
            message: "Log 10 minutes of movement before noon.",
            channel: "IN_APP",
          },
        ]),
      );
    }

    return route.continue();
  });

  await page.route("**/internal/v1/mobile/citizen/wellness/**", async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace(/.*\/internal\/v1\/mobile\/citizen\/wellness/, "");
    const method = route.request().method();

    if (path.startsWith("/activities")) {
      if (method === "GET") {
        return route.fulfill(json({ data: activities }));
      }
      if (method === "POST") {
        const body = route.request().postDataJSON() as Record<string, unknown>;
        activities.push(body);
        return route.fulfill(json({ data: body }, 201));
      }
    }
    if (path.startsWith("/challenges")) {
      return route.continue();
    }
    return route.fulfill(json({ data: [] }));
  });
}
