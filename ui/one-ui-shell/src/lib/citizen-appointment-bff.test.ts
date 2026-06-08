import { describe, expect, it } from "vitest";
import {
  normalizeCitizenAppointmentList,
  toCitizenAppointmentRequest,
} from "./citizen-appointment-bff";

describe("citizen-appointment-bff", () => {
  it("maps request payload to citizen BFF contract", () => {
    expect(
      toCitizenAppointmentRequest({
        facilityId: "fac-1",
        appointmentType: "GENERAL",
        preferredDate: "2026-06-10T09:00:00Z",
        reason: "  Check-up  ",
      }),
    ).toEqual({
      facilityId: "fac-1",
      appointmentType: "GENERAL",
      preferredDate: "2026-06-10T09:00:00Z",
      reason: "Check-up",
    });
  });

  it("normalizes camelCase citizen appointment rows", () => {
    const rows = normalizeCitizenAppointmentList({
      data: [
        {
          id: "apt-1",
          facilityName: "Harare Central",
          appointmentType: "GENERAL",
          status: "CONFIRMED",
          scheduledAt: "2026-06-10T09:00:00Z",
        },
      ],
    });

    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({
      id: "apt-1",
      facilityName: "Harare Central",
      appointmentType: "GENERAL",
      status: "CONFIRMED",
      scheduledAt: "2026-06-10T09:00:00Z",
    });
  });
});
