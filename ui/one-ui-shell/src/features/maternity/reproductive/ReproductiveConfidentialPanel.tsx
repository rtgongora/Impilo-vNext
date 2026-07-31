"use client";

/**
 * Clinician confidential SRH read panel — contraception, loss history, TOP authorisations and
 * procedures for a patient CPID on the EHR maternity route.
 *
 * Backend: `/internal/v1/confidential/reproductive/**` via useConfidentialReproductive hooks.
 * Empty lists are withhold semantics, not proof of absence; 502 is an outage, never an empty chart.
 */

import { AlertTriangle, Loader2, Shield } from "lucide-react";
import {
  isReproductiveReadUnavailable,
  useContraceptionCoverage,
  useContraceptionHistory,
  usePregnancyLosses,
  useTerminations,
  useTopAuthorisations,
  type ContraceptionCoverageView,
  type ContraceptiveEpisodeView,
  type PregnancyLossView,
  type TerminationProcedureView,
  type TopAuthorisationView,
} from "@/hooks/queries/useConfidentialReproductive";
import {
  REPRODUCTIVE_EMPTY_HINT,
  REPRODUCTIVE_UNAVAILABLE_BODY,
  REPRODUCTIVE_UNAVAILABLE_TITLE,
} from "./reproductiveWording";

export interface ReproductiveConfidentialPanelProps {
  patientCpid: string;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return "Not recorded";
  try {
    return new Date(iso).toLocaleDateString();
  } catch {
    return iso;
  }
}

function humanise(code: string | null | undefined): string {
  if (!code) return "Not recorded";
  return code.replace(/_/g, " ").toLowerCase();
}

function UnavailableBanner({ testId }: { testId: string }) {
  return (
    <div
      className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-sm text-red-900"
      data-testid={testId}
      role="alert"
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
      <div>
        <p className="font-semibold">{REPRODUCTIVE_UNAVAILABLE_TITLE}</p>
        <p className="mt-1 text-xs">{REPRODUCTIVE_UNAVAILABLE_BODY}</p>
      </div>
    </div>
  );
}

function EmptyHint() {
  return <p className="text-xs text-muted-foreground">{REPRODUCTIVE_EMPTY_HINT}</p>;
}

function LoadingRow({ label }: { label: string }) {
  return (
    <p className="flex items-center gap-2 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" /> Loading {label}…
    </p>
  );
}

function CoverageCard({ item }: { item: ContraceptionCoverageView }) {
  return (
    <li
      className="rounded-md border border-border bg-muted/30 px-3 py-2 text-sm"
      data-testid="contraception-coverage-item"
    >
      <p className="font-medium text-foreground">
        {humanise(item.method_code ?? item.method_class)}
        {item.status ? ` · ${humanise(item.status)}` : ""}
      </p>
      <p className="mt-1 text-xs text-muted-foreground">
        Coverage: {humanise(item.coverage_status)}
        {item.days_remaining != null ? ` · ${item.days_remaining} day(s) remaining` : ""}
      </p>
      {item.next_due_on ? (
        <p className="text-xs text-muted-foreground">Next due: {formatDate(item.next_due_on)}</p>
      ) : null}
      {item.coverage_unknown_why ? (
        <p className="mt-1 text-xs text-amber-800">{item.coverage_unknown_why}</p>
      ) : null}
    </li>
  );
}

function HistoryRow({ item }: { item: ContraceptiveEpisodeView }) {
  return (
    <li className="flex flex-wrap justify-between gap-2 border-b border-border py-2 text-sm last:border-0">
      <span>{humanise(item.method_code ?? item.method_class)}</span>
      <span className="text-xs text-muted-foreground">
        {formatDate(item.started_on)}
        {item.ended_on ? ` – ${formatDate(item.ended_on)}` : " – ongoing"}
      </span>
    </li>
  );
}

function LossRow({ item }: { item: PregnancyLossView }) {
  return (
    <li className="flex flex-wrap justify-between gap-2 border-b border-border py-2 text-sm last:border-0">
      <span>{humanise(item.loss_type)}</span>
      <span className="text-xs text-muted-foreground">{formatDate(item.occurred_on)}</span>
    </li>
  );
}

function TopAuthRow({ item }: { item: TopAuthorisationView }) {
  return (
    <li className="rounded-md border border-border bg-muted/20 px-3 py-2 text-sm">
      <p className="font-medium">{humanise(item.status)}</p>
      <p className="text-xs text-muted-foreground">
        {item.legal_ground ? humanise(item.legal_ground) : "Ground not recorded"}
        {item.gestation_weeks_at_request != null
          ? ` · ${item.gestation_weeks_at_request} wks at request`
          : ""}
      </p>
      {item.consent_given === false ? (
        <p className="mt-1 text-xs text-amber-800">
          Consent not recorded — authorisation without consent may indicate a safeguarding concern.
        </p>
      ) : null}
    </li>
  );
}

