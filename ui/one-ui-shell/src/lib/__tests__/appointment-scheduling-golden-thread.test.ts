import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("appointment scheduling golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("routes citizen check-in through shared outpatient spine helper", () => {
    const citizenPage = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/home/appointments/[appointmentId]/page.tsx"),
      "utf8",
    );
    expect(citizenPage).toContain("buildPostCheckInRoute");
    expect(citizenPage).toContain("useCheckInAppointment");
  });

  it("routes provider scheduled queue check-in to encounter spine", () => {
    const providerPage = readFileSync(
      resolve(repoRoot, "ui/one-ui-shell/src/app/queue/scheduled/page.tsx"),
      "utf8",
    );
    expect(providerPage).toContain("buildPostCheckInRoute");
  });

  it("booking-first check-in enriches journey correlation in BFF", () => {
    const service = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/service/AppointmentCheckInService.java",
      ),
      "utf8",
    );
    expect(service).toContain("checkInAppointment");
    expect(service).toContain("core_transaction_id");
    expect(service).toContain("journey_id");
  });

  it("exposes citizen mobile check-in on shared appointment service", () => {
    const citizenController = readFileSync(
      resolve(
        repoRoot,
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/citizen/CitizenAppointmentController.java",
      ),
      "utf8",
    );
    const mobileService = readFileSync(
      resolve(repoRoot, "apps/mobile/citizen-app/src/services/appointmentService.ts"),
      "utf8",
    );
    expect(citizenController).toContain("appointmentCheckInService.checkIn");
    expect(mobileService).toContain("/check-in");
  });
});
