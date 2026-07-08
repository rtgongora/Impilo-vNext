"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ArrowLeft,
  Building2,
  Clock,
  FileText,
  Loader2,
  Mail,
  ShieldCheck,
  Stethoscope,
  Upload,
} from "lucide-react";
import {
  facilityClaimErrorMessage,
  useAcceptOrgInvitation,
  useAppointFacilityClaim,
  useFacilityClaimEligibility,
  usePreviewFacilityClaim,
  useUploadFacilityEvidence,
} from "@/hooks/queries/useFacilityClaim";

type ClaimPath = "letter" | "document" | "org";

/**
 * Facility administrator self-service claim journey (IATG Wave 2, WS-E).
 *
 * Flow: eligibility gate (platform-legitimacy) → choose path (appointment letter /
 * document / org invitation) → masked preview + consent → appoint → pending-approval
 * confirmation.
 *
 * Doctrine: honest states only — every lane binds to real BFF endpoints (appointment-letter,
 * document upload, and organisation-invitation accept); downstream verdicts surface verbatim.
 * Downstream 409 verdicts are shown verbatim, and facility codes / evidence refs are masked
 * end-to-end. The subject is the facility UUID; the claimant is always the signed-in Health ID.
 */
export function FacilityClaimJourney({ facilityUuid }: { facilityUuid?: string }) {
  const [path, setPath] = useState<ClaimPath | null>(null);
  const eligibility = useFacilityClaimEligibility(facilityUuid ?? "");

  if (!facilityUuid) {
    return (
      <section
        className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-4"
        data-testid="facility-claim-journey"
      >
        <div className="flex items-start gap-2 text-sm text-warning-foreground">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            No facility selected. Open a facility and choose &ldquo;Claim administration&rdquo; to
            request administrator access.
          </p>
        </div>
      </section>
    );
  }

  if (eligibility.isLoading) {
    return (
      <section
        className="rounded-xl border border-border bg-background px-4 py-6"
        data-testid="facility-claim-journey"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Checking whether this facility can be administered right now…
        </div>
      </section>
    );
  }

  if (eligibility.isError) {
    return (
      <section
        className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-4"
        data-testid="facility-claim-journey"
      >
        <div className="flex items-start gap-2 text-sm text-warning-foreground">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Eligibility could not be checked right now. Nothing was changed — retry once the
            service is reachable.
          </p>
        </div>
      </section>
    );
  }

  const data = eligibility.data;
  const claimable = !!data?.claimable;
  const allowedOnPlatform = !!data?.allowedOnPlatform;

  return (
    <section
      className="space-y-4 rounded-xl border border-border bg-card p-5 shadow-sm"
      data-testid="facility-claim-journey"
    >
      <header className="flex items-start gap-3">
        <Building2 className="mt-1 h-5 w-5 shrink-0 text-primary" />
        <div>
          <h1 className="text-lg font-semibold text-foreground">
            Claim administrator access for this facility
          </h1>
          <p className="text-sm text-muted-foreground">
            You stay signed in as a person; a facility claim only requests administrator access —
            it is recorded against your Health ID and activates nothing until an approver actions
            it (Health OS §6).
          </p>
        </div>
      </header>

      {!claimable ? (
        <NotClaimablePanel
          allowedOnPlatform={allowedOnPlatform}
          activeAdministratorCount={data?.activeAdministratorCount ?? 0}
        />
      ) : null}

      {claimable && path === null ? <PathChooser onChoose={setPath} /> : null}

      {claimable && path !== null ? (
        <div className="space-y-3">
          <button
            type="button"
            onClick={() => setPath(null)}
            className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Choose a different route
          </button>
          {path === "letter" ? <AppointmentLetterFlow facilityUuid={facilityUuid} /> : null}
          {path === "document" ? <DocumentEvidenceFlow facilityUuid={facilityUuid} /> : null}
          {path === "org" ? <OrgInvitationFlow facilityUuid={facilityUuid} /> : null}
        </div>
      ) : null}

      <footer className="border-t border-border pt-3 text-xs text-muted-foreground">
        Nompilo can guide you through claiming administration of a facility, but approval decisions
        always rest with the registry.
      </footer>
    </section>
  );
}

// ── Not claimable ─────────────────────────────────────────────────────────────