function TerminationRow({ item }: { item: TerminationProcedureView }) {
  return (
    <li className="flex flex-wrap justify-between gap-2 border-b border-border py-2 text-sm last:border-0">
      <span>{humanise(item.method)}</span>
      <span className="text-xs text-muted-foreground">{formatDate(item.performed_on)}</span>
    </li>
  );
}

export function ContraceptionCoveragePanel({ patientCpid }: { patientCpid: string }) {
  const coverage = useContraceptionCoverage(patientCpid);
  const history = useContraceptionHistory(patientCpid);

  if (coverage.isLoading || history.isLoading) {
    return <LoadingRow label="contraception" />;
  }

  if (coverage.isError || history.isError) {
    const err = coverage.error ?? history.error;
    if (isReproductiveReadUnavailable(err)) {
      return <UnavailableBanner testId="contraception-unavailable" />;
    }
    return (
      <p className="text-sm text-muted-foreground">Could not load contraception records. Try again.</p>
    );
  }

  const current = coverage.data ?? [];
  const past = history.data ?? [];

  return (
    <div data-testid="contraception-panel">
      {current.length === 0 && past.length === 0 ? (
        <EmptyHint />
      ) : (
        <>
          {current.length > 0 ? (
            <ul className="mt-2 space-y-2">
              {current.map((c) => (
                <CoverageCard key={c.contraceptive_episode_id} item={c} />
              ))}
            </ul>
          ) : null}
          {past.length > 0 ? (
            <div className="mt-3">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Method history
              </p>
              <ul className="mt-1">{past.map((h) => <HistoryRow key={h.contraceptive_episode_id} item={h} />)}</ul>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

export function LossHistoryPanel({ patientCpid }: { patientCpid: string }) {
  const losses = usePregnancyLosses(patientCpid);

  if (losses.isLoading) return <LoadingRow label="pregnancy losses" />;
  if (losses.isError) {
    if (isReproductiveReadUnavailable(losses.error)) {
      return <UnavailableBanner testId="losses-unavailable" />;
    }
    return <p className="text-sm text-muted-foreground">Could not load loss history. Try again.</p>;
  }

  const rows = losses.data ?? [];
  return (
    <div data-testid="losses-panel">
      {rows.length === 0 ? (
        <EmptyHint />
      ) : (
        <ul>{rows.map((l) => <LossRow key={l.loss_record_id} item={l} />)}</ul>
      )}
    </div>
  );
}

export function TopAuthorisationsPanel({ patientCpid }: { patientCpid: string }) {
  const auths = useTopAuthorisations(patientCpid);
  const procedures = useTerminations(patientCpid);

  if (auths.isLoading || procedures.isLoading) {
    return <LoadingRow label="termination records" />;
  }

  if (auths.isError || procedures.isError) {
    const err = auths.error ?? procedures.error;
    if (isReproductiveReadUnavailable(err)) {
      return <UnavailableBanner testId="top-unavailable" />;
    }
    return (
      <p className="text-sm text-muted-foreground">Could not load termination records. Try again.</p>
    );
  }

  const authRows = auths.data ?? [];
  const procRows = procedures.data ?? [];

  return (
    <div data-testid="top-authorisations-panel">
      {authRows.length === 0 && procRows.length === 0 ? (
        <EmptyHint />
      ) : (
        <>
          {authRows.length > 0 ? (
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Authorisations
              </p>
              <ul className="mt-2 space-y-2">
                {authRows.map((a) => (
                  <TopAuthRow key={a.authorisation_id} item={a} />
                ))}
              </ul>
            </div>
          ) : null}
          {procRows.length > 0 ? (
            <div className="mt-3">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Procedures performed
              </p>
              <ul className="mt-1">
                {procRows.map((p) => (
                  <TerminationRow key={p.procedure_id} item={p} />
                ))}
              </ul>
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

/** Combined clinician panel for the EHR maternity route. */
export function ReproductiveConfidentialPanel({ patientCpid }: ReproductiveConfidentialPanelProps) {
  return (
    <section
      className="mb-4 rounded-lg border border-violet-200/90 bg-card p-5"
      data-testid="reproductive-confidential-panel"
    >
      <div className="flex items-center gap-2">
        <Shield className="h-5 w-5 text-violet-700" />
        <h2 className="text-lg font-semibold text-foreground">Confidential reproductive health</h2>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">
        Contraception, pregnancy losses and termination records on the confidential lane. An empty
        section is not proof that nothing exists — it may be withheld.
      </p>

      <div className="mt-4 space-y-5">
        <div>
          <h3 className="text-sm font-medium text-foreground">Contraception</h3>
          <ContraceptionCoveragePanel patientCpid={patientCpid} />
        </div>
        <div>
          <h3 className="text-sm font-medium text-foreground">Pregnancy losses</h3>
          <LossHistoryPanel patientCpid={patientCpid} />
        </div>
        <div>
          <h3 className="text-sm font-medium text-foreground">Termination authorisations & procedures</h3>
          <TopAuthorisationsPanel patientCpid={patientCpid} />
        </div>
      </div>
    </section>
  );
}
