"use client";

/**
 * Credentials & CPD — Full professional credentials page with
 * license status, CPD tracker, council affiliations, and certifications.
 *
 * Lovable reference: MyProfessionalHub Credentials tab.
 * Data source: VARAPI via BFF proxy (licenses, affiliations).
 */

import Link from "next/link";
import { ArrowLeft, Award, Shield, BookOpen, CheckCircle2, AlertTriangle, Clock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useProviderLicenses, type LicenseResource } from "@/hooks/queries/useLicenses";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

function useCounsilAffiliations(providerId: string | undefined) {
  return useQuery<{ data: unknown[] }>({
    queryKey: ["provider-affiliations", providerId],
    queryFn: () => apiClient.get(`/internal/v1/registry/providers/${providerId}`),
    enabled: !!providerId,
  });
}

const LICENSE_STATUS_STYLES: Record<string, { bg: string; text: string; icon: typeof CheckCircle2 }> = {
  ACTIVE: { bg: "bg-green-50 border-green-200", text: "text-green-700", icon: CheckCircle2 },
  SUSPENDED: { bg: "bg-amber-50 border-amber-200", text: "text-amber-700", icon: AlertTriangle },
  REVOKED: { bg: "bg-red-50 border-red-200", text: "text-red-700", icon: AlertTriangle },
  EXPIRED: { bg: "bg-gray-50 border-gray-200", text: "text-gray-700", icon: Clock },
};

export default function CredentialsPage() {
  const user = useAuthStore((s) => s.user);
  const { data: licenseData, isLoading } = useProviderLicenses(user?.id);
  const { data: affiliationData } = useCounsilAffiliations(user?.id);

  const licenses: LicenseResource[] = licenseData?.data ?? [];
  const activeLicenses = licenses.filter((l) => l.status === "ACTIVE");
  const hasActive = activeLicenses.length > 0;

  return (
    <AppLayout>
      <PageShell title="Credentials & CPD" subtitle="Professional licenses, certifications, and continuing education">
        <div className="mb-4">
          <Link href="/home" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors">
            <ArrowLeft className="w-4 h-4" /> Back to Home
          </Link>
        </div>

        <div className="space-y-6">
          {/* License Status Overview */}
          <div className={`rounded-lg border-2 p-6 ${hasActive ? "border-green-200 bg-green-50" : "border-amber-200 bg-amber-50"}`}>
            <div className="flex items-center gap-3 mb-4">
              <div className={`w-12 h-12 rounded-full flex items-center justify-center ${hasActive ? "bg-green-100" : "bg-amber-100"}`}>
                {hasActive
                  ? <CheckCircle2 className="w-6 h-6 text-green-600" />
                  : <AlertTriangle className="w-6 h-6 text-amber-600" />
                }
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

            {/* License Cards */}
            {isLoading ? (
              <p className="text-sm text-gray-500">Loading license data...</p>
            ) : licenses.length === 0 ? (
              <p className="text-sm text-gray-500">No license records found. Contact your licensing council.</p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mt-4">
                {licenses.map((lic, idx) => {
                  const style = LICENSE_STATUS_STYLES[lic.status] ?? LICENSE_STATUS_STYLES.EXPIRED;
                  const StatusIcon = style.icon;
                  return (
                    <div key={idx} className={`rounded-lg border p-4 ${style.bg}`}>
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          <StatusIcon className={`w-4 h-4 ${style.text}`} />
                          <span className={`text-sm font-semibold ${style.text}`}>{lic.status}</span>
                        </div>
                        {lic.licenseNumber && (
                          <span className="text-xs font-mono text-gray-500">{lic.licenseNumber}</span>
                        )}
                      </div>
                      {lic.issuedBy && <p className="text-xs text-gray-600">Issued by: {lic.issuedBy}</p>}
                      {lic.cadre && <p className="text-xs text-gray-600">Cadre: {lic.cadre}</p>}
                      <div className="flex gap-4 mt-2 text-xs text-gray-500">
                        {lic.validFrom && <span>From: {new Date(lic.validFrom).toLocaleDateString()}</span>}
                        {lic.validTo && <span>To: {new Date(lic.validTo).toLocaleDateString()}</span>}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* CPD Tracker */}
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <div className="flex items-center gap-2 mb-4">
              <BookOpen className="w-5 h-5 text-blue-600" />
              <h3 className="text-base font-semibold text-gray-900">CPD Tracker</h3>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <p className="text-xs text-gray-500 mb-1">Current Cycle: 2024–2026</p>
                <div className="flex items-center gap-3">
                  <div className="flex-1">
                    <div className="bg-gray-200 rounded-full h-3">
                      <div className="bg-blue-500 rounded-full h-3 transition-all" style={{ width: "72%" }} />
                    </div>
                  </div>
                  <span className="text-sm font-semibold text-gray-900">18/25</span>
                </div>
                <p className="text-xs text-gray-500 mt-1">7 points needed · Deadline: 2026-03-31</p>
              </div>
              <div className="flex gap-3">
                <button className="flex-1 inline-flex items-center justify-center gap-1.5 px-4 py-2.5 text-sm font-medium bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors">
                  <BookOpen className="w-4 h-4" /> Browse Courses
                </button>
                <button className="flex-1 inline-flex items-center justify-center gap-1.5 px-4 py-2.5 text-sm font-medium bg-amber-50 text-amber-700 rounded-lg hover:bg-amber-100 transition-colors">
                  <Award className="w-4 h-4" /> Log Activity
                </button>
              </div>
            </div>
          </div>

          {/* Certifications */}
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <div className="flex items-center gap-2 mb-4">
              <Shield className="w-5 h-5 text-purple-600" />
              <h3 className="text-base font-semibold text-gray-900">Certifications</h3>
            </div>
            <div className="divide-y divide-gray-100">
              <div className="flex items-center justify-between py-3">
                <div>
                  <p className="text-sm font-medium text-gray-900">BLS Provider</p>
                  <p className="text-xs text-gray-500">American Heart Association</p>
                </div>
                <span className="px-2.5 py-1 text-xs font-medium rounded-full bg-green-100 text-green-700">Valid</span>
              </div>
              <div className="flex items-center justify-between py-3">
                <div>
                  <p className="text-sm font-medium text-gray-900">ACLS Provider</p>
                  <p className="text-xs text-gray-500">American Heart Association</p>
                </div>
                <span className="px-2.5 py-1 text-xs font-medium rounded-full bg-amber-100 text-amber-700">Expiring Soon</span>
              </div>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