function NotClaimablePanel({
  allowedOnPlatform,
  activeAdministratorCount,
}: {
  allowedOnPlatform: boolean;
  activeAdministratorCount: number;
}) {
  return (
    <div
      className="rounded-lg border border-warning/35 bg-warning-soft px-4 py-3 text-sm"
      data-testid="facility-claim-not-claimable"
    >
      <div className="flex items-start gap-2">
        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-warning-foreground" />
        <div className="space-y-1">
          <p className="font-medium text-foreground">This facility cannot be claimed right now.</p>
          <p className="text-muted-foreground">
            {allowedOnPlatform
              ? "The facility is recognised on the platform, but administrator claims are not open for it at the moment."
              : "The facility is not currently allowed on the platform, so administrator access cannot be requested."}
            {activeAdministratorCount > 0
              ? ` It has ${activeAdministratorCount} active administrator${activeAdministratorCount === 1 ? "" : "s"}.`
              : ""}
          </p>
        </div>
      </div>
    </div>
  );
}

// ── Path chooser ────────────────────────────────────────────────────────────

const PATHS: { id: ClaimPath; icon: typeof FileText; title: string; body: string }[] = [
  {
    id: "letter",
    icon: FileText,
    title: "I have an appointment letter",
    body: "Reference your administrator appointment letter. Preview the facility first, then request access.",
  },
  {
    id: "document",
    icon: Stethoscope,
    title: "Upload a supporting document",
    body: "Upload an appointment letter or supporting document, then request administrator access with it attached.",
  },
  {
    id: "org",
    icon: Mail,
    title: "Organisation invitation",
    body: "Accept an invitation from an organisation to administer a facility using your invitation token.",
  },
];

function PathChooser({ onChoose }: { onChoose: (p: ClaimPath) => void }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2" data-testid="facility-claim-path-chooser">
      {PATHS.map(({ id, icon: Icon, title, body }) => (
        <button
          key={id}
          type="button"
          onClick={() => onChoose(id)}
          className="rounded-lg border border-border bg-background px-4 py-3 text-left hover:border-primary/40 hover:bg-primary-soft"
        >
          <span className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Icon className="h-4 w-4 text-primary" /> {title}
          </span>
          <span className="mt-1 block text-xs text-muted-foreground">{body}</span>
        </button>
      ))}
    </div>
  );
}

// ── Shared bits ─────────────────────────────────────────────────────────────

function ErrorNote({ err, fallback }: { err: unknown; fallback: string }) {
  return (
    <div
      className="flex items-start gap-2 rounded-lg border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-foreground"
      role="alert"
    >
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
      <p>{facilityClaimErrorMessage(err, fallback)}</p>
    </div>
  );
}

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";
const primaryButtonClass =
  "inline-flex items-center gap-2 rounded-lg bg-primary-hover px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700 disabled:cursor-not-allowed disabled:opacity-50";

// ── Appointment letter flow ──────────────────────────────────────────────────

