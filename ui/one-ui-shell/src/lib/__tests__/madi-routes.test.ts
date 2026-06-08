import { describe, expect, it } from "vitest";
import { ROUTES } from "../routes";

const MADI_PATHS: Array<[string, string, "auth" | "facility" | "role", string?]> = [
  ["/madi", "Madi Blood Services", "auth"],
  ["/madi/donor", "My Donor Hub", "auth"],
  ["/madi/donor/register", "Become a Donor", "auth"],
  ["/madi/drives", "Donation Drives", "facility"],
  ["/madi/blood-bank", "Local Blood Bank", "facility"],
  ["/madi/central-bank", "Central Blood Bank", "role", "ADMIN"],
  ["/madi/orders", "Order Blood", "facility"],
  ["/madi/transfusion", "Record Transfusion", "facility"],
  ["/madi/haemovigilance", "Haemovigilance", "facility"],
  ["/madi/dashboard", "Madi Dashboard", "facility"],
  ["/madi/processing", "Blood Processing", "facility"],
  ["/madi/logistics", "Blood Logistics", "facility"],
];

describe("Madi route registry", () => {
  it("registers all primary Madi routes with expected guards", () => {
    for (const [path, title, guard, role] of MADI_PATHS) {
      const route = ROUTES.find((r) => r.path === path);
      expect(route, `missing route ${path}`).toBeTruthy();
      expect(route?.pageTitle).toBe(title);
      expect(route?.guard).toBe(guard);
      if (role) {
        expect(route?.requiredRole).toBe(role);
      }
    }
  });

  it("assigns navZone work to facility routes and life to donor routes", () => {
    const donorRoute = ROUTES.find((r) => r.path === "/madi/donor");
    expect(donorRoute?.navZone).toBe("life");

    const hubRoute = ROUTES.find((r) => r.path === "/madi");
    expect(hubRoute?.navZone).toBe("work");

    const drivesRoute = ROUTES.find((r) => r.path === "/madi/drives");
    expect(drivesRoute?.navZone).toBe("work");
  });

  it("registers dynamic Madi segments", () => {
    for (const path of ["/madi/drives/[driveId]", "/madi/orders/[orderId]", "/madi/transfusion/[episodeId]"]) {
      expect(ROUTES.some((r) => r.path === path), path).toBe(true);
    }
  });
});
