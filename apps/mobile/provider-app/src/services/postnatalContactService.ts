/**
 * CHW community postnatal contact — service client.
 *
 * Backend: experience-bff `/internal/v1/confidential/community/postnatal/contacts`
 * (`ConfidentialCommunityPostnatalController`), proxying pct-service's `PostnatalContactService`
 * (migration V436). This is the day-3-at-home visit: until V436 there was nowhere to record a
 * postnatal contact that happened at home, in the community, or virtually at all.
 *
 * Contract: docs/clinical/rmnp/chw-community-postnatal-mobile-contract.md §4 — the behaviours this
 * module exists to preserve on the wire, so a screen built on top of it cannot get them wrong by
 * accident:
 *
 *   1. `screeningComplete`, `dangerSignsPresent` and `breastfeedingStatus` are sent exactly as the
 *      caller passes them, nulls included. This module fills in none of them — an unscreened
 *      contact must arrive at pct as unscreened (`V436 chk_pnc_screening_gate` then refuses to let
 *      a danger-sign finding exist without a completed screen), and defaulting `breastfeedingStatus`
 *      would erase the difference between "not assessed" and "assessed as not breastfeeding".
 *   2. `contactSetting` is never defaulted to FACILITY. HOME, COMMUNITY and VIRTUAL are first-class;
 *      a caller must choose one explicitly.
 *   3. `clientOfflineId` makes a replay idempotent (pct returns the *existing* contact, 200, rather
 *      than minting a duplicate). This module never mints its own id — the caller supplies one
 *      stable id per captured visit and reuses it across every retry of that same visit, including
 *      across a PCT_UNAVAILABLE 502.
 *   4. A 502 (`ApiError.code === "PCT_UNAVAILABLE"`, or a network/timeout failure) is queued
 *      offline via the shared `offlineClinicalQueue` rather than surfaced as a lost submission —
 *      keyed by the caller's `clientOfflineId`, so the exact same packet is what eventually
 *      replays. A 422 is a field refusal a CHW can fix in the field, and is never queued: fixing
 *      it takes a new answer, not a retry of the same one.
 *
 * Response fields are snake_case, matching pct's `PostnatalContactEntity`/`toMap` output exactly.
 */

import { apiClient } from "@impilo/mobile-api-client";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";

const BASE = "/internal/v1/confidential/community/postnatal";

/** `pct_postnatal_contacts.chk_pnc_timing` (V436) — the WHO PNC contact schedule (PNC.S.01). */
export type PostnatalContactTiming = "WITHIN_24H" | "DAY_3" | "DAY_7_14" | "WEEK_6" | "ADDITIONAL";

/**
 * `chk_pnc_setting` (V436). HOME, COMMUNITY and VIRTUAL are first-class — never assume FACILITY
 * because the caller omitted a choice.
 */
export type PostnatalContactSetting = "FACILITY" | "HOME" | "COMMUNITY" | "VIRTUAL";

/**
 * `chk_pnc_breastfeeding` (V436). `NOT_ASSESSED` is a real, explicit answer distinct from `NONE`
 * (not breastfeeding) and from leaving the field unanswered (`undefined`/`null`, "did not even
 * ask") — three states, not two.
 */
export type BreastfeedingStatus = "EXCLUSIVE" | "PREDOMINANT" | "PARTIAL" | "NONE" | "NOT_ASSESSED";

export interface RecordPostnatalContactInput {
  motherCpid: string;
  pregnancyEpisodeId?: string;
  contactTiming: PostnatalContactTiming;
  /** Required by pct with no default — see module doc §2. */
  contactSetting: PostnatalContactSetting;
  contactNumber?: number;
  /** ISO-8601. Defaults to "now" at pct if omitted. */
  contactedAt?: string;
  daysSinceBirth?: number;
  attendedBy?: string;
  attendantCadre?: string;
  maternalCondition?: string;
  bleedingAssessment?: string;
  moodScreen?: string;
  /**
   * `undefined`/`null` = the screen was not (yet) completed. Only when this is `true` may
   * `dangerSignsPresent` carry a value — `chk_pnc_screening_gate` refuses the write otherwise.
   */
  screeningComplete?: boolean | null;
  /** `null` = not screened. Never set this to `false` to mean "not screened" — that is a
   * different, reassuring clinical claim the schema exists to distinguish from silence. */
  dangerSignsPresent?: boolean | null;
  breastfeedingStatus?: BreastfeedingStatus | null;
  breastfeedingDifficulty?: string;
  lactationSupportGiven?: boolean;
  contraceptionDiscussed?: boolean;
  postpartumContraceptionEpisodeId?: string;
  referralMade?: boolean;
  /** Required by pct whenever `referralMade` is true (`chk_pnc_referral`). */
  referralReason?: string;
  referralFacilityId?: string;
  facilityId?: string;
  /** Stable per captured visit — generate once, reuse on every retry of the same visit. */
  clientOfflineId: string;
  recordedBy?: string;
}

export interface PostnatalContactRecord {
  postnatal_contact_id: string;
  mother_cpid: string;
  contact_timing: string;
  contact_setting: string;
  contacted_at: string | null;
  screening_complete: boolean | null;
  danger_signs_present: boolean | null;
  breastfeeding_status: string | null;
  referral_made: boolean | null;
  referral_reason: string | null;
  client_offline_id: string | null;
  /** True when this `clientOfflineId` had already been applied — a replay, not a new visit. */
  replayed: boolean;
  sensitivity_class?: string;
  confidentiality_category?: string;
}

export interface RecordPostnatalContactResult {
  /** The saved (or replayed) contact when the write reached pct live. Absent when queued offline. */
  record?: PostnatalContactRecord;
  /** True when the submit could not reach pct and was captured offline instead — the visit is
   * saved on-device and will sync with its original `clientOfflineId`, not lost and not resent
   * as a fresh visit. */
  queued: boolean;
  clientOfflineId: string;
}

/**
 * Records a postnatal contact. Live when reachable; queued offline (replayed later with the same
 * `clientOfflineId`) on a retryable failure — see module doc §4. A 422 field refusal is rethrown
 * as-is so the caller can show pct's own reason (`PNC_SETTING_REQUIRED`,
 * `PNC_REFERRAL_REASON_REQUIRED`) rather than a generic failure.
 */
export async function recordPostnatalContact(
  input: RecordPostnatalContactInput,
): Promise<RecordPostnatalContactResult> {
  const payload: Record<string, unknown> = { ...input };
  try {
    const response = await apiClient.post<{ data: PostnatalContactRecord }>(`${BASE}/contacts`, payload);
    return { record: response.data.data, queued: false, clientOfflineId: input.clientOfflineId };
  } catch (error) {
    const offline = await queueClinicalCreateOnRetryableError(error, {
      collection: "postnatal_contacts",
      path: `${BASE}/contacts`,
      payload,
      recordId: input.clientOfflineId,
    });
    if (!offline) throw error;
    return { queued: true, clientOfflineId: input.clientOfflineId };
  }
}

/**
 * A mother's postnatal contacts. An empty array is never proof that no contact happened — it may
 * be a withholding under the confidentiality flip, indistinguishable from absence on purpose (see
 * `ConfidentialCommunityPostnatalController#contacts`'s doc comment). Do not render it as
 * "first visit".
 */
export async function getPostnatalContacts(motherCpid: string): Promise<PostnatalContactRecord[]> {
  const response = await apiClient.get<{ data: PostnatalContactRecord[] }>(
    `${BASE}/contacts/${encodeURIComponent(motherCpid)}`,
  );
  return response.data.data ?? [];
}