function AppointmentLetterFlow({ facilityUuid }: { facilityUuid: string }) {
  const [role, setRole] = useState("");
  const [evidenceRef, setEvidenceRef] = useState("");
  const [consent, setConsent] = useState(false);
  const preview = usePreviewFacilityClaim();
  const appoint = useAppointFacilityClaim();

  if (appoint.data) {
    return (
      <div
        className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm"
        data-testid="facility-claim-confirmation"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <Clock className="h-4 w-4 text-primary" /> Administrator request recorded — pending approval
        </p>
        <p className="mt-1 text-muted-foreground">
          {appoint.data.note ??
            "Your facility administrator request was recorded and is pending approval. No administrative capability is active until it is approved."}
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-3"
      data-testid="facility-claim-letter-flow"
      onSubmit={(e) => {
        e.preventDefault();
        if (!preview.data) preview.mutate(facilityUuid);
        else if (consent)
          appoint.mutate({
            facilityUuid,
            consent: true,
            role: role.trim() || undefined,
            evidenceRef: evidenceRef.trim() || undefined,
          });
      }}
    >
      {preview.isError ? (
        <ErrorNote err={preview.error} fallback="That facility could not be previewed." />
      ) : null}
      {appoint.isError ? (
        <ErrorNote
          err={appoint.error}
          fallback="The claim could not be submitted. You may already administer this facility."
        />
      ) : null}

      {preview.data ? (
        <div
          className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm"
          data-testid="facility-claim-preview"
        >
          <p className="font-medium text-foreground">You are requesting access to:</p>
          <p className="mt-1 text-muted-foreground">
            <span className="font-medium text-foreground">{preview.data.name}</span> —{" "}
            <span className="font-mono">{preview.data.facilityCode}</span>
            {preview.data.facilityType ? `, ${preview.data.facilityType}` : ""}
            {preview.data.district || preview.data.province
              ? ` (${[preview.data.district, preview.data.province].filter(Boolean).join(", ")})`
              : ""}
          </p>

          <label className="mt-3 block text-xs font-medium text-foreground" htmlFor="facility-role-input">
            Administrator role (optional)
          </label>
          <input
            id="facility-role-input"
            value={role}
            onChange={(e) => setRole(e.target.value)}
            placeholder="e.g. FACILITY_ADMIN"
            className={inputClass}
          />
          <label
            className="mt-2 block text-xs font-medium text-foreground"
            htmlFor="facility-evidence-input"
          >
            Appointment letter reference (optional)
          </label>
          <input
            id="facility-evidence-input"
            value={evidenceRef}
            onChange={(e) => setEvidenceRef(e.target.value)}
            placeholder="e.g. LETTER-2026-001"
            className={inputClass}
          />

          <label className="mt-2 flex items-start gap-2 text-xs text-foreground">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              className="mt-0.5"
            />
            I confirm I am appointed to administer this facility and consent to recording this
            request against my Health ID. Only a masked reference is stored.
          </label>
        </div>
      ) : null}

      <button
        type="submit"
        className={primaryButtonClass}
        disabled={preview.isPending || appoint.isPending || (!!preview.data && !consent)}
      >
        {(preview.isPending || appoint.isPending) && (
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
        )}
        {preview.data ? "Request administrator access" : "Preview this facility"}
      </button>

      {!preview.data ? (
        <p className="text-xs text-muted-foreground">
          Preview shows a masked facility summary before you commit. Nothing is recorded until you
          consent and submit.
        </p>
      ) : null}

      <p className="border-t border-border pt-2 text-xs text-muted-foreground">
        Need to return to the facility?{" "}
        <Link href={`/facility/${facilityUuid}`} className="font-medium text-primary underline">
          Back to facility
        </Link>
      </p>
    </form>
  );
}

// ── Document evidence flow ───────────────────────────────────────────────────

function DocumentEvidenceFlow({ facilityUuid }: { facilityUuid: string }) {
  const [file, setFile] = useState<File | null>(null);
  const [consent, setConsent] = useState(false);
  const upload = useUploadFacilityEvidence();
  const preview = usePreviewFacilityClaim();
  const appoint = useAppointFacilityClaim();
  const evidenceRef = upload.data?.evidenceRef;

  if (appoint.data) {
    return (
      <div
        className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm"
        data-testid="facility-claim-document-confirmation"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <Clock className="h-4 w-4 text-primary" /> Administrator request recorded — pending approval
        </p>
        <p className="mt-1 text-muted-foreground">
          {appoint.data.note ??
            "Your facility administrator request was recorded with your supporting document and is pending approval."}
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-3"
      data-testid="facility-claim-document-flow"
      onSubmit={(e) => {
        e.preventDefault();
        if (!evidenceRef) {
          if (file) upload.mutate(file);
        } else if (!preview.data) {
          preview.mutate(facilityUuid);
        } else if (consent) {
          appoint.mutate({ facilityUuid, consent: true, evidenceRef });
        }
      }}
    >
      {upload.isError ? (
        <ErrorNote err={upload.error} fallback="The document could not be uploaded. Try a smaller PDF or image." />
      ) : null}
      {preview.isError ? (
        <ErrorNote err={preview.error} fallback="That facility could not be previewed." />
      ) : null}
      {appoint.isError ? (
        <ErrorNote
          err={appoint.error}
          fallback="The claim could not be submitted. You may already administer this facility."
        />
      ) : null}

      {!evidenceRef ? (
        <div>
          <label className="block text-xs font-medium text-foreground" htmlFor="facility-document-input">
            Supporting document (PDF or image, up to 10 MB)
          </label>
          <input
            id="facility-document-input"
            type="file"
            accept=".pdf,image/*"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className={inputClass}
          />
        </div>
      ) : (
        <div
          className="rounded-lg border border-primary/25 bg-primary-soft px-3 py-2 text-xs text-muted-foreground"
          data-testid="facility-claim-document-uploaded"
        >
          Uploaded <span className="font-medium text-foreground">{upload.data?.filename ?? "document"}</span>
          {upload.data?.scanStatus ? ` — scan: ${upload.data.scanStatus}` : ""}. Preview the facility, then
          submit your request with this document attached.
        </div>
      )}

      {preview.data ? (
        <div
          className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm"
          data-testid="facility-claim-document-preview"
        >
          <p className="font-medium text-foreground">You are requesting access to:</p>
          <p className="mt-1 text-muted-foreground">
            <span className="font-medium text-foreground">{preview.data.name}</span> —{" "}
            <span className="font-mono">{preview.data.facilityCode}</span>
          </p>
          <label className="mt-2 flex items-start gap-2 text-xs text-foreground">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              className="mt-0.5"
            />
            I confirm I am appointed to administer this facility and consent to recording this request,
            with my uploaded document as evidence, against my Health ID.
          </label>
        </div>
      ) : null}

      <button
        type="submit"
        className={primaryButtonClass}
        disabled={
          upload.isPending ||
          preview.isPending ||
          appoint.isPending ||
          (!evidenceRef && !file) ||
          (!!preview.data && !consent)
        }
      >
        {(upload.isPending || preview.isPending || appoint.isPending) && (
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
        )}
        {!evidenceRef ? (
          <>
            <Upload className="h-3.5 w-3.5" /> Upload document
          </>
        ) : !preview.data ? (
          "Preview this facility"
        ) : (
          "Request administrator access"
        )}
      </button>

      <p className="border-t border-border pt-2 text-xs text-muted-foreground">
        Need to return to the facility?{" "}
        <Link href={`/facility/${facilityUuid}`} className="font-medium text-primary underline">
          Back to facility
        </Link>
      </p>
    </form>
  );
}

// ── Organisation invitation flow ─────────────────────────────────────────────

function OrgInvitationFlow({ facilityUuid }: { facilityUuid: string }) {
  const [token, setToken] = useState("");
  const [consent, setConsent] = useState(false);
  const accept = useAcceptOrgInvitation();

  if (accept.data) {
    return (
      <div
        className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm"
        data-testid="facility-claim-org-invitation-accepted"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <ShieldCheck className="h-4 w-4 text-primary" /> Invitation accepted — administering affiliation active
        </p>
        <p className="mt-1 text-muted-foreground">
          {accept.data.note ??
            "Your organisation invitation was accepted and your administering affiliation is now active."}
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-3"
      data-testid="facility-claim-org-invitation-flow"
      onSubmit={(e) => {
        e.preventDefault();
        if (consent && token.trim()) accept.mutate(token.trim());
      }}
    >
      {accept.isError ? (
        <ErrorNote
          err={accept.error}
          fallback="That invitation token could not be accepted. It may be unknown, expired, or already used."
        />
      ) : null}

      <label className="block text-xs font-medium text-foreground" htmlFor="org-invitation-token-input">
        Invitation token
      </label>
      <input
        id="org-invitation-token-input"
        value={token}
        onChange={(e) => setToken(e.target.value)}
        placeholder="Paste the token from your organisation invitation"
        className={inputClass}
        autoComplete="off"
      />

      <label className="flex items-start gap-2 text-xs text-foreground">
        <input
          type="checkbox"
          checked={consent}
          onChange={(e) => setConsent(e.target.checked)}
          className="mt-0.5"
        />
        I confirm this invitation was issued to me and consent to recording the administering
        affiliation against my Health ID.
      </label>

      <button
        type="submit"
        className={primaryButtonClass}
        disabled={accept.isPending || !token.trim() || !consent}
      >
        {accept.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
        Accept invitation
      </button>

      <p className="border-t border-border pt-2 text-xs text-muted-foreground">
        Need to return to the facility?{" "}
        <Link href={`/facility/${facilityUuid}`} className="font-medium text-primary underline">
          Back to facility
        </Link>
      </p>
    </form>
  );
}
