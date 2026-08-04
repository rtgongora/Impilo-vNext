"use client";

/**
 * Find-care access-to-care actions — the real next steps after a service-aware search.
 *
 * Renders only actions that actually work. The find-care journey is anonymous, so a resource-moving
 * action (book, request transport, start virtual care, referral status) either:
 *  - for a signed-in citizen, calls the CITIZEN-gated access-to-care lane and shows the sovereign
 *    source's honest reference + pending status (never a fabricated confirmation), or
 *  - for a guest, routes to sign-in with a `returnTo` that lands back on this facility/service
 *    selection (preserved in the find-care journey store) — the honest next step, not a fake success.
 *
 * "Start virtual care" appears ONLY when the last search confirmed a live (ACTIVE) virtual service;
 * otherwise it is omitted entirely.
 */

import { useState } from "react";
import Link from "next/link";
import { CalendarPlus, Video, Ambulance, ClipboardList, Loader2, CheckCircle2, LogIn } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFindCareJourneyStore } from "@/hooks/useFindCareJourneyStore";
import { SERVICE_CHIPS } from "@/lib/find-care/service-chips";
import {
  requestAppointment,
  requestTransport,
  type AccessToCareReceipt,
} from "@/lib/find-care/access-to-care";

interface FindCareAccessActionsProps {
  facilityId: number;
  facilityName?: string | null;
  serviceToken?: string | null;
  serviceLabel?: string | null;
  /** From the last search — only true when a real ACTIVE virtual service was found. */
  virtualCareAvailable?: boolean;
  variant?: "compact" | "full";
}

function friendlyError(err: unknown): string {
  const code = (err as { error?: { code?: string }; status?: number })?.error?.code;
  const status = (err as { status?: number })?.status;
  if (code === "RATE_LIMITED" || status === 429) {
    return "You've made several requests just now. Please wait a moment and try again.";
  }
  if (code === "VALIDATION" || status === 400) {
    return "Please check the details and try again.";
  }
  return "We couldn't complete that just now. Please try again shortly.";
}

