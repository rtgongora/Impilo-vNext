"use client";

/**
 * Credentials & CPD - professional credentials plus personal digital credential vault.
 * Data source: VARAPI via BFF proxy (licenses, affiliations) and credential-service via Experience BFF.
 */

import Link from "next/link";
import { ArrowLeft, Award, Shield, BookOpen, CheckCircle2, AlertTriangle, Clock, Download, BadgeCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useProviderLicenses, type LicenseResource } from "@/hooks/queries/useLicenses";
import { useProviderCpd } from "@/hooks/queries/useCpd";
import { usePersonalCredentials } from "@/hooks/queries/usePersonalCredentials";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BFF_BASE_URL = process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8160";

function useCouncilAffiliations(providerId: string | undefined) {
  return useQuery<{ data: unknown[] }>({
    queryKey: ["provider-affiliations", providerId],
    queryFn: () => apiClient.get(`/internal/v1/registry/providers/${providerId}/affiliations`),
    enabled: !!providerId,
  });
}

const LICENSE_STATUS_STYLES: Record<string, { bg: string; text: string; icon: typeof CheckCircle2 }> = {
  ACTIVE: { bg: "bg-green-50 border-green-200", text: "text-green-700", icon: CheckCircle2 },
  SUSPENDED: { bg: "bg-amber-50 border-amber-200", text: "text-amber-700", icon: AlertTriangle },
  REVOKED: { bg: "bg-red-50 border-red-200", text: "text-red-700", icon: AlertTriangle },
  EXPIRED: { bg: "bg-gray-50 border-gray-200", text: "text-gray-700", icon: Clock },
};

function formatCredentialDate(value: string | null | undefined): string {
  if (!value) return "No expiry";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
}

