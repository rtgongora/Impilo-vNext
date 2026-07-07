"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  Building2,
  CheckCircle2,
  Clock,
  FileText,
  KeyRound,
  ListChecks,
  Loader2,
  RotateCcw,
  ShieldCheck,
  Stethoscope,
  UserPlus,
} from "lucide-react";
import {
  isFeaturePending,
  providerClaimErrorMessage,
  useClaimProviderProfile,
  usePreviewClaimToken,
  useProviderClaimEligibility,
  useRecoverProviderProfile,
  useSubmitClaimEvidence,
} from "@/hooks/queries/useProviderClaim";
import {
  employmentMatchErrorMessage,
  useEmploymentMatch,
} from "@/hooks/queries/useTrustEmploymentMatch";
import {
  useSubmitProviderAccessRequest,
  type ProviderAccessRequestView,
} from "@/hooks/queries/useProviderAccessRequest";

type ClaimPath = "token" | "council" | "ec" | "new" | "org" | "recover";

/**
 * Provider self-service claim / recovery journey (IATG WS-F).
 *
 * Flow: eligibility check → choose path (claim token / council number /
 * EC number / recover) → evidence submission → confirmation.
 *
 * Doctrine: honest states only — feature-pending lanes render as "coming
 * soon" (never fake success), downstream 409 verdicts are shown verbatim,
 * and recovery always surfaces the SAME masked Provider ID (recover-not-
 * reissue). Identifiers are masked end-to-end; the raw Provider ID is never
 * rendered here.
 */