export function FindCareAccessActions({
  facilityId,
  facilityName,
  serviceToken,
  serviceLabel,
  virtualCareAvailable = false,
  variant = "full",
}: FindCareAccessActionsProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const setReturnTo = useFindCareJourneyStore((s) => s.setReturnTo);
  const setSelectedFacility = useFindCareJourneyStore((s) => s.setSelectedFacility);

  const detailPath = `/welcome/find-care/${facilityId}`;

  // Remember where the person was so sign-in returns them to the exact selection.
  function rememberSelection() {
    setSelectedFacility(facilityId);
    setReturnTo(detailPath);
  }
  function signInHref(returnTo: string): string {
    return `/auth/login?returnTo=${encodeURIComponent(returnTo)}`;
  }

  if (variant === "compact") {
    return (
      <div className="flex flex-wrap gap-2">
        {isAuthenticated ? (
          <Link
            href={detailPath}
            onClick={() => setSelectedFacility(facilityId)}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            <CalendarPlus className="h-4 w-4" aria-hidden />
            Request appointment
          </Link>
        ) : (
          <Link
            href={signInHref(detailPath)}
            onClick={rememberSelection}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50"
          >
            <LogIn className="h-4 w-4" aria-hidden />
            Sign in to book
          </Link>
        )}
        {virtualCareAvailable && (
          <Link
            href={isAuthenticated ? "/citizen/virtual-care" : signInHref("/citizen/virtual-care")}
            onClick={isAuthenticated ? undefined : rememberSelection}
            className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-300 bg-emerald-50 px-3.5 py-2 text-sm font-semibold text-emerald-800 hover:bg-emerald-100"
          >
            <Video className="h-4 w-4" aria-hidden />
            Start virtual care
          </Link>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <AppointmentAction
        facilityId={facilityId}
        serviceToken={serviceToken}
        serviceLabel={serviceLabel}
        isAuthenticated={isAuthenticated}
        signInHref={signInHref}
        onBeforeSignIn={rememberSelection}
      />

      {virtualCareAvailable && (
        <section className="rounded-2xl border border-emerald-200 bg-emerald-50 p-6">
          <h2 className="flex items-center gap-2 font-semibold text-emerald-900">
            <Video className="h-4 w-4" aria-hidden />
            Virtual care
          </h2>
          <p className="mt-2 text-sm text-emerald-800">
            A live virtual service is available. You can start a remote consultation after signing in.
          </p>
          <Link
            href={isAuthenticated ? "/citizen/virtual-care" : signInHref("/citizen/virtual-care")}
            onClick={isAuthenticated ? undefined : rememberSelection}
            className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
          >
            {isAuthenticated ? "Start virtual care" : "Sign in to start virtual care"}
          </Link>
        </section>
      )}

      <TransportAction
        facilityId={facilityId}
        facilityName={facilityName}
        isAuthenticated={isAuthenticated}
        signInHref={signInHref}
        onBeforeSignIn={rememberSelection}
      />

      <section className="rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-6">
        <h2 className="flex items-center gap-2 font-semibold text-slate-900">
          <ClipboardList className="h-4 w-4 text-slate-400" aria-hidden />
          Referral status
        </h2>
        <p className="mt-2 text-sm text-slate-600">
          If you were referred here, you can follow its progress — accepted, transport, and arrival —
          from your referrals.
        </p>
        <Link
          href={isAuthenticated ? "/home/referrals" : signInHref("/home/referrals")}
          onClick={isAuthenticated ? undefined : rememberSelection}
          className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
        >
          {isAuthenticated ? "View my referrals" : "Sign in to view referrals"}
        </Link>
      </section>
    </div>
  );
}

// ── Appointment ────────────────────────────────────────────────────────────

interface AppointmentActionProps {
  facilityId: number;
  serviceToken?: string | null;
  serviceLabel?: string | null;
  isAuthenticated: boolean;
  signInHref: (returnTo: string) => string;
  onBeforeSignIn: () => void;
}

function AppointmentAction({
  facilityId,
  serviceToken,
  serviceLabel,
  isAuthenticated,
  signInHref,
  onBeforeSignIn,
}: AppointmentActionProps) {
  const [service, setService] = useState(serviceToken ?? "");
  const [preferredDate, setPreferredDate] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [receipt, setReceipt] = useState<AccessToCareReceipt | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await requestAppointment({
        facilityId: String(facilityId),
        service: service.trim() || "GENERAL",
        preferredDate,
        reason: reason.trim() || undefined,
      });
      setReceipt(result);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-6">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900">
        <CalendarPlus className="h-4 w-4 text-slate-400" aria-hidden />
        Request an appointment
      </h2>

      {!isAuthenticated ? (
        <>
          <p className="mt-2 text-sm text-slate-600">
            Appointments are tied to your health record, so you&apos;ll sign in first. We&apos;ll bring
            you back to this facility{serviceLabel ? ` for ${serviceLabel}` : ""} to finish.
          </p>
          <Link
            href={signInHref(`/welcome/find-care/${facilityId}`)}
            onClick={onBeforeSignIn}
            className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700"
          >
            <LogIn className="h-4 w-4" aria-hidden />
            Sign in to request
          </Link>
        </>
      ) : receipt ? (
        <div className="mt-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
          <p className="flex items-center gap-2 font-medium text-emerald-900">
            <CheckCircle2 className="h-4 w-4" aria-hidden />
            Request sent{receipt.status ? ` · ${receipt.status}` : ""}
          </p>
          {receipt.reference && (
            <p className="mt-1 text-sm text-emerald-800">
              Reference: <span className="font-mono">{receipt.reference}</span>
            </p>
          )}
          <p className="mt-1 text-sm text-emerald-800">
            {receipt.message ??
              "Your request has been sent to the facility. Nothing is booked until the facility confirms."}
          </p>
        </div>
      ) : (
        <form className="mt-3 space-y-3" onSubmit={submit}>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label htmlFor="atc-service" className="text-xs font-medium text-slate-600">
                Service
              </label>
              <select
                id="atc-service"
                value={service}
                onChange={(e) => setService(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
              >
                <option value="">General / outpatient</option>
                {SERVICE_CHIPS.map((chip) => (
                  <option key={chip.token} value={chip.token}>
                    {chip.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label htmlFor="atc-date" className="text-xs font-medium text-slate-600">
                Preferred date
              </label>
              <input
                id="atc-date"
                type="date"
                required
                value={preferredDate}
                onChange={(e) => setPreferredDate(e.target.value)}
                className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
              />
            </div>
          </div>
          <div>
            <label htmlFor="atc-reason" className="text-xs font-medium text-slate-600">
              Reason (optional)
            </label>
            <input
              id="atc-reason"
              type="text"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. follow-up, new symptom"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
            />
          </div>
          {error && <p className="text-sm text-red-700">{error}</p>}
          <button
            type="submit"
            disabled={submitting || !preferredDate}
            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <CalendarPlus className="h-4 w-4" aria-hidden />}
            Send request
          </button>
          <p className="text-xs text-slate-500">
            This sends a request — a facility confirms it. No time slot is reserved until then.
          </p>
        </form>
      )}
    </section>
  );
}

// ── Transport ──────────────────────────────────────────────────────────────

interface TransportActionProps {
  facilityId: number;
  facilityName?: string | null;
  isAuthenticated: boolean;
  signInHref: (returnTo: string) => string;
  onBeforeSignIn: () => void;
}

function TransportAction({
  facilityId,
  facilityName,
  isAuthenticated,
  signInHref,
  onBeforeSignIn,
}: TransportActionProps) {
  const [pickupLabel, setPickupLabel] = useState("");
  const [notes, setNotes] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [receipt, setReceipt] = useState<AccessToCareReceipt | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await requestTransport({
        facilityId: String(facilityId),
        facilityLabel: facilityName ?? undefined,
        pickupLabel: pickupLabel.trim() || undefined,
        notes: notes.trim() || undefined,
      });
      setReceipt(result);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-6">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900">
        <Ambulance className="h-4 w-4 text-slate-400" aria-hidden />
        Request patient transport
      </h2>
      <p className="mt-1 text-xs text-slate-500">
        For planned, non-emergency transport. In an emergency, use{" "}
        <Link href="/welcome/emergency" className="font-medium text-red-700 underline">
          emergency help
        </Link>{" "}
        instead.
      </p>

      {!isAuthenticated ? (
        <Link
          href={signInHref(`/welcome/find-care/${facilityId}`)}
          onClick={onBeforeSignIn}
          className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
        >
          <LogIn className="h-4 w-4" aria-hidden />
          Sign in to request transport
        </Link>
      ) : receipt ? (
        <div className="mt-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4">
          <p className="flex items-center gap-2 font-medium text-emerald-900">
            <CheckCircle2 className="h-4 w-4" aria-hidden />
            Transport requested{receipt.status ? ` · ${receipt.status}` : ""}
          </p>
          {receipt.reference && (
            <p className="mt-1 text-sm text-emerald-800">
              Reference: <span className="font-mono">{receipt.reference}</span>
            </p>
          )}
          <p className="mt-1 text-sm text-emerald-800">
            {receipt.message ??
              "Your transport request has been received. No vehicle is on the way until it is assigned."}
          </p>
        </div>
      ) : (
        <form className="mt-3 space-y-3" onSubmit={submit}>
          <div>
            <label htmlFor="atc-pickup" className="text-xs font-medium text-slate-600">
              Where should you be collected? (optional)
            </label>
            <input
              id="atc-pickup"
              type="text"
              value={pickupLabel}
              onChange={(e) => setPickupLabel(e.target.value)}
              placeholder="e.g. home, ward, another clinic"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
            />
          </div>
          <div>
            <label htmlFor="atc-notes" className="text-xs font-medium text-slate-600">
              Anything the team should know? (optional)
            </label>
            <input
              id="atc-notes"
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g. needs a wheelchair"
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-emerald-500 focus:outline-none"
            />
          </div>
          {error && <p className="text-sm text-red-700">{error}</p>}
          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Ambulance className="h-4 w-4" aria-hidden />}
            Request transport
          </button>
        </form>
      )}
    </section>
  );
}
