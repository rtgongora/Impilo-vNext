"use client";

/**
 * Privacy & Data Settings — Consent management, data rights, and account deletion.
 * Route: /settings/privacy | pageTitle: "Privacy & Data"
 *
 * Per Privacy Policy §13 and §16: Users may manage consent,
 * request data export, and initiate account deletion.
 */

import { useState } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  Shield,
  FileText,
  CheckCircle,
  AlertTriangle,
  Loader2,
  ExternalLink,
  Trash2,
  Download,
  Clock,
  XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { DelegationManagementSection } from "@/components/citizen/DelegationManagementSection";
import { useConsentStore, CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";
import {
  usePolicyConsentStatus,
  usePolicyConsentHistory,
  useRevokePolicyConsent,
  useDeletionRequestStatus,
  useRequestAccountDeletion,
  useCancelAccountDeletion,
  type PolicyConsentResource,
  type PolicyConsentHistoryResource,
} from "@/hooks/queries/usePolicyConsent";

export default function PrivacySettingsPage() {
  const { revokeConsent: revokeClientConsent } = useConsentStore();
  const { data: consentData, isLoading: consentLoading } = usePolicyConsentStatus();
  const { data: historyData } = usePolicyConsentHistory();
  const { data: deletionData, isLoading: deletionLoading } = useDeletionRequestStatus();

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteReason, setDeleteReason] = useState("");
  const [deleteConfirmText, setDeleteConfirmText] = useState("");

  const consentRecords = Array.isArray(consentData?.data) ? consentData.data : [];
  const consentHistory = Array.isArray(historyData?.data) ? historyData.data : [];
  const privacyConsent = consentRecords.find(
    (c: PolicyConsentResource) => c.attributes.policyType === "PRIVACY_POLICY"
  );
  const termsConsent = consentRecords.find(
    (c: PolicyConsentResource) => c.attributes.policyType === "TERMS_OF_USE"
  );
  const deletionRequest = deletionData?.data ?? null;
  const hasPendingDeletion =
    deletionRequest?.attributes?.status === "PENDING" ||
    deletionRequest?.attributes?.status === "PROCESSING";

  const requestDeletion = useRequestAccountDeletion();
  const cancelDeletion = useCancelAccountDeletion();

  const revokeConsentMutation = useRevokePolicyConsent();

  // Wrap revoke to also clear client-side consent store
  const revokeConsent = {
    ...revokeConsentMutation,
    mutate: (policyType: string) => {
      revokeConsentMutation.mutate(policyType, {
        onSuccess: () => revokeClientConsent(),
      });
    },
  };

  const isLoading = consentLoading || deletionLoading;

  return (
    <AppLayout>
      <PageShell
        title="Privacy & Data"
        subtitle="Manage your consent, data rights, and account"
      >
        <div className="mb-4">
          <Link
            href="/settings"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to settings
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading privacy settings...</span>
          </div>
        ) : (
          <div className="space-y-6 max-w-2xl">
            {/* ── Consent Status ──────────────────────────── */}
            <section className="bg-card rounded-lg border border-border p-6">
              <h3 className="text-sm font-semibold text-foreground mb-4 flex items-center gap-2">
                <Shield className="w-4 h-4 text-primary" />
                Policy Consent
              </h3>

              <div className="space-y-4">
                <ConsentRow
                  label="Privacy Policy"
                  href="/privacy"
                  accepted={privacyConsent?.attributes?.accepted}
                  acceptedAt={privacyConsent?.attributes?.acceptedAt}
                  version={CURRENT_CONSENT_VERSION}
                  onRevoke={() => revokeConsent.mutate("PRIVACY_POLICY")}
                  revoking={revokeConsent.isPending}
                />

                <ConsentRow
                  label="Terms of Use"
                  href="/terms"
                  accepted={termsConsent?.attributes?.accepted}
                  acceptedAt={termsConsent?.attributes?.acceptedAt}
                  version={CURRENT_CONSENT_VERSION}
                  onRevoke={() => revokeConsent.mutate("TERMS_OF_USE")}
                  revoking={revokeConsent.isPending}
                />
              </div>

              <p className="mt-4 text-xs text-muted-foreground">
                Policy version: {CURRENT_CONSENT_VERSION}. Revoking consent will
                require you to re-accept before continuing to use Impilo.
              </p>
            </section>

            {/* ── Consent History (real audit trail from Mvumo) ── */}
            {consentHistory.length > 0 && (
              <section className="bg-card rounded-lg border border-border p-6">
                <h3 className="text-sm font-semibold text-foreground mb-4 flex items-center gap-2">
                  <Clock className="w-4 h-4 text-primary" />
                  Consent History
                </h3>
                <ul className="space-y-3">
                  {consentHistory.map((h: PolicyConsentHistoryResource) => {
                    const withdrawn = h.attributes.state === "WITHDRAWN" || Boolean(h.attributes.revokedAt);
                    const superseded = h.attributes.state === "SUPERSEDED";
                    const label = h.attributes.policyType === "PRIVACY_POLICY" ? "Privacy Policy" : "Terms of Use";
                    const when = h.attributes.revokedAt || h.attributes.acceptedAt;
                    return (
                      <li key={h.id} className="flex items-start gap-3 text-sm">
                        {withdrawn ? (
                          <XCircle className="w-4 h-4 mt-0.5 text-muted-foreground shrink-0" />
                        ) : (
                          <CheckCircle className="w-4 h-4 mt-0.5 text-green-600 shrink-0" />
                        )}
                        <div>
                          <p className="text-foreground">
                            {label} <span className="text-muted-foreground">v{h.attributes.policyVersion}</span>
                            {" · "}
                            <span className="text-muted-foreground">
                              {withdrawn ? "withdrawn" : superseded ? "superseded" : "accepted"}
                              {h.attributes.channel ? ` (${h.attributes.channel})` : ""}
                            </span>
                          </p>
                          {when && (
                            <p className="text-xs text-muted-foreground">{new Date(when).toLocaleString()}</p>
                          )}
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </section>
            )}

            {/* ── Acting on behalf (delegated access, L5) ── */}
            <DelegationManagementSection />

            {/* ── Your Rights ─────────────────────────────── */}
            <section className="bg-card rounded-lg border border-border p-6">
              <h3 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
                <FileText className="w-4 h-4 text-indigo-600" />
                Your Data Rights
              </h3>

              <p className="text-sm text-muted-foreground mb-4">
                Subject to applicable law, you have the right to access, correct,
                delete, restrict, or export your personal data. For requests
                beyond what is available here, contact{" "}
                <a
                  href="mailto:support@impilo.io"
                  className="text-primary hover:text-blue-800"
                >
                  support@impilo.io
                </a>
                .
              </p>

              <div className="flex flex-wrap gap-3">
                <Link
                  href="/privacy#16"
                  target="_blank"
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground border border-border rounded-lg hover:border-blue-300 hover:bg-info-soft transition-colors"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  View Your Rights
                </Link>
                <button
                  onClick={() => {
                    /* Data export is a future feature — placeholder */
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground border border-border rounded-lg hover:border-blue-300 hover:bg-info-soft transition-colors"
                >
                  <Download className="w-3.5 h-3.5" />
                  Request Data Export
                </button>
              </div>
            </section>

            {/* ── Account Deletion ────────────────────────── */}
            <section className="bg-card rounded-lg border border-red-100 p-6">
              <h3 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
                <Trash2 className="w-4 h-4 text-red-500" />
                Delete Account
              </h3>

              {hasPendingDeletion ? (
                <div className="space-y-3">
                  <div className="flex items-start gap-3 p-3 bg-warning-soft border border-warning/35 rounded-lg">
                    <Clock className="w-4 h-4 text-amber-600 mt-0.5 shrink-0" />
                    <div>
                      <p className="text-sm font-medium text-warning-foreground">
                        Deletion request pending
                      </p>
                      <p className="text-xs text-amber-600 mt-0.5">
                        Submitted{" "}
                        {deletionRequest?.attributes?.requestedAt
                          ? new Date(
                              deletionRequest.attributes.requestedAt
                            ).toLocaleDateString()
                          : "recently"}
                        . Your account will be processed for deletion.
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={() => cancelDeletion.mutate()}
                    disabled={cancelDeletion.isPending}
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground border border-border rounded-lg hover:border-gray-400 transition-colors disabled:opacity-50"
                  >
                    {cancelDeletion.isPending ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    ) : (
                      <XCircle className="w-3.5 h-3.5" />
                    )}
                    Cancel Deletion Request
                  </button>
                </div>
              ) : showDeleteConfirm ? (
                <div className="space-y-4">
                  <div className="flex items-start gap-3 p-3 bg-danger-soft border border-danger/28 rounded-lg">
                    <AlertTriangle className="w-4 h-4 text-red-600 mt-0.5 shrink-0" />
                    <div>
                      <p className="text-sm font-medium text-red-800">
                        This action cannot be easily undone
                      </p>
                      <p className="text-xs text-red-600 mt-1">
                        Your account and associated data will be scheduled for
                        deletion. Some data may be retained where required by law
                        for security, audit, public health, or recordkeeping.
                      </p>
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-foreground mb-1">
                      Reason (optional)
                    </label>
                    <textarea
                      value={deleteReason}
                      onChange={(e) => setDeleteReason(e.target.value)}
                      placeholder="Help us understand why you're leaving..."
                      className="w-full px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-red-500 resize-none"
                      rows={2}
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-foreground mb-1">
                      Type <strong>DELETE</strong> to confirm
                    </label>
                    <input
                      type="text"
                      value={deleteConfirmText}
                      onChange={(e) => setDeleteConfirmText(e.target.value)}
                      className="w-full px-3 py-2 border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="DELETE"
                    />
                  </div>

                  <div className="flex gap-3">
                    <button
                      onClick={() => requestDeletion.mutate({ reason: deleteReason }, {
                        onSuccess: () => {
                          setShowDeleteConfirm(false);
                          setDeleteReason("");
                          setDeleteConfirmText("");
                        },
                      })}
                      disabled={
                        deleteConfirmText !== "DELETE" || requestDeletion.isPending
                      }
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white bg-red-600 rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {requestDeletion.isPending ? (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      ) : (
                        <Trash2 className="w-4 h-4" />
                      )}
                      Delete My Account
                    </button>
                    <button
                      onClick={() => {
                        setShowDeleteConfirm(false);
                        setDeleteConfirmText("");
                        setDeleteReason("");
                      }}
                      className="px-4 py-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <p className="text-sm text-muted-foreground mb-3">
                    Permanently delete your Impilo account and request removal of
                    your personal data. This action will revoke all consents and
                    deactivate your account.
                  </p>
                  <button
                    onClick={() => setShowDeleteConfirm(true)}
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-red-600 border border-danger/28 rounded-lg hover:bg-danger-soft transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    Request Account Deletion
                  </button>
                </div>
              )}
            </section>

            {/* ── Legal Documents ───────────────────────────── */}
            <section className="bg-card rounded-lg border border-border p-6">
              <h3 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
                <FileText className="w-4 h-4 text-muted-foreground" />
                Legal Documents
              </h3>
              <div className="flex flex-wrap gap-3">
                <Link
                  href="/privacy"
                  target="_blank"
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground border border-border rounded-lg hover:border-blue-300 hover:bg-info-soft transition-colors"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  Privacy Policy
                </Link>
                <Link
                  href="/terms"
                  target="_blank"
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground border border-border rounded-lg hover:border-blue-300 hover:bg-info-soft transition-colors"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  Terms of Use
                </Link>
                <Link
                  href="/account-deletion"
                  target="_blank"
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground border border-border rounded-lg hover:border-blue-300 hover:bg-info-soft transition-colors"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  Account Deletion Notice
                </Link>
                <Link
                  href="/privacy/app-stores"
                  target="_blank"
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-foreground border border-border rounded-lg hover:border-blue-300 hover:bg-info-soft transition-colors"
                >
                  <ExternalLink className="w-3.5 h-3.5" />
                  App Store Privacy
                </Link>
              </div>
            </section>

            {/* ── Contact ─────────────────────────────────── */}
            <section className="bg-background rounded-lg border border-border p-5">
              <p className="text-xs text-muted-foreground">
                For privacy questions, data requests, or account deletion
                assistance, contact{" "}
                <a
                  href="mailto:support@impilo.io"
                  className="text-primary hover:text-blue-800"
                >
                  support@impilo.io
                </a>{" "}
                or visit{" "}
                <a
                  href="https://www.impilo.io"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary hover:text-blue-800"
                >
                  www.impilo.io
                </a>
                .
              </p>
            </section>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

/* ------------------------------------------------------------------ */
/*  Consent row component                                              */
/* ------------------------------------------------------------------ */

function ConsentRow({
  label,
  href,
  accepted,
  acceptedAt,
  version,
  onRevoke,
  revoking,
}: {
  label: string;
  href: string;
  accepted?: boolean;
  acceptedAt?: string;
  version: string;
  onRevoke: () => void;
  revoking: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-4 p-3 bg-background rounded-lg">
      <div className="flex items-center gap-3 min-w-0">
        {accepted ? (
          <CheckCircle className="w-4 h-4 text-green-600 shrink-0" />
        ) : (
          <XCircle className="w-4 h-4 text-muted-foreground shrink-0" />
        )}
        <div className="min-w-0">
          <Link
            href={href}
            target="_blank"
            className="text-sm font-medium text-foreground hover:text-primary transition-colors"
          >
            {label}
          </Link>
          <p className="text-xs text-muted-foreground">
            {accepted && acceptedAt
              ? `Accepted ${new Date(acceptedAt).toLocaleDateString()} (v${version})`
              : "Not accepted"}
          </p>
        </div>
      </div>
      {accepted && (
        <button
          onClick={onRevoke}
          disabled={revoking}
          className="text-xs text-muted-foreground hover:text-red-600 transition-colors disabled:opacity-50 shrink-0"
        >
          {revoking ? "Revoking..." : "Revoke"}
        </button>
      )}
    </div>
  );
}