export default function CredentialsPage() {
  const user = useAuthStore((s) => s.user);
  const { data: licenseData, isLoading } = useProviderLicenses(user?.id);
  useCouncilAffiliations(user?.providerId ?? user?.id);
  const { data: cpdData } = useProviderCpd(user?.id);
  const { data: personalCredentialData, isLoading: personalCredentialsLoading, isError: personalCredentialsError } = usePersonalCredentials();
  const cpd = cpdData?.data;

  const licenses: LicenseResource[] = licenseData?.data ?? [];
  const personalCredentials = personalCredentialData?.data ?? [];
  const activeLicenses = licenses.filter((l) => l.status === "ACTIVE");
  const hasActive = activeLicenses.length > 0;

  return (
    <AppLayout>
      <PageShell title="Credentials & CPD" subtitle="Professional licenses, certifications, continuing education, and digital credentials">
        <div className="mb-4">
          <Link href="/home" className="inline-flex items-center gap-1 text-sm text-gray-500 transition-colors hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Back to Home
          </Link>
        </div>

        <div className="space-y-6">
          <div className={`rounded-lg border-2 p-6 ${hasActive ? "border-green-200 bg-green-50" : "border-amber-200 bg-amber-50"}`}>
            <div className="mb-4 flex items-center gap-3">
              <div className={`flex h-12 w-12 items-center justify-center rounded-full ${hasActive ? "bg-green-100" : "bg-amber-100"}`}>
                {hasActive ? <CheckCircle2 className="h-6 w-6 text-green-600" /> : <AlertTriangle className="h-6 w-6 text-amber-600" />}
              </div>
              <div>
                <h2 className="text-lg font-semibold text-gray-900">
                  {hasActive ? "Professional License Active" : "License Status Requires Attention"}
                </h2>
                <p className="text-sm text-gray-600">
                  {licenses.length} license{licenses.length !== 1 ? "s" : ""} on record
                  {activeLicenses.length > 0 && ` · ${activeLicenses.length} active`}
                </p>
              </div>
            </div>

            {isLoading ? (
              <p className="text-sm text-gray-500">Loading license data...</p>
            ) : licenses.length === 0 ? (
              <p className="text-sm text-gray-500">No license records found. Contact your licensing council.</p>
            ) : (
              <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
                {licenses.map((lic, idx) => {
                  const style = LICENSE_STATUS_STYLES[lic.status] ?? LICENSE_STATUS_STYLES.EXPIRED;
                  const StatusIcon = style.icon;
                  return (
                    <div key={idx} className={`rounded-lg border p-4 ${style.bg}`}>
                      <div className="mb-2 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <StatusIcon className={`h-4 w-4 ${style.text}`} />
                          <span className={`text-sm font-semibold ${style.text}`}>{lic.status}</span>
                        </div>
                        {lic.licenseNumber ? <span className="font-mono text-xs text-gray-500">{lic.licenseNumber}</span> : null}
                      </div>
                      {lic.issuedBy ? <p className="text-xs text-gray-600">Issued by: {lic.issuedBy}</p> : null}
                      {lic.cadre ? <p className="text-xs text-gray-600">Cadre: {lic.cadre}</p> : null}
                      <div className="mt-2 flex gap-4 text-xs text-gray-500">
                        {lic.validFrom ? <span>From: {new Date(lic.validFrom).toLocaleDateString()}</span> : null}
                        {lic.validTo ? <span>To: {new Date(lic.validTo).toLocaleDateString()}</span> : null}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <div className="mb-4 flex items-center gap-2">
              <BookOpen className="h-5 w-5 text-impilo-500" />
              <h3 className="text-base font-semibold text-gray-900">CPD Tracker</h3>
            </div>
            <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
              <div>
                {cpd?.currentCycle ? (
                  <>
                    <p className="mb-1 text-xs text-gray-500">
                      Current Cycle: {cpd.currentCycle.startDate?.slice(0, 4)}-{cpd.currentCycle.endDate?.slice(0, 4)}
                    </p>
                    <div className="flex items-center gap-3">
                      <div className="flex-1">
                        <div className="h-3 rounded-full bg-gray-200">
                          <div
                            className="h-3 rounded-full bg-impilo-500 transition-all"
                            style={{ width: `${Math.min(100, Math.round((cpd.currentCycle.earnedPoints / cpd.currentCycle.requiredPoints) * 100))}%` }}
                          />
                        </div>
                      </div>
                      <span className="text-sm font-semibold text-gray-900">
                        {cpd.currentCycle.earnedPoints}/{cpd.currentCycle.requiredPoints}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-gray-500">
                      {Math.max(0, cpd.currentCycle.requiredPoints - cpd.currentCycle.earnedPoints)} points needed
                      {cpd.currentCycle.endDate && ` · Deadline: ${cpd.currentCycle.endDate}`}
                    </p>
                  </>
                ) : (
                  <p className="text-xs text-gray-500">No active CPD cycle. Contact your council to start one.</p>
                )}
              </div>
              <div className="flex gap-3">
                <button className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-impilo-50 px-4 py-2.5 text-sm font-medium text-impilo-600 transition-colors hover:bg-impilo-100">
                  <BookOpen className="h-4 w-4" /> Browse Courses
                </button>
                <button className="inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-amber-50 px-4 py-2.5 text-sm font-medium text-amber-700 transition-colors hover:bg-amber-100">
                  <Award className="h-4 w-4" /> Log Activity
                </button>
              </div>
            </div>
          </div>

          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <div className="mb-4 flex items-center gap-2">
              <BadgeCheck className="h-5 w-5 text-indigo-600" />
              <h3 className="text-base font-semibold text-gray-900">Digital credential vault</h3>
            </div>
            <p className="mb-4 text-sm text-gray-500">
              Citizen-scoped digital credentials now flow through the Experience BFF. Public verification still uses the standalone verifier.
            </p>

            {personalCredentialsLoading ? (
              <p className="text-sm text-gray-500">Loading digital credentials...</p>
            ) : personalCredentialsError ? (
              <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
                We could not load your digital credential vault right now.
              </div>
            ) : personalCredentials.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-6 text-sm text-gray-500">
                No digital credentials were returned for your signed-in identity.
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2">
                {personalCredentials.map((credential) => (
                  <div key={credential.credentialId} className="rounded-lg border border-gray-200 p-4">
                    <div className="mb-3 flex items-start justify-between gap-3">
                      <div>
                        <p className="text-sm font-semibold text-gray-900">{credential.title}</p>
                        <p className="text-xs text-gray-500">{credential.credentialType}</p>
                      </div>
                      <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${credential.status === "ACTIVE" ? "bg-green-100 text-green-700" : credential.status === "EXPIRED" ? "bg-amber-100 text-amber-700" : "bg-gray-100 text-gray-600"}`}>
                        {credential.status}
                      </span>
                    </div>
                    <dl className="space-y-1 text-xs text-gray-600">
                      <div className="flex justify-between gap-3">
                        <dt>Issued by</dt>
                        <dd className="text-right text-gray-900">{credential.issuedBy || "Unknown issuer"}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt>Holder</dt>
                        <dd className="text-right text-gray-900">{credential.subjectName || "Current user"}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt>Valid</dt>
                        <dd className="text-right text-gray-900">
                          {formatCredentialDate(credential.validFrom)} - {formatCredentialDate(credential.validTo)}
                        </dd>
                      </div>
                    </dl>
                    <div className="mt-4 flex flex-wrap gap-2">
                      <a
                        href={`${BFF_BASE_URL}/internal/v1/credentials/${credential.credentialId}/pdf`}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-impilo-600"
                      >
                        <Download className="h-4 w-4" /> Download PDF
                      </a>
                      {credential.verificationUrl ? (
                        <a
                          href={credential.verificationUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1.5 rounded-lg bg-gray-100 px-3 py-2 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-200"
                        >
                          <Shield className="h-4 w-4" /> Verify
                        </a>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <div className="mb-4 flex items-center gap-2">
              <Shield className="h-5 w-5 text-purple-600" />
              <h3 className="text-base font-semibold text-gray-900">Certifications & Specialist Credentials</h3>
            </div>
            {(() => {
              const certs = licenses.filter((l) => {
                const lt = (((l as Record<string, unknown>).licenseType as string) ?? "").toUpperCase();
                return lt === "SPECIALIST" || lt === "TEMPORARY" || lt === "CERTIFICATION";
              });
              const rows = certs.length === 0 ? licenses : certs;

              return rows.length === 0 ? (
                <p className="py-3 text-sm text-gray-400">No certifications on record.</p>
              ) : (
                <div className="divide-y divide-gray-100">
                  {rows.map((lic, idx) => {
                    const isExpiring = lic.validTo && new Date(lic.validTo).getTime() - Date.now() < 90 * 86400000;
                    return (
                      <div key={idx} className="flex items-center justify-between py-3">
                        <div>
                          <p className="text-sm font-medium text-gray-900">
                            {lic.cadre ?? lic.issuedBy ?? "License"} - {((lic as Record<string, unknown>).licenseType as string) ?? "PRACTICE"}
                          </p>
                          <p className="text-xs text-gray-500">{lic.issuedBy ?? "Licensing Council"}</p>
                        </div>
                        <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${lic.status === "ACTIVE" && !isExpiring ? "bg-green-100 text-green-700" : isExpiring ? "bg-amber-100 text-amber-700" : "bg-gray-100 text-gray-600"}`}>
                          {lic.status === "ACTIVE" && isExpiring ? "Expiring Soon" : lic.status}
                        </span>
                      </div>
                    );
                  })}
                </div>
              );
            })()}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
