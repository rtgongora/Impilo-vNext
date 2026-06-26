/**
 * Work / My-Professional / My-Life isolation boundary (L3 W4).
 *
 * Health OS doctrine: "One person anchor, many roles, many contexts." The three
 * provider shells share ONE person anchor but are governed as isolated zones —
 * the boundary between them is a *correctness invariant*, not a styling choice:
 *
 *  - WORK shell      → clinical/operational work ON OTHER PEOPLE under an active
 *                      Vashandi assignment. Work permissions NEVER reach the
 *                      actor's OWN citizen record.
 *  - MY-PROFESSIONAL → the actor's own provider profile, registration, CPD.
 *                      Never grants clinical authority over patients.
 *  - MY-LIFE         → the actor's own citizen/personal record only. Citizen
 *                      identity NEVER authorises clinical work.
 *
 * The two failure modes that are *unacceptable* (and therefore enforced here on
 * the client as defence-in-depth, in addition to server policy):
 *
 *   1. A provider, while in WORK mode, opening their OWN clinical record as a
 *      clinician (self-treatment) — SELF-TREATMENT-BLOCK / WORK-PRO-LIFE-ISOLATION.
 *   2. A citizen-only (or de-activated) identity reaching clinical WORK surfaces —
 *      LIFE-SELF-ONLY (already gated by isRouteBlockedForCitizen; this module
 *      adds the symmetric self-record protection).
 *
 * Authoritative enforcement is the Tshepo policy track (these IDs are SPEC-ONLY
 * here and route to CZO-LEAD / WS-OPA — see tshepo-policy-contract-list.md):
 *   TODO(policy WORK-PRO-LIFE-ISOLATION): server denies work perms over actor's own record.
 *   TODO(policy LIFE-SELF-ONLY): My-Life access is actor.id == subjectId only.
 *   TODO(policy PRO-PROFILE-SELF): My-Professional is own profile/CPD; no clinical-on-patients.
 *   TODO(policy WORK-REQUIRES-ASSIGNMENT): work actions require an active assignment + check-in.
 *   TODO(policy SELF-TREATMENT-BLOCK): opening own/family clinical record requires break-glass + audit.
 *
 * This module is pure (no React, no stores) so the invariant is unit-testable
 * and reusable by the guard, Nompilo, and any server-driven contract check.
 */

export type ShellZone = "work" | "professional" | "life";

/** The subset of actor identity the boundary needs — kept minimal and explicit. */
export interface BoundaryActor {
  /** Canonical person anchor (Health ID). */
  healthId: string | null;
  /** True when a Provider ID is activated for this session. */
  providerActivated: boolean;
}

export type BoundaryDecision =
  | { allowed: true }
  | { allowed: false; reason: BoundaryReason; redirectTo: string };

export type BoundaryReason =
  /** Work shell tried to open the actor's OWN clinical record (self-treatment). */
  | "SELF_TREATMENT_BLOCKED"
  /** Citizen/de-activated identity reached a work surface without provider activation. */
  | "WORK_REQUIRES_PROVIDER";

/**
 * Extract the EHR subject (patient Health ID) from a clinical work path, if any.
 * Matches `/ehr/<patientId>` and its sub-routes. Returns null for non-EHR paths.
 */
export function ehrSubjectFromPath(pathname: string): string | null {
  const path = pathname.split("?")[0];
  const m = path.match(/^\/ehr\/([^/]+)(?:\/.*)?$/);
  if (!m) return null;
  const subject = decodeURIComponent(m[1]).trim();
  return subject.length > 0 ? subject : null;
}

/**
 * Normalise a Health ID for self-comparison. Self-treatment must be caught even
 * when one side carries casing/whitespace differences, so we compare on a
 * lower-cased, trimmed key. Returns null when the id is absent/blank.
 */
function anchorKey(id: string | null | undefined): string | null {
  if (id == null) return null;
  const k = id.trim().toLowerCase();
  return k.length > 0 ? k : null;
}

/**
 * Is `subjectId` the actor's own person anchor? Used to detect self-treatment.
 * Conservative: any match against the actor's Health ID counts as self.
 */
export function isSelfSubject(actor: BoundaryActor, subjectId: string | null): boolean {
  const subject = anchorKey(subjectId);
  if (subject == null) return false;
  return subject === anchorKey(actor.healthId);
}

/**
 * Evaluate the Work/Pro/Life isolation invariant for a navigation into a
 * clinical WORK surface. Pure and synchronous; the guard calls this and the
 * server policy (track P) is the authoritative second enforcement.
 *
 * - Opening one's OWN EHR while in work mode → SELF_TREATMENT_BLOCKED (the
 *   actor's personal record lives in My-Life, never the clinical work shell).
 *   Break-glass for legitimate self/family emergency care is a server-mediated
 *   path (TODO(policy SELF-TREATMENT-BLOCK)) — never a silent client bypass.
 * - Reaching a clinical EHR surface without an activated Provider ID →
 *   WORK_REQUIRES_PROVIDER (citizen identity never authorises clinical work).
 */
export function evaluateClinicalWorkAccess(
  pathname: string,
  actor: BoundaryActor,
): BoundaryDecision {
  const subject = ehrSubjectFromPath(pathname);
  if (subject == null) {
    return { allowed: true }; // not a clinical-subject surface; other guards apply
  }

  // WORK-PRO-LIFE-ISOLATION + SELF-TREATMENT-BLOCK: the work shell must never
  // be the route through which a provider opens their own citizen record.
  if (isSelfSubject(actor, subject)) {
    return {
      allowed: false,
      reason: "SELF_TREATMENT_BLOCKED",
      // Send the actor to their personal record in My-Life, where self-access
      // is the correct (and only) lawful path.
      redirectTo: "/home",
    };
  }

  // LIFE-SELF-ONLY (symmetric): a non-activated/citizen identity cannot transact
  // clinically on another person. isRouteBlockedForCitizen already covers the
  // citizen-only case; this catches an authenticated-but-not-activated actor.
  if (!actor.providerActivated) {
    return {
      allowed: false,
      reason: "WORK_REQUIRES_PROVIDER",
      redirectTo: `/provider/activate?returnTo=${encodeURIComponent(pathname)}`,
    };
  }

  return { allowed: true };
}
