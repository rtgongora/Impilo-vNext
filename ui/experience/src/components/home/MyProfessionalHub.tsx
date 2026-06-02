"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Award,
  Bell,
  BookOpen,
  Building2,
  Calendar,
  ChevronRight,
  FileText,
  Filter,
  Globe,
  GraduationCap,
  Mail,
  MapPin,
  Play,
  Search,
  Shield,
  Stethoscope,
} from "lucide-react";
import type { FacilityResource } from "@/hooks/queries/useFacilities";
import type { LicenseResource } from "@/hooks/queries/useLicenses";
import type { ProviderPrivilege } from "@/hooks/queries/useProviderPrivileges";
import { showBackendPendingToast } from "@/hooks/useBackendPendingToast";
import {
  PendingApiRoute,
  ProfessionalHubTab,
} from "@/lib/experience/pending-api-routes";

const MOCK_PRIORITY = [
  { id: "1", title: "Critical K+ 7.2", patient: "M. Ndlovu", context: "Virtual", time: "2 min ago", severity: "critical" as const },
  { id: "2", title: "Discharge summary pending", patient: "T. Moyo", context: "Facility", time: "Today", severity: "warning" as const },
  { id: "3", title: "Missed specialist callback", patient: "Referral", context: "System", time: "Yesterday", severity: "info" as const },
];

const MOCK_PATIENTS = [
  { id: "1", name: "T. Moyo", context: "Virtual", lastSeen: "3 days ago", statusLabel: "Due" },
  { id: "2", name: "M. Ndlovu", context: "Virtual", lastSeen: "Today", statusLabel: "Critical" },
  { id: "3", name: "S. Dube", context: "Facility", lastSeen: "1 week", statusLabel: "Stable" },
];

const MOCK_SCHEDULE = [
  { id: "1", time: "08:00", title: "Shift", location: "Ward", type: "shift" as const },
  { id: "2", time: "14:00", title: "Virtual consult", location: "Online", type: "consult" as const },
];

export interface MyProfessionalHubProps {
  displayName?: string;
  isClinical: boolean;
  licenses: LicenseResource[];
  licenseActive: boolean;
  facilities: FacilityResource[];
  facilitiesLoading: boolean;
  privileges: ProviderPrivilege[];
  showRegistryAdmin?: boolean;
  showOrgAdmin?: boolean;
  onStartShift: (facility: FacilityResource) => void;
  onSwitchToWork: () => void;
  onStartVirtualSession: () => void;
}

