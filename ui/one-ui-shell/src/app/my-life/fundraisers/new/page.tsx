"use client";

/**
 * Start a fundraiser — create wizard. Creating produces a private DRAFT case; the page
 * then explains honestly that the fundraiser only goes public once the treating facility
 * verifies it, and offers the request-verification step (consentRef comes from the care
 * team — there is no auto-consent).
 * Route: /my-life/fundraisers/new
 */

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, CheckCircle2, Loader2, ShieldCheck } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { errMessage, statusMeta, unwrap, type FundraiserCase } from "../fundraisers";

const CATEGORIES = [
  "MEDICAL_COSTS",
  "SURGERY",
  "MEDICATION",
  "REHABILITATION",
  "TRANSPORT",
  "OTHER",
] as const;

const CURRENCIES = ["USD", "ZWG"] as const;

export default function NewFundraiserPage() {
  const [title, setTitle] = useState("");
  const [story, setStory] = useState("");
  const [category, setCategory] = useState<string>("MEDICAL_COSTS");
  const [targetAmount, setTargetAmount] = useState("");
  const [currency, setCurrency] = useState<string>("USD");
  const [endsAt, setEndsAt] = useState("");
  const [anonymous, setAnonymous] = useState(true);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<FundraiserCase | null>(null);

  const [consentRef, setConsentRef] = useState("");
  const [requestingVerification, setRequestingVerification] = useState(false);
  const [verificationError, setVerificationError] = useState<string | null>(null);
  const [verificationRequested, setVerificationRequested] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const body: Record<string, unknown> = {
        title,
        story,
        category,
        targetAmount,
        currency,
        subjectIdentityMode: anonymous ? "ANONYMOUS" : "KNOWN",
      };
      if (endsAt) body.endsAt = `${endsAt}T23:59:59Z`;
      const res = await apiClient.post<unknown>("/internal/v1/citizen/fundraisers", body);
      setCreated(unwrap<FundraiserCase>(res));
    } catch (e2) {
      setError(errMessage(e2, "Could not create your fundraiser."));
    } finally {
      setSubmitting(false);
    }
  };

  const requestVerification = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!created?.id) return;
    setRequestingVerification(true);
    setVerificationError(null);
    try {
      await apiClient.post<unknown>(
        `/internal/v1/citizen/fundraisers/${encodeURIComponent(created.id)}/request-verification`,
        { consentRef: consentRef.trim() },
      );
      setVerificationRequested(true);
    } catch (e2) {
      setVerificationError(errMessage(e2, "Could not request verification."));
    } finally {
      setRequestingVerification(false);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Start a fundraiser"
        subtitle="Raise help for care costs — verified by your treating facility before it goes public"
        serviceSlug="daidzai"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <div className="mb-4">
            <Link
              href="/my-life/fundraisers"
              className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Back to fundraisers
            </Link>
          </div>

          {created ? (
            <div className="max-w-xl space-y-4" data-testid="draft-explainer">
              <div className="rounded-2xl border border-teal-300 bg-teal-50 p-6">
                <div className="mb-2 flex items-center gap-2 font-semibold text-teal-900">
                  <CheckCircle2 className="h-5 w-5" /> Your fundraiser was created
                </div>
                <p className="text-sm text-teal-800">
                  Reference{" "}
                  <span className="font-mono font-semibold">
                    {created.assistanceReference ?? created.id}
                  </span>
                  . It is currently a{" "}
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusMeta(created.verificationStatus ?? "DRAFT").className}`}
                  >
                    {statusMeta(created.verificationStatus ?? "DRAFT").label}
                  </span>{" "}
                  — only you can see it. To go public it must be verified by your treating
                  facility.
                </p>
              </div>

              {verificationRequested ? (
                <div className="rounded-xl border border-amber-300 bg-amber-50 p-5 text-sm text-amber-900">
                  <div className="mb-1 flex items-center gap-2 font-semibold">
                    <ShieldCheck className="h-4 w-4" /> Verification requested
                  </div>
                  Your treating facility will review and attest your fundraiser. It goes public
                  only after their attestation.
                </div>
              ) : (
                <GlassSurface className="p-5">
                  <h3 className="mb-1 flex items-center gap-2 text-sm font-semibold text-foreground">
                    <ShieldCheck className="h-4 w-4 text-teal-600" /> Request verification
                  </h3>
                  <p className="mb-3 text-sm text-muted-foreground">
                    Verification needs a consent reference from your care team — the reference
                    you were given when you consented to your facility confirming your treatment
                    for this fundraiser. If you don&apos;t have one yet, ask your treating
                    facility; we can&apos;t create it for you.
                  </p>
                  {verificationError && (
                    <div className="mb-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                      {verificationError}
                    </div>
                  )}
                  <form onSubmit={requestVerification} className="flex flex-wrap gap-2">
                    <input
                      value={consentRef}
                      onChange={(e) => setConsentRef(e.target.value)}
                      placeholder="Consent reference from your care team"
                      data-testid="consent-ref-input"
                      className="flex-1 rounded-lg border border-border px-3 py-2 text-sm"
                    />
                    <button
                      type="submit"
                      disabled={!consentRef.trim() || requestingVerification}
                      data-testid="request-verification-submit"
                      className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                    >
                      {requestingVerification && <Loader2 className="h-4 w-4 animate-spin" />}
                      Request verification
                    </button>
                  </form>
                </GlassSurface>
              )}

              {created.id && (
                <Link
                  href={`/my-life/fundraisers/${encodeURIComponent(created.id)}`}
                  className="inline-flex rounded-lg border border-teal-300 bg-white px-4 py-2 text-sm font-medium text-teal-700 hover:bg-teal-50"
                >
                  View my fundraiser
                </Link>
              )}
            </div>
          ) : (
            <form onSubmit={submit} className="max-w-xl space-y-4" data-testid="create-form">
              {error && (
                <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                  {error}
                </div>
              )}
              <div>
                <label className="mb-1 block text-sm font-medium text-foreground">Title</label>
                <input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  required
                  data-testid="title-input"
                  className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-foreground">
                  Your story
                </label>
                <textarea
                  value={story}
                  onChange={(e) => setStory(e.target.value)}
                  required
                  rows={5}
                  data-testid="story-input"
                  className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-sm font-medium text-foreground">
                    Target amount
                  </label>
                  <input
                    value={targetAmount}
                    onChange={(e) => setTargetAmount(e.target.value)}
                    required
                    type="number"
                    min="1"
                    step="0.01"
                    inputMode="decimal"
                    data-testid="target-input"
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-sm font-medium text-foreground">Currency</label>
                  <select
                    value={currency}
                    onChange={(e) => setCurrency(e.target.value)}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  >
                    {CURRENCIES.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="mb-1 block text-sm font-medium text-foreground">Category</label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  >
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {c.replace(/_/g, " ")}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-sm font-medium text-foreground">
                    Ends on (optional)
                  </label>
                  <input
                    type="date"
                    value={endsAt}
                    onChange={(e) => setEndsAt(e.target.value)}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  />
                </div>
              </div>
              <label className="flex items-start gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={anonymous}
                  onChange={(e) => setAnonymous(e.target.checked)}
                  className="mt-0.5"
                  data-testid="anonymous-toggle"
                />
                <span>
                  Present me anonymously to donors
                  <span className="block text-xs text-muted-foreground">
                    Donors see your story and the facility&apos;s verification — not your
                    identity. Your treating facility always knows who you are.
                  </span>
                </span>
              </label>
              <button
                type="submit"
                disabled={submitting || !title || !story || !targetAmount}
                data-testid="create-submit"
                className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
              >
                {submitting && <Loader2 className="h-4 w-4 animate-spin" />}
                {submitting ? "Creating…" : "Create fundraiser"}
              </button>
            </form>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