export function ProviderClaimJourney() {
  const [path, setPath] = useState<ClaimPath | null>(null);
  const eligibility = useProviderClaimEligibility();

  if (eligibility.isLoading) {
    return (
      <section
        className="rounded-xl border border-border bg-background px-4 py-6"
        data-testid="provider-claim-journey"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Checking whether a provider profile is already linked to this Health ID…
        </div>
      </section>
    );
  }

  if (eligibility.isError) {
    return (
      <section
        className="rounded-xl border border-warning/35 bg-warning-soft px-4 py-4"
        data-testid="provider-claim-journey"
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
  const alreadyLinked = !!data?.alreadyLinked;

  return (
    <section
      className="space-y-4 rounded-xl border border-border bg-card p-5 shadow-sm"
      data-testid="provider-claim-journey"
    >
      <header className="flex items-start gap-3">
        <Stethoscope className="mt-1 h-5 w-5 shrink-0 text-primary" />
        <div>
          <h1 className="text-lg font-semibold text-foreground">
            Request Provider Access
          </h1>
          <p className="text-sm text-muted-foreground">
            You stay signed in as a person; claiming or recovering a Provider ID only enables
            provider role activation — regulated work still requires activation, licensure, and
            facility context (Health OS §6). Your Provider ID is confidential and is never a
            council or EC number.
          </p>
        </div>
      </header>

      {alreadyLinked ? (
        <AlreadyLinkedPanel
          maskedProviderId={data?.providerPublicId}
          onRecover={() => setPath("recover")}
          recovering={path === "recover"}
        />
      ) : null}

      {!alreadyLinked && path === null ? <PathChooser onChoose={setPath} /> : null}

      {path !== null ? (
        <div className="space-y-3">
          <button
            type="button"
            onClick={() => setPath(null)}
            className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Choose a different route
          </button>
          {path === "token" ? <ClaimTokenFlow /> : null}
          {path === "council" ? <CouncilNumberFlow /> : null}
          {path === "ec" ? <EcNumberFlow /> : null}
          {path === "new" ? <NewProviderFlow /> : null}
          {path === "org" ? <OrgInvitationFlow /> : null}
          {path === "recover" ? <RecoverFlow /> : null}
        </div>
      ) : null}

      <footer className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3 text-xs text-muted-foreground">
        <span>
          Document upload arrives with a later wave and will appear here when real. Verification
          decisions always rest with the registry — no shortcut is faked.
        </span>
        <Link
          href="/citizen/provider-claim/status"
          className="inline-flex items-center gap-1 font-medium text-primary hover:text-primary-hover"
          data-testid="provider-claim-status-link"
        >
          <ListChecks className="h-3.5 w-3.5" /> Check the status of a request
        </Link>
      </footer>
    </section>
  );
}

// ── Already linked ──────────────────────────────────────────────────────────

function AlreadyLinkedPanel({
  maskedProviderId,
  onRecover,
  recovering,
}: {
  maskedProviderId?: string;
  onRecover: () => void;
  recovering: boolean;
}) {
  return (
    <div
      className="rounded-lg border border-success/25 bg-success-soft px-4 py-3 text-sm"
      data-testid="provider-claim-already-linked"
    >
      <div className="flex items-start gap-2">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
        <div className="space-y-1">
          <p className="font-medium text-foreground">
            A provider profile is already linked to this Health ID
            {maskedProviderId ? (
              <>
                {" "}
                (<span className="font-mono">{maskedProviderId}</span>)
              </>
            ) : null}
            .
          </p>
          <p className="text-muted-foreground">
            To practise, activate the role from the{" "}
            <Link href="/provider/activate" className="font-medium text-primary underline">
              provider activation
            </Link>{" "}
            journey. Lost access on a new login?{" "}
            {!recovering ? (
              <button
                type="button"
                onClick={onRecover}
                className="font-medium text-primary underline"
              >
                Recover this profile
              </button>
            ) : (
              <span className="font-medium">Recovery selected below.</span>
            )}
          </p>
        </div>
      </div>
    </div>
  );
}

// ── Path chooser ────────────────────────────────────────────────────────────

const PATHS: { id: ClaimPath; icon: typeof KeyRound; title: string; body: string }[] = [
  {
    id: "token",
    icon: KeyRound,
    title: "I have a claim token",
    body: "Single-use token from your organisation or council preload. Preview first, then claim.",
  },
  {
    id: "council",
    icon: BadgeCheck,
    title: "I have a council number",
    body: "Your registration is checked against the council record; verification is reviewed before anything activates.",
  },
  {
    id: "ec",
    icon: FileText,
    title: "I have an EC (employment) number",
    body: "Matched against the workforce employment registry when that lane is live.",
  },
  {
    id: "new",
    icon: UserPlus,
    title: "I am a new provider",
    body: "No preload or token yet. This records a durable request routed to the right reviewer — no Provider ID is issued automatically.",
  },
  {
    id: "org",
    icon: Building2,
    title: "I was invited by an organisation",
    body: "Records a durable request routed to the inviting organisation to confirm your access.",
  },
  {
    id: "recover",
    icon: RotateCcw,
    title: "Recover an existing profile",
    body: "Re-link a provider profile you already own to this login. Your Provider ID never changes.",
  },
];

function PathChooser({ onChoose }: { onChoose: (p: ClaimPath) => void }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2" data-testid="provider-claim-path-chooser">
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
      <p>{providerClaimErrorMessage(err, fallback)}</p>
    </div>
  );
}

function ComingSoonNote({ err }: { err: unknown }) {
  return (
    <div
      className="flex items-start gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm text-muted-foreground"
      data-testid="provider-claim-coming-soon"
    >
      <Clock className="mt-0.5 h-4 w-4 shrink-0" />
      <p>
        {providerClaimErrorMessage(
          err,
          "This route is not available yet. It will appear here once the supporting lane is live — no shortcut is faked in the meantime.",
        )}
      </p>
    </div>
  );
}

const inputClass =
  "w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground";
const primaryButtonClass =
  "inline-flex items-center gap-2 rounded-lg bg-primary-hover px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700 disabled:cursor-not-allowed disabled:opacity-50";

// ── Claim token flow ────────────────────────────────────────────────────────

function ClaimTokenFlow() {
  const [token, setToken] = useState("");
  const [consent, setConsent] = useState(false);
  const preview = usePreviewClaimToken();
  const claim = useClaimProviderProfile();

  if (claim.data) {
    return (
      <div
        className="rounded-lg border border-success/25 bg-success-soft px-4 py-3 text-sm"
        data-testid="provider-claim-confirmation"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <ShieldCheck className="h-4 w-4 text-primary" /> Provider profile linked
        </p>
        <p className="mt-1 text-muted-foreground">
          Provider ID <span className="font-mono">{claim.data.providerPublicId}</span> is now
          linked to your Health ID. Activate the role from{" "}
          <Link href="/provider/activate" className="font-medium text-primary underline">
            provider activation
          </Link>{" "}
          before any regulated work.
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-3"
      data-testid="provider-claim-token-flow"
      onSubmit={(e) => {
        e.preventDefault();
        if (!preview.data) preview.mutate(token.trim());
        else if (consent) claim.mutate({ claimToken: token.trim(), consent: true });
      }}
    >
      <label className="block text-sm font-medium text-foreground" htmlFor="claim-token-input">
        Claim token
      </label>
      <input
        id="claim-token-input"
        type="password"
        autoComplete="off"
        value={token}
        onChange={(e) => setToken(e.target.value)}
        placeholder="Paste your single-use claim token"
        className={inputClass}
      />

      {preview.isError ? (
        <ErrorNote err={preview.error} fallback="That claim token was not recognised." />
      ) : null}
      {claim.isError ? (
        <ErrorNote
          err={claim.error}
          fallback="The claim could not be completed. The token may already have been used."
        />
      ) : null}

      {preview.data ? (
        <div
          className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm"
          data-testid="provider-claim-preview"
        >
          <p className="font-medium text-foreground">This token claims:</p>
          <p className="mt-1 text-muted-foreground">
            <span className="font-mono">{preview.data.providerPublicId}</span> —{" "}
            {preview.data.givenNameInitial} {preview.data.familyName}
            {preview.data.profession ? `, ${preview.data.profession}` : ""} (
            {preview.data.lifecycleStatus})
          </p>
          <label className="mt-2 flex items-start gap-2 text-xs text-foreground">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
              className="mt-0.5"
            />
            I confirm this preloaded profile is mine and consent to linking it to my Health ID.
            The token is single-use and will be burned.
          </label>
        </div>
      ) : null}

      <button
        type="submit"
        className={primaryButtonClass}
        disabled={
          !token.trim() || preview.isPending || claim.isPending || (!!preview.data && !consent)
        }
      >
        {(preview.isPending || claim.isPending) && (
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
        )}
        {preview.data ? "Claim this profile" : "Preview what this token claims"}
      </button>
    </form>
  );
}

// ── Council number flow ─────────────────────────────────────────────────────

function CouncilNumberFlow() {
  const [value, setValue] = useState("");
  const [councilCode, setCouncilCode] = useState("");
  const evidence = useSubmitClaimEvidence();

  if (evidence.data) {
    return (
      <div
        className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm"
        data-testid="provider-claim-evidence-recorded"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <Clock className="h-4 w-4 text-primary" /> Evidence recorded — verification pending
        </p>
        <p className="mt-1 text-muted-foreground">
          Registration <span className="font-mono">{evidence.data.evidenceRef}</span> was recorded
          against profile <span className="font-mono">{evidence.data.providerPublicId}</span>.{" "}
          {evidence.data.nextStep}
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-3"
      data-testid="provider-claim-council-flow"
      onSubmit={(e) => {
        e.preventDefault();
        evidence.mutate({
          type: "COUNCIL_NUMBER",
          value: value.trim(),
          councilCode: councilCode.trim() || undefined,
        });
      }}
    >
      <label className="block text-sm font-medium text-foreground" htmlFor="council-number-input">
        Council registration number
      </label>
      <input
        id="council-number-input"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="e.g. EC-1001"
        className={inputClass}
      />
      <label className="block text-sm font-medium text-foreground" htmlFor="council-code-input">
        Council code (optional)
      </label>
      <input
        id="council-code-input"
        value={councilCode}
        onChange={(e) => setCouncilCode(e.target.value)}
        placeholder="e.g. MDPCZ"
        className={inputClass}
      />
      {evidence.isError ? (
        <ErrorNote
          err={evidence.error}
          fallback="That registration could not be matched. Nothing was recorded."
        />
      ) : null}
      <button
        type="submit"
        className={primaryButtonClass}
        disabled={!value.trim() || evidence.isPending}
      >
        {evidence.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
        Submit council number
      </button>
      <p className="text-xs text-muted-foreground">
        Only a masked reference travels to the trust ledger. Verification is reviewed against the
        council record — no provider role activates until it completes.
      </p>
    </form>
  );
}

// ── EC number flow (Channel B — live workforce employment match) ────────────
//
// Re-pointed (E2-TRUST) from the report-only /provider-claim/evidence lane
// (501-gated) to the real /api/v1/trust/employment-match endpoint. That endpoint
// returns a RAW map (no { data, meta } envelope): the mutation consumes it as-is.
// A confirmed match with a linked provider asserts EMPLOYMENT_MATCHED trust; a
// match with no provider surfaces honest providerCreationRequired guidance.

function EcNumberFlow() {
  const [value, setValue] = useState("");
  const match = useEmploymentMatch();
  const result = match.data;

  if (result?.trustAssertion?.status === "ASSERTED") {
    return (
      <div
        className="rounded-lg border border-success/25 bg-success-soft px-4 py-3 text-sm"
        data-testid="provider-claim-ec-asserted"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <ShieldCheck className="h-4 w-4 text-primary" /> Employment matched — EMPLOYMENT_MATCHED
          asserted
        </p>
        <p className="mt-1 text-muted-foreground">
          EC number <span className="font-mono">{result.maskedEcNumber}</span> matched the workforce
          registry
          {result.trustAssertion.providerPublicId ? (
            <>
              {" "}
              and EMPLOYMENT_MATCHED trust was asserted on provider{" "}
              <span className="font-mono">{result.trustAssertion.providerPublicId}</span>
            </>
          ) : null}
          . Activate the role from{" "}
          <Link href="/provider/activate" className="font-medium text-primary underline">
            provider activation
          </Link>{" "}
          before any regulated work.
        </p>
      </div>
    );
  }

  if (result?.providerCreationRequired) {
    return (
      <div
        className="rounded-lg border border-border bg-background px-4 py-3 text-sm text-muted-foreground"
        data-testid="provider-claim-ec-provider-required"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <Clock className="h-4 w-4 text-primary" /> Employment matched — no provider profile yet
        </p>
        <p className="mt-1">
          EC number <span className="font-mono">{result.maskedEcNumber}</span> matched the workforce
          registry ({result.matchStatus}), but no provider profile is linked to this Health ID yet.
          Provider profiles are not created here — nothing was fabricated. Claim or create one from
          the{" "}
          <Link href="/citizen/provider-claim" className="font-medium text-primary underline">
            provider claim
          </Link>{" "}
          journey.
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-3"
      data-testid="provider-claim-ec-flow"
      onSubmit={(e) => {
        e.preventDefault();
        match.mutate({ ecNumber: value.trim() });
      }}
    >
      <label className="block text-sm font-medium text-foreground" htmlFor="ec-number-input">
        EC (employment) number
      </label>
      <input
        id="ec-number-input"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="e.g. EC-77"
        className={inputClass}
      />
      {match.isError ? (
        <ErrorNote
          err={match.error}
          fallback={employmentMatchErrorMessage(match.error, "Employment matching failed.")}
        />
      ) : null}
      {result && result.status === "UNAVAILABLE" ? (
        <div
          className="flex items-start gap-2 rounded-lg border border-border bg-background px-3 py-2 text-sm text-muted-foreground"
          data-testid="provider-claim-ec-unavailable"
        >
          <Clock className="mt-0.5 h-4 w-4 shrink-0" />
          <p>The workforce registry is unreachable right now — nothing was matched or fabricated.</p>
        </div>
      ) : null}
      {result && result.status === "OK" && !result.trustAssertion && !result.providerCreationRequired ? (
        <div
          className="rounded-lg border border-primary/25 bg-primary-soft px-4 py-3 text-sm text-muted-foreground"
          data-testid="provider-claim-ec-result"
        >
          Employment match result:{" "}
          <span className="font-medium text-foreground">{result.matchStatus}</span>
        </div>
      ) : null}
      <button
        type="submit"
        className={primaryButtonClass}
        disabled={!value.trim() || match.isPending}
      >
        {match.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
        Match employment record
      </button>
    </form>
  );
}

// ── Recovery flow (recover-not-reissue) ─────────────────────────────────────

function RecoverFlow() {
  const [evidenceType, setEvidenceType] = useState("COUNCIL_NUMBER");
  const [value, setValue] = useState("");
  const recover = useRecoverProviderProfile();

  if (recover.data) {
    return (
      <div
        className="rounded-lg border border-success/25 bg-success-soft px-4 py-3 text-sm"
        data-testid="provider-claim-recovered"
      >
        <p className="flex items-center gap-2 font-medium text-foreground">
          <ShieldCheck className="h-4 w-4 text-primary" /> Profile recovered — same Provider ID
        </p>
        <p className="mt-1 text-muted-foreground">
          <span className="font-mono">{recover.data.providerPublicId}</span> was re-linked to this
          login. {recover.data.note}
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-3"
      data-testid="provider-claim-recover-flow"
      onSubmit={(e) => {
        e.preventDefault();
        recover.mutate({ evidence: { type: evidenceType, value: value.trim() } });
      }}
    >
      <p className="text-sm text-muted-foreground">
        Recovery re-links the provider profile you already own to this login. Your Provider ID is
        never re-issued — the same identifier is preserved.
      </p>
      <label className="block text-sm font-medium text-foreground" htmlFor="recover-evidence-type">
        Supporting evidence
      </label>
      <select
        id="recover-evidence-type"
        value={evidenceType}
        onChange={(e) => setEvidenceType(e.target.value)}
        className={inputClass}
      >
        <option value="COUNCIL_NUMBER">Council registration number</option>
        <option value="EC_NUMBER">EC (employment) number</option>
      </select>
      <input
        aria-label="Evidence value"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Evidence reference"
        className={inputClass}
      />
      {recover.isError ? (
        <ErrorNote
          err={recover.error}
          fallback="Recovery could not be completed. No provider identity was changed."
        />
      ) : null}
      <button
        type="submit"
        className={primaryButtonClass}
        disabled={!value.trim() || recover.isPending}
      >
        {recover.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
        Recover my profile
      </button>
    </form>
  );
}

// ── Access-request confirmation (durable; IATG Journey D) ─────────────────────

function AccessRequestSubmitted({ view }: { view: ProviderAccessRequestView }) {
  return (
    <div
      className="space-y-2 rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-3 text-sm dark:border-emerald-800 dark:bg-emerald-950/30"
      data-testid="provider-claim-access-request-submitted"
    >
      <div className="flex items-center gap-2 font-medium text-foreground">
        <CheckCircle2 className="h-4 w-4 text-emerald-600" /> Request recorded
      </div>
      <dl className="grid grid-cols-[auto,1fr] gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <dt>Reference</dt>
        <dd className="font-mono text-foreground">{view.publicId}</dd>
        <dt>Status</dt>
        <dd className="text-foreground">{view.status}</dd>
        {view.nextActor ? (
          <>
            <dt>Next actor</dt>
            <dd className="text-foreground">{view.nextActor}</dd>
          </>
        ) : null}
      </dl>
      {view.reason ? <p className="text-xs text-muted-foreground">{view.reason}</p> : null}
      <Link
        href="/citizen/provider-claim/status"
        className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:text-primary-hover"
      >
        <ListChecks className="h-3.5 w-3.5" /> Track this request
      </Link>
    </div>
  );
}

// ── New provider flow (durable request; no auto-issue) ────────────────────────

function NewProviderFlow() {
  const [profession, setProfession] = useState("");
  const [councilCode, setCouncilCode] = useState("");
  const [councilNumber, setCouncilNumber] = useState("");
  const [ecNumber, setEcNumber] = useState("");
  const submit = useSubmitProviderAccessRequest();

  if (submit.data) {
    return <AccessRequestSubmitted view={submit.data} />;
  }

  return (
    <form
      className="space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        submit.mutate({
          requestType: "NEW_PROVIDER",
          profession: profession.trim() || undefined,
          councilCode: councilCode.trim() || undefined,
          councilNumber: councilNumber.trim() || undefined,
          ecNumber: ecNumber.trim() || undefined,
        });
      }}
    >
      <p className="text-sm text-muted-foreground">
        Submit your details for review. A Provider ID is never issued automatically — your request
        is routed to the right reviewer and you can track its status.
      </p>
      <input className={inputClass} placeholder="Profession / cadre (e.g. Medical Officer)"
        value={profession} onChange={(e) => setProfession(e.target.value)} aria-label="Profession" />
      <div className="grid gap-2 sm:grid-cols-2">
        <input className={inputClass} placeholder="Council code (optional)"
          value={councilCode} onChange={(e) => setCouncilCode(e.target.value)} aria-label="Council code" />
        <input className={inputClass} placeholder="Council registration no. (optional)"
          value={councilNumber} onChange={(e) => setCouncilNumber(e.target.value)} aria-label="Council registration number" />
      </div>
      <input className={inputClass} placeholder="EC number if public-sector (optional)"
        value={ecNumber} onChange={(e) => setEcNumber(e.target.value)} aria-label="EC number" />
      {submit.isError ? <ErrorNote err={submit.error} fallback="Your request could not be submitted right now. Nothing was changed — retry." /> : null}
      <button type="submit" className={primaryButtonClass} disabled={submit.isPending}>
        {submit.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <UserPlus className="h-3.5 w-3.5" />}
        Submit request
      </button>
    </form>
  );
}

// ── Organisation-invitation flow (durable request) ────────────────────────────

function OrgInvitationFlow() {
  const [organizationRef, setOrganizationRef] = useState("");
  const [evidenceSummary, setEvidenceSummary] = useState("");
  const submit = useSubmitProviderAccessRequest();

  if (submit.data) {
    return <AccessRequestSubmitted view={submit.data} />;
  }

  return (
    <form
      className="space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        submit.mutate({
          requestType: "ORG_INVITATION",
          organizationRef: organizationRef.trim() || undefined,
          evidenceSummary: evidenceSummary.trim() || undefined,
        });
      }}
    >
      <p className="text-sm text-muted-foreground">
        Recorded and routed to the inviting organisation to confirm your access. Nothing activates
        until the organisation confirms.
      </p>
      <input className={inputClass} placeholder="Organisation name or reference"
        value={organizationRef} onChange={(e) => setOrganizationRef(e.target.value)} aria-label="Organisation reference" />
      <input className={inputClass} placeholder="Anything to help them find you (optional)"
        value={evidenceSummary} onChange={(e) => setEvidenceSummary(e.target.value)} aria-label="Evidence summary" />
      {submit.isError ? <ErrorNote err={submit.error} fallback="Your request could not be submitted right now. Nothing was changed — retry." /> : null}
      <button type="submit" className={primaryButtonClass} disabled={submit.isPending}>
        {submit.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Building2 className="h-3.5 w-3.5" />}
        Submit request
      </button>
    </form>
  );
}
