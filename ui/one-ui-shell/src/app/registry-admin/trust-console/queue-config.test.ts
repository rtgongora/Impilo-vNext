import { describe, expect, it } from "vitest";
import { QUEUE_CONFIGS, queueConfig } from "./queue-config";

describe("trust-console queue config", () => {
  it("covers exactly the five BFF queues", () => {
    expect(QUEUE_CONFIGS.map((q) => q.name).sort()).toEqual([
      "assurance-upgrades",
      "facility-admin-appointments",
      "org-access-requests",
      "pending-invitations",
      "provider-access-requests",
    ]);
  });

  it("invitations queue offers only resend/revoke lifecycle actions", () => {
    expect(queueConfig("pending-invitations").decisions).toEqual(["RESEND", "REVOKE"]);
    const view = queueConfig("pending-invitations").toView({
      id: "batch-1:row-1",
      importBatchId: "batch-1",
      rowId: "row-1",
      providerPublicId: "PROV-9",
      matchedHealthId: "c000-1",
      outcome: "ISSUED",
      invitation: { status: "sent", expiresAt: "2026-07-17T00:00:00Z" },
    });
    expect(view.id).toBe("batch-1:row-1");
    expect(view.status).toBe("sent");
    expect(view.detail.some((d) => d.label === "Expires")).toBe(true);
  });

  it("facility admin claims are approve-only (no downstream reject transition)", () => {
    expect(queueConfig("facility-admin-appointments").decisions).toEqual(["APPROVED"]);
    expect(queueConfig("facility-admin-appointments").decisionNote).toBeTruthy();
  });

  it("maps a varapi provider access request view", () => {
    const view = queueConfig("provider-access-requests").toView({
      publicId: "PAR-1234",
      requestType: "NEW_PROVIDER",
      status: "PENDING_NATIONAL_REVIEW",
      profession: "Medical Officer",
      nextActor: "NATIONAL_ADMINISTRATOR",
      createdAt: "2026-07-09T00:00:00Z",
    });
    expect(view.id).toBe("PAR-1234");
    expect(view.title).toContain("NEW_PROVIDER");
    expect(view.status).toBe("PENDING_NATIONAL_REVIEW");
    expect(view.detail[0]).toEqual({ label: "Next actor", value: "NATIONAL_ADMINISTRATOR" });
  });

  it("maps a tuso appointment with a numeric id", () => {
    const view = queueConfig("facility-admin-appointments").toView({
      id: 7,
      facilityUuid: "99999999-8888-7777-6666-555555555555",
      personHealthId: "hid-1",
      role: "FACILITY_ADMINISTRATOR",
      approvalState: "PENDING",
    });
    expect(view.id).toBe("7");
    expect(view.status).toBe("PENDING");
    expect(view.detail.find((d) => d.label === "Person")?.value).toBe("hid-1");
  });

  it("maps an assurance upgrade request with level transition", () => {
    const view = queueConfig("assurance-upgrades").toView({
      id: 5,
      currentLevel: "LOA1",
      targetLevel: "LOA2",
      status: "PENDING",
      actorId: "actor-1",
    });
    expect(view.subtitle).toBe("LOA1 → LOA2");
    expect(view.detail.find((d) => d.label === "Subject")?.value).toBe("actor-1");
  });

  it("never crashes on missing fields — renders placeholders instead", () => {
    for (const config of QUEUE_CONFIGS) {
      const view = config.toView({});
      expect(view.status).toBe("—");
      expect(typeof view.id).toBe("string");
    }
  });
});