export function MyProfessionalHub({
  displayName,
  isClinical,
  licenses,
  licenseActive,
  facilities,
  facilitiesLoading,
  privileges,
  showRegistryAdmin,
  showOrgAdmin,
  onStartShift,
  onSwitchToWork,
  onStartVirtualSession,
}: MyProfessionalHubProps) {
  const [activeTab, setActiveTab] = useState<ProfessionalHubTab>(ProfessionalHubTab.Dashboard);
  const [patientSearch, setPatientSearch] = useState("");
  const [patientFilter, setPatientFilter] = useState("all");

  const cpdEarned = 18;
  const cpdRequired = 25;

  const filteredPatients = useMemo(
    () =>
      MOCK_PATIENTS.filter((p) => {
        const q = patientSearch.toLowerCase();
        const matchesSearch = !q || p.name.toLowerCase().includes(q);
        const matchesFilter =
          patientFilter === "all" || p.context.toLowerCase().includes(patientFilter.toLowerCase());
        return matchesSearch && matchesFilter;
      }),
    [patientSearch, patientFilter],
  );

  const pendingPriority = () => {
    showBackendPendingToast(PendingApiRoute.ProfessionalPriorityFeed, "Priority attention feed");
  };

  const pendingPatients = () => {
    showBackendPendingToast(PendingApiRoute.ProfessionalPatientPanel, "Cross-facility patient panel");
  };

  const pendingSchedule = () => {
    showBackendPendingToast(PendingApiRoute.ProfessionalMergedSchedule, "Merged professional schedule");
  };

  const pendingLandela = () => {
    showBackendPendingToast(PendingApiRoute.ProfessionalLandelaInbox, "Landela professional inbox");
  };

  const pendingEmail = () => {
    showBackendPendingToast(PendingApiRoute.ProfessionalMohEmail, "MoH professional email");
  };

  const pendingCpd = (action: string) => {
    showBackendPendingToast(
      action === "courses" ? PendingApiRoute.ProfessionalCpdCourses : PendingApiRoute.ProfessionalCpdLogActivity,
      action === "courses" ? "CPD course catalogue" : "Log CPD activity",
    );
  };

  return (
    <div className="space-y-4">
      {(showRegistryAdmin || showOrgAdmin) && (
        <div className="rounded-xl border border-slate-200 bg-gradient-to-r from-slate-50 to-white p-5">
          <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
            <Shield className="w-4 h-4 text-amber-700" />
            Administrative planes
          </h3>
          <p className="mt-1 text-xs text-gray-600 max-w-2xl">
            Registry and organization operations are separate from facility work (facility → workspace → shift).
          </p>
          <div className="mt-4 flex flex-wrap gap-3">
            {showRegistryAdmin && (
              <Link
                href="/registry-admin"
                className="inline-flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 text-sm font-medium text-amber-950 hover:border-amber-400"
              >
                Registry administration
              </Link>
            )}
            {showOrgAdmin && (
              <Link
                href="/organization-admin"
                className="inline-flex items-center gap-2 rounded-lg border border-violet-200 bg-violet-50 px-4 py-2.5 text-sm font-medium text-violet-950 hover:border-violet-400"
              >
                Organization administration
              </Link>
            )}
          </div>
        </div>
      )}

      <div className="flex gap-1 bg-gray-100 p-1 rounded-lg overflow-x-auto">
        {(
          [
            [ProfessionalHubTab.Dashboard, "Dashboard"],
            [ProfessionalHubTab.Affiliations, "Affiliations"],
            [ProfessionalHubTab.Patients, isClinical ? "My Patients" : "My Tasks"],
            [ProfessionalHubTab.Schedule, "Schedule"],
            [ProfessionalHubTab.Credentials, "Credentials"],
          ] as const
        ).map(([tab, label]) => (
          <button
            key={tab}
            type="button"
            onClick={() => setActiveTab(tab)}
            className={`flex-1 min-w-[5.5rem] px-3 py-2 text-xs sm:text-sm font-medium rounded-md whitespace-nowrap ${
              activeTab === tab ? "bg-white text-gray-900 shadow-sm" : "text-gray-500"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {activeTab === ProfessionalHubTab.Dashboard && (
        <div className="space-y-4">
          <div className="rounded-lg border border-amber-200 bg-amber-50/50 p-4">
            <h4 className="text-sm font-semibold flex items-center gap-2 text-amber-900">
              <Bell className="h-4 w-4" />
              Priority attention ({MOCK_PRIORITY.length})
            </h4>
            <div className="mt-3 space-y-2">
              {MOCK_PRIORITY.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={pendingPriority}
                  className={`w-full flex items-center justify-between p-3 rounded-lg border text-left hover:bg-white transition-colors ${
                    item.severity === "critical"
                      ? "border-red-200 bg-red-50"
                      : item.severity === "warning"
                        ? "border-amber-200 bg-amber-50"
                        : "border-gray-200 bg-white"
                  }`}
                >
                  <div className="flex items-center gap-3">
                    <AlertTriangle className="h-4 w-4 text-red-500 shrink-0" />
                    <div>
                      <p className="text-sm font-medium">{item.title}</p>
                      <p className="text-xs text-gray-500">
                        {item.patient} · {item.context}
                      </p>
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 text-gray-400" />
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            <Link href="/lab" className="rounded-lg border bg-white p-4 text-center hover:shadow-md">
              <p className="text-2xl font-bold text-impilo-600">12</p>
              <p className="text-xs text-gray-500">Pending results</p>
            </Link>
            <Link href="/clinical" className="rounded-lg border bg-white p-4 text-center hover:shadow-md">
              <p className="text-2xl font-bold text-amber-600">3</p>
              <p className="text-xs text-gray-500">Awaiting signature</p>
            </Link>
            <Link href="/telemedicine" className="rounded-lg border bg-white p-4 text-center hover:shadow-md">
              <p className="text-2xl font-bold text-teal-600">5</p>
              <p className="text-xs text-gray-500">Referrals to me</p>
            </Link>
            <Link href="/scheduling" className="rounded-lg border bg-white p-4 text-center hover:shadow-md">
              <p className="text-2xl font-bold text-purple-600">8</p>
              <p className="text-xs text-gray-500">Follow-ups due</p>
            </Link>
          </div>

          <div className="rounded-lg border bg-white p-4">
            <h4 className="text-sm font-semibold flex items-center gap-2 mb-3">
              <Calendar className="h-4 w-4 text-impilo-500" />
              Today&apos;s schedule
            </h4>
            {MOCK_SCHEDULE.map((item) => (
              <div key={item.id} className="flex items-center justify-between py-2 border-b last:border-0">
                <div className="flex items-center gap-3">
                  <span className="text-xs font-medium bg-gray-100 px-2 py-1 rounded">{item.time}</span>
                  <div>
                    <p className="text-sm font-medium">{item.title}</p>
                    <p className="text-xs text-gray-500 flex items-center gap-1">
                      <MapPin className="h-3 w-3" /> {item.location}
                    </p>
                  </div>
                </div>
                <button type="button" onClick={pendingSchedule} className="text-gray-400 hover:text-impilo-600">
                  <ChevronRight className="h-4 w-4" />
                </button>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <button
              type="button"
              onClick={pendingLandela}
              className="rounded-lg border bg-white p-4 text-left hover:border-impilo-200"
            >
              <FileText className="h-5 w-5 text-cyan-600 mb-2" />
              <p className="text-sm font-medium">Landela notifications</p>
              <p className="text-xs text-gray-500">Document workflow inbox (BFF pending)</p>
            </button>
            <button
              type="button"
              onClick={pendingEmail}
              className="rounded-lg border bg-white p-4 text-left hover:border-impilo-200"
            >
              <Mail className="h-5 w-5 text-indigo-600 mb-2" />
              <p className="text-sm font-medium">Professional email</p>
              <p className="text-xs text-gray-500">MoH integration (BFF pending)</p>
            </button>
          </div>

          <div className="rounded-lg border bg-white p-4 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap gap-4 text-sm">
              <span className="flex items-center gap-1">
                <Shield className={`h-4 w-4 ${licenseActive ? "text-green-600" : "text-red-500"}`} />
                {licenseActive ? "License active" : "License action required"}
              </span>
              <span className="flex items-center gap-1">
                <GraduationCap className="h-4 w-4 text-impilo-500" />
                CPD {cpdEarned}/{cpdRequired}
              </span>
            </div>
            <button
              type="button"
              onClick={() => setActiveTab(ProfessionalHubTab.Credentials)}
              className="text-sm text-impilo-600 font-medium"
            >
              View credentials
            </button>
          </div>
        </div>
      )}

      {activeTab === ProfessionalHubTab.Affiliations && (
        <div className="space-y-3">
          {facilitiesLoading ? (
            <p className="text-sm text-gray-500 py-8 text-center">Loading affiliations…</p>
          ) : facilities.length === 0 && privileges.length === 0 ? (
            <p className="text-sm text-gray-400 py-8 text-center">No facility affiliations found.</p>
          ) : (
            <>
              {privileges.slice(0, 8).map((priv) => (
                <div key={priv.id} className="rounded-lg border bg-white p-4 flex justify-between items-center">
                  <div className="flex items-center gap-3">
                    <Building2 className="h-5 w-5 text-gray-400" />
                    <div>
                      <p className="text-sm font-medium">Facility {priv.facilityId.slice(0, 8)}…</p>
                      <p className="text-xs text-gray-500">
                        {priv.privilegeType} · {priv.scope}
                      </p>
                    </div>
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">{priv.status}</span>
                </div>
              ))}
              {facilities.map((f) => (
                <div key={f.id} className="rounded-lg border bg-white p-4 flex justify-between items-start gap-4">
                  <div className="flex items-start gap-3">
                    <div className="h-10 w-10 rounded-lg bg-impilo-50 flex items-center justify-center">
                      <Building2 className="h-5 w-5 text-impilo-600" />
                    </div>
                    <div>
                      <p className="font-semibold text-sm">{f.attributes.name}</p>
                      <p className="text-xs text-gray-500">{f.attributes.facilityType}</p>
                    </div>
                  </div>
                  <div className="flex flex-col gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        onStartShift(f);
                        onSwitchToWork();
                      }}
                      className="inline-flex items-center gap-1 text-xs font-medium text-white bg-impilo-600 px-3 py-1.5 rounded-lg hover:bg-impilo-700"
                    >
                      <Play className="h-3 w-3" />
                      Start shift
                    </button>
                    <Link href="/facility-operations" className="text-xs text-impilo-600 text-center">
                      View facility
                    </Link>
                  </div>
                </div>
              ))}
            </>
          )}
          <div className="rounded-lg border border-dashed border-impilo-200 bg-impilo-50/30 p-4 flex justify-between items-center">
            <div>
              <p className="font-semibold text-sm">Independent practice (virtual)</p>
              <p className="text-xs text-gray-500">License-anchored · no facility required</p>
            </div>
            <button
              type="button"
              onClick={onStartVirtualSession}
              className="inline-flex items-center gap-1 text-xs border border-impilo-300 px-3 py-1.5 rounded-lg hover:bg-white"
            >
              <Globe className="h-3 w-3" />
              Start session
            </button>
          </div>
        </div>
      )}

      {activeTab === ProfessionalHubTab.Patients && (
        <div className="space-y-4">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                value={patientSearch}
                onChange={(e) => setPatientSearch(e.target.value)}
                placeholder="Search patients…"
                className="w-full pl-9 pr-3 py-2 text-sm border rounded-lg"
              />
            </div>
            <button type="button" className="p-2 border rounded-lg" aria-label="Filter">
              <Filter className="h-4 w-4" />
            </button>
          </div>
          <div className="flex gap-2 flex-wrap">
            {["all", "virtual", "facility"].map((ctx) => (
              <button
                key={ctx}
                type="button"
                onClick={() => setPatientFilter(ctx)}
                className={`text-xs px-3 py-1 rounded-full border ${
                  patientFilter === ctx ? "bg-impilo-600 text-white border-impilo-600" : "bg-white"
                }`}
              >
                {ctx === "all" ? "All" : ctx.charAt(0).toUpperCase() + ctx.slice(1)}
              </button>
            ))}
          </div>
          <div className="space-y-2">
            {filteredPatients.map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={pendingPatients}
                className="w-full rounded-lg border bg-white p-4 flex justify-between items-center text-left hover:shadow-sm"
              >
                <div className="flex items-center gap-3">
                  <div className="h-10 w-10 rounded-full bg-gray-100 flex items-center justify-center text-sm font-medium">
                    {p.name.split(" ").map((n) => n[0]).join("")}
                  </div>
                  <div>
                    <p className="font-medium text-sm">{p.name}</p>
                    <p className="text-xs text-gray-500">
                      {p.context} · Last seen {p.lastSeen}
                    </p>
                  </div>
                </div>
                <span className="text-xs bg-gray-100 px-2 py-0.5 rounded">{p.statusLabel}</span>
              </button>
            ))}
          </div>
          <Link href="/queue/search" className="text-sm text-impilo-600 font-medium">
            Open patient search (Experience) →
          </Link>
        </div>
      )}

      {activeTab === ProfessionalHubTab.Schedule && (
        <div className="rounded-lg border bg-white p-4 space-y-4">
          <h4 className="font-semibold text-sm flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            Merged schedule (all contexts)
          </h4>
          {MOCK_SCHEDULE.map((item) => (
            <div key={item.id} className="flex items-center gap-3 p-3 rounded-lg border">
              <span className="text-xs font-medium bg-gray-100 px-2 py-1 rounded">{item.time}</span>
              <div>
                <p className="text-sm font-medium">{item.title}</p>
                <p className="text-xs text-gray-500">{item.location}</p>
              </div>
            </div>
          ))}
          <button type="button" onClick={pendingSchedule} className="text-sm text-impilo-600">
            Load full merged schedule (BFF pending)
          </button>
          <Link href="/scheduling" className="block text-sm text-gray-600">
            Open Experience scheduling →
          </Link>
        </div>
      )}

      {activeTab === ProfessionalHubTab.Credentials && (
        <div className="space-y-4">
          <div
            className={`rounded-lg border-2 p-4 ${licenseActive ? "border-green-200 bg-green-50" : "border-red-200 bg-red-50"}`}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Shield className={`h-6 w-6 ${licenseActive ? "text-green-600" : "text-red-600"}`} />
                <div>
                  <p className="font-semibold">{licenseActive ? "License active" : "License action required"}</p>
                  <p className="text-sm text-gray-600">
                    {licenses[0]?.licenseNumber ?? "VARAPI license record"}
                  </p>
                </div>
              </div>
              <Link href="/home/credentials" className="text-sm text-impilo-600">
                Manage →
              </Link>
            </div>
          </div>

          <div className="rounded-lg border bg-white p-4">
            <div className="flex justify-between mb-2 text-sm">
              <span className="text-gray-500">CPD cycle</span>
              <span className="font-medium">
                {cpdEarned}/{cpdRequired} points
              </span>
            </div>
            <div className="w-full bg-gray-100 rounded-full h-2">
              <div className="bg-impilo-500 h-2 rounded-full" style={{ width: `${(cpdEarned / cpdRequired) * 100}%` }} />
            </div>
            <div className="flex gap-2 mt-4">
              <button
                type="button"
                onClick={() => pendingCpd("courses")}
                className="flex-1 text-xs border rounded-lg py-2 hover:bg-gray-50"
              >
                <BookOpen className="h-4 w-4 inline mr-1" />
                Browse courses
              </button>
              <button
                type="button"
                onClick={() => pendingCpd("log")}
                className="flex-1 text-xs border rounded-lg py-2 hover:bg-gray-50"
              >
                <Award className="h-4 w-4 inline mr-1" />
                Log activity
              </button>
            </div>
            <button
              type="button"
              onClick={() => showBackendPendingToast(PendingApiRoute.ProfessionalCpdCycle)}
              className="mt-2 text-xs text-impilo-600"
            >
              Sync CPD from council (BFF pending)
            </button>
          </div>

          <button
            type="button"
            onClick={() => showBackendPendingToast(PendingApiRoute.ProfessionalCertifications)}
            className="w-full rounded-lg border bg-white p-4 text-left hover:border-impilo-200"
          >
            <p className="text-sm font-medium flex items-center gap-2">
              <Award className="h-4 w-4 text-amber-500" />
              Certifications
            </p>
            <p className="text-xs text-gray-500 mt-1">BLS, ACLS, and council certificates (BFF pending)</p>
          </button>

          {displayName && (
            <p className="text-xs text-gray-400 text-center">
              Signed in as {displayName}. Client registry and VARAPI flows unchanged.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
