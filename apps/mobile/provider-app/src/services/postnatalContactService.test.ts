/**
 * CHW community postnatal contact service tests — the wire contract in
 * docs/clinical/rmnp/chw-community-postnatal-mobile-contract.md §4, proved at the service layer.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";
import { ApiError } from "@impilo/mobile-api-client";

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockQueueOnRetryable = vi.fn();

vi.mock("@impilo/mobile-api-client", async () => {
  const actual = await vi.importActual<typeof import("@impilo/mobile-api-client")>("@impilo/mobile-api-client");
  return {
    ...actual,
    apiClient: { get: (...a: unknown[]) => mockGet(...a), post: (...a: unknown[]) => mockPost(...a) },
  };
});

vi.mock("./offlineClinicalQueue", () => ({
  queueClinicalCreateOnRetryableError: (...a: unknown[]) => mockQueueOnRetryable(...a),
}));

describe("postnatalContactService", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
    mockQueueOnRetryable.mockReset();
  });

  it("records a contact live and forwards nulls for screeningComplete/dangerSignsPresent/breastfeedingStatus unchanged", async () => {
    mockPost.mockResolvedValue({
      data: {
        data: {
          postnatal_contact_id: "pnc-1",
          mother_cpid: "CP-1",
          contact_timing: "DAY_3",
          contact_setting: "HOME",
          contacted_at: "2026-07-30T08:00:00Z",
          screening_complete: null,
          danger_signs_present: null,
          breastfeeding_status: null,
          referral_made: false,
          referral_reason: null,
          client_offline_id: "offline-1",
          replayed: false,
        },
      },
    });
    const { recordPostnatalContact } = await import("./postnatalContactService");

    const res = await recordPostnatalContact({
      motherCpid: "CP-1",
      contactTiming: "DAY_3",
      contactSetting: "HOME",
      clientOfflineId: "offline-1",
    });

    expect(res.queued).toBe(false);
    expect(res.record?.postnatal_contact_id).toBe("pnc-1");
    expect(mockPost).toHaveBeenCalledWith(
      "/internal/v1/confidential/community/postnatal/contacts",
      expect.objectContaining({
        motherCpid: "CP-1",
        contactSetting: "HOME",
        clientOfflineId: "offline-1",
      }),
    );
    // Nothing in this module fills in screeningComplete/dangerSignsPresent/breastfeedingStatus —
    // they must be absent (undefined) rather than coerced to false/NONE when the caller never set them.
    const [, sentPayload] = mockPost.mock.calls[0];
    expect(sentPayload).not.toHaveProperty("screeningComplete");
    expect(sentPayload).not.toHaveProperty("dangerSignsPresent");
    expect(sentPayload).not.toHaveProperty("breastfeedingStatus");
  });

  it("never defaults contactSetting — the caller's exact value reaches the wire", async () => {
    mockPost.mockResolvedValue({ data: { data: { postnatal_contact_id: "pnc-2", replayed: false } } });
    const { recordPostnatalContact } = await import("./postnatalContactService");

    await recordPostnatalContact({
      motherCpid: "CP-2",
      contactTiming: "WITHIN_24H",
      contactSetting: "COMMUNITY",
      clientOfflineId: "offline-2",
    });

    const [, sentPayload] = mockPost.mock.calls[0] as [string, Record<string, unknown>];
    expect(sentPayload.contactSetting).toBe("COMMUNITY");
  });

  it("queues a PCT_UNAVAILABLE 502 offline keyed by the caller's clientOfflineId, and reports queued rather than throwing", async () => {
    mockPost.mockRejectedValue(
      new ApiError({ code: "PCT_UNAVAILABLE", message: "upstream error", status: 502, correlationId: "c1" }),
    );
    mockQueueOnRetryable.mockResolvedValue({ recordId: "offline-3", queued: true });
    const { recordPostnatalContact } = await import("./postnatalContactService");

    const res = await recordPostnatalContact({
      motherCpid: "CP-3",
      contactTiming: "DAY_7_14",
      contactSetting: "VIRTUAL",
      clientOfflineId: "offline-3",
    });

    expect(res.queued).toBe(true);
    expect(res.clientOfflineId).toBe("offline-3");
    expect(res.record).toBeUndefined();
    expect(mockQueueOnRetryable).toHaveBeenCalledWith(
      expect.any(ApiError),
      expect.objectContaining({
        collection: "postnatal_contacts",
        path: "/internal/v1/confidential/community/postnatal/contacts",
        recordId: "offline-3",
      }),
    );
  });

  it("rethrows a 422 field refusal rather than queueing it — a CHW must fix the field, not retry the same answer", async () => {
    const refusal = new ApiError({
      code: "PNC_SETTING_REQUIRED",
      message: "A postnatal contact must say where it happened.",
      status: 422,
      correlationId: "c2",
    });
    mockPost.mockRejectedValue(refusal);
    mockQueueOnRetryable.mockResolvedValue(null);
    const { recordPostnatalContact } = await import("./postnatalContactService");

    await expect(
      recordPostnatalContact({
        motherCpid: "CP-4",
        contactTiming: "WEEK_6",
        contactSetting: "HOME",
        clientOfflineId: "offline-4",
      }),
    ).rejects.toBe(refusal);
  });

  it("reads a mother's postnatal contacts, rendering danger_signs_present:null as unscreened rather than coercing it", async () => {
    mockGet.mockResolvedValue({
      data: {
        data: [
          {
            postnatal_contact_id: "pnc-5",
            mother_cpid: "CP-5",
            contact_timing: "DAY_3",
            contact_setting: "HOME",
            contacted_at: "2026-07-27T08:00:00Z",
            screening_complete: false,
            danger_signs_present: null,
            breastfeeding_status: "NOT_ASSESSED",
            referral_made: false,
            referral_reason: null,
            client_offline_id: "offline-5",
            replayed: false,
          },
        ],
      },
    });
    const { getPostnatalContacts } = await import("./postnatalContactService");

    const contacts = await getPostnatalContacts("CP-5");

    expect(contacts).toHaveLength(1);
    expect(contacts[0].danger_signs_present).toBeNull();
    expect(contacts[0].breastfeeding_status).toBe("NOT_ASSESSED");
    expect(mockGet).toHaveBeenCalledWith("/internal/v1/confidential/community/postnatal/contacts/CP-5");
  });

  it("returns an empty array rather than throwing when nothing is visible — never rendered as 'first visit'", async () => {
    mockGet.mockResolvedValue({ data: { data: [] } });
    const { getPostnatalContacts } = await import("./postnatalContactService");

    const contacts = await getPostnatalContacts("CP-6");
    expect(contacts).toEqual([]);
  });
});
