"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ArrowRight,
  CreditCard,
  FileSearch,
  FileText,
  Fingerprint,
  GitMerge,
  Key,
  PackageCheck,
  Printer,
  QrCode,
  RefreshCw,
  Search,
  Share2,
  ShieldCheck,
  UserCheck,
  UserPlus,
  Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useClientStewardshipWorkspace } from "@/hooks/queries/useClientRegistry";
import { useResolveIdentity, useRotateAlias } from "@/hooks/queries/useIdentity";

function labelize(value: string | null | undefined) {
  if (!value) return "Not stated";
  return value.replaceAll("_", " ");
}

const navLinks = [
  {
    label: "Cards",
    description: "Manage smart card requests, activation, and revocation.",
    href: "/operations/vito/cards",
    Icon: CreditCard,
    tone: "bg-info-soft text-primary-hover border-indigo-100",
  },
  {
    label: "Match Review",
    description: "Resolve identity match candidates from probabilistic matching.",
    href: "/operations/vito/match",
    Icon: GitMerge,
    tone: "bg-warning-soft text-warning-foreground border-amber-100",
  },
  {
    label: "Issuance",
    description: "Track Health ID issuance workflow from submission to delivery.",
    href: "/operations/vito/issuance",
    Icon: Key,
    tone: "bg-success-soft text-primary-hover border-emerald-100",
  },
  {
    label: "Deduplication",
    description: "Score, merge, and unmerge potential duplicate person records.",
    href: "/operations/vito/dedup",
    Icon: RefreshCw,
    tone: "bg-danger-soft text-danger border-rose-100",
  },
  {
    label: "Registry Admin",
    description: "Provisional IDs, registry dedup cases, and OpenCR matching.",
    href: "/operations/vito/registry-admin",
    Icon: FileSearch,
    tone: "bg-background text-foreground border-border",
  },
  {
    label: "Print & Slips",
    description: "Submit card print jobs and generate emergency / pickup slips.",
    href: "/operations/vito/print",
    Icon: Printer,
    tone: "bg-sky-50 text-sky-700 border-sky-100",
  },
  {
    label: "Internal Search",
    description: "Staff-only masked client search and full record retrieval.",
    href: "/operations/vito/internal-search",
    Icon: Search,
    tone: "bg-violet-50 text-violet-700 border-violet-100",
  },
  {
    label: "Recovery & SHS",
    description: "Secure handover, SHS creation, and JWS verification.",
    href: "/operations/vito/recovery",
    Icon: FileText,
    tone: "bg-orange-50 text-orange-700 border-orange-100",
  },
  {
    label: "Biometrics",
    description: "Biometric enrolment, verification, templates, and dedup assist.",
    href: "/operations/vito/biometrics",
    Icon: Fingerprint,
    tone: "bg-teal-50 text-teal-700 border-teal-100",
  },
  {
    label: "QR Resolver",
    description: "Resolve QR tokens to Health IDs and inspect public key.",
    href: "/citizen/health-id/qr",
    Icon: QrCode,
    tone: "bg-lime-50 text-lime-700 border-lime-100",
  },
  {
    label: "Client Registration",
    description: "Multi-step guided registration wizard for new client enrolment.",
    href: "/operations/vito/registration",
    Icon: UserPlus,
    tone: "bg-cyan-50 text-cyan-700 border-cyan-100",
  },
  {
    label: "Patient Shares",
    description: "Manage health record sharing requests, contributions, and revocations.",
    href: "/operations/vito/patient-shares",
    Icon: Share2,
    tone: "bg-pink-50 text-pink-700 border-pink-100",
  },
  {
    label: "Card Pickup",
    description: "Verify pickup tokens and confirm physical card handover to delegates.",
    href: "/operations/vito/cards/pickup",
    Icon: PackageCheck,
    tone: "bg-green-50 text-green-700 border-green-100",
  },
];

function IdentityResolutionPanel() {
  const [aliasType, setAliasType] = useState("");
  const [aliasValue, setAliasValue] = useState("");
  const resolve = useResolveIdentity();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    resolve.mutate({ aliasType, aliasValue });
  }

  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="flex items-center gap-2 mb-4">
        <Search className="h-4 w-4 text-indigo-600" />
        <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Identity Resolution</h3>
      </div>
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Alias Type</label>
          <input
            className="w-full rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-indigo-400"
            placeholder="e.g. NID, PASSPORT, PHONE"
            value={aliasType}
            onChange={(e) => setAliasType(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Alias Value</label>
          <input
            className="w-full rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-indigo-400"
            placeholder="Identifier value"
            value={aliasValue}
            onChange={(e) => setAliasValue(e.target.value)}
          />
        </div>
        <button
          type="submit"
          disabled={!aliasType || !aliasValue || resolve.isPending}
          className="w-full rounded-xl bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary disabled:opacity-50"
        >
          {resolve.isPending ? "Resolving…" : "Resolve"}
        </button>
      </form>
      {resolve.isSuccess && (
        <div className="mt-3 rounded-xl bg-success-soft px-3 py-2 text-sm text-primary-hover">
          <span className="font-medium">Health ID:</span> {resolve.data.data.healthId}
          {" · "}
          {resolve.data.data.resolved ? "Resolved" : "Not resolved"}
        </div>
      )}
      {resolve.isError && (
        <div className="mt-3 rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">Resolution failed. Check alias type and value.</div>
      )}
    </div>
  );
}

function AliasRotationPanel() {
  const [healthId, setHealthId] = useState("");
  const [aliasType, setAliasType] = useState("");
  const [newValue, setNewValue] = useState("");
  const rotate = useRotateAlias();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    rotate.mutate({ healthId, aliasType, newValue });
  }

  return (
    <div className="rounded-2xl border border-border bg-card p-5">
      <div className="flex items-center gap-2 mb-4">
        <Key className="h-4 w-4 text-amber-600" />
        <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Alias Rotation</h3>
      </div>
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Health ID</label>
          <input
            className="w-full rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-amber-400"
            placeholder="IMP-…"
            value={healthId}
            onChange={(e) => setHealthId(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">Alias Type</label>
          <input
            className="w-full rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-amber-400"
            placeholder="e.g. PHONE, EMAIL"
            value={aliasType}
            onChange={(e) => setAliasType(e.target.value)}
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1">New Value</label>
          <input
            className="w-full rounded-xl border border-border px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-amber-400"
            placeholder="New alias value"
            value={newValue}
            onChange={(e) => setNewValue(e.target.value)}
          />
        </div>
        <button
          type="submit"
          disabled={!healthId || !aliasType || !newValue || rotate.isPending}
          className="w-full rounded-xl bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
        >
          {rotate.isPending ? "Rotating…" : "Rotate Alias"}
        </button>
      </form>
      {rotate.isSuccess && (
        <div className="mt-3 rounded-xl bg-success-soft px-3 py-2 text-sm text-primary-hover">Alias rotated successfully.</div>
      )}
      {rotate.isError && (
        <div className="mt-3 rounded-xl bg-danger-soft px-3 py-2 text-sm text-danger">Rotation failed. Verify inputs and try again.</div>
      )}
    </div>
  );
}

export default function VitoOpsPage() {
  const workspace = useClientStewardshipWorkspace();
  const data = workspace.data?.data;

  const sections = [
    {
      label: "Deduplication Queue",
      description: "Review and resolve potential duplicate person records before merge execution.",
      Icon: GitMerge,
      tone: "bg-warning-soft text-warning-foreground border-amber-100",
      count: data?.duplicateQueue.length ?? 0,
    },
    {
      label: "Verification Queue",
      description: "Identity verification and assurance decisions awaiting stewardship action.",
      Icon: ShieldCheck,
      tone: "bg-sky-50 text-sky-700 border-sky-100",
      count: data?.verificationQueue.length ?? 0,
    },
    {
      label: "Stewardship Actions",
      description: "Open quality, correction, guardian, and authorisation follow-up work.",
      Icon: UserCheck,
      tone: "bg-success-soft text-primary-hover border-emerald-100",
      count: data?.stewardshipQueue.length ?? 0,
    },
    {
      label: "Identifier Issuance",
      description: "Operational visibility over provisional and canonical Impilo identifier progression.",
      Icon: CreditCard,
      tone: "bg-background text-foreground border-border",
      count: data?.verificationQueue.filter((item) => item.decision === "VERIFIED").length ?? 0,
    },
  ];

  return (
    <AppLayout>
      <PageShell
        title="Identity Operations"
        subtitle="Stewardship queues for duplicate review, verification, identifier issuance, and registry exceptions"
        serviceSlug="vito"
      >
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-border bg-card p-4">
            <div>
              <p className="text-sm font-medium text-foreground">Identity stewardship console</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Use this workspace to resolve verification uncertainty, confirm duplicates, and advance registry quality.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Link
                href="/operations/vito/biometrics"
                className="inline-flex items-center gap-2 rounded-xl border border-primary/25 bg-primary-soft px-4 py-2 text-sm font-medium text-impilo-900 hover:border-impilo-300"
              >
                Biometric governance
                <ArrowRight className="h-4 w-4" />
              </Link>
              <Link
                href="/registry/clients"
                className="inline-flex items-center gap-2 rounded-xl border border-border px-4 py-2 text-sm font-medium text-foreground hover:border-slate-400 hover:text-foreground"
              >
                Client registry
                <ArrowRight className="h-4 w-4" />
              </Link>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {sections.map(({ label, description, Icon, tone, count }) => (
              <div key={label} className={`rounded-2xl border bg-card p-5 ${tone}`}>
                <div className="flex items-center gap-3">
                  <div className="rounded-lg bg-card p-2">
                    <Icon className="h-5 w-5" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-foreground">{label}</h3>
                    <span className="rounded bg-card px-2 py-0.5 text-xs text-muted-foreground">{count} pending</span>
                  </div>
                </div>
                <p className="mt-3 text-sm text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>

          <div>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Operations Modules</h2>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
              {navLinks.map(({ label, description, href, Icon, tone }) => (
                <Link
                  key={href}
                  href={href}
                  className={`flex flex-col gap-2 rounded-2xl border p-4 transition-shadow hover:shadow-md ${tone}`}
                >
                  <div className="flex items-center gap-2">
                    <div className="rounded-lg bg-card p-1.5">
                      <Icon className="h-4 w-4" />
                    </div>
                    <span className="text-sm font-semibold text-foreground">{label}</span>
                  </div>
                  <p className="text-xs text-muted-foreground leading-relaxed">{description}</p>
                  <ArrowRight className="h-3.5 w-3.5 self-end opacity-50" />
                </Link>
              ))}
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-3">
            <div className="rounded-2xl border border-border bg-card p-5">
              <div className="flex items-center gap-2">
                <GitMerge className="h-4 w-4 text-amber-600" />
                <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Duplicate Review</h3>
              </div>
              <div className="mt-4 space-y-3">
                {data?.duplicateQueue.length ? (
                  data.duplicateQueue.slice(0, 6).map((item) => (
                    <Link key={item.matchId} href={`/registry/clients/${item.sourceHealthId}`} className="block rounded-2xl border border-border p-4 hover:border-slate-400">
                      <p className="font-medium text-foreground">
                        {item.sourceHealthId.slice(0, 8)} ↔ {item.candidateHealthId.slice(0, 8)}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Score {item.matchScore} • {labelize(item.status)}
                      </p>
                    </Link>
                  ))
                ) : (
                  <EmptyQueue label="No duplicate candidates are waiting right now." />
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-card p-5">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-4 w-4 text-sky-600" />
                <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Verification Queue</h3>
              </div>
              <div className="mt-4 space-y-3">
                {data?.verificationQueue.length ? (
                  data.verificationQueue.slice(0, 6).map((item) => (
                    <div key={item.reviewId} className="rounded-2xl border border-border p-4">
                      <p className="font-medium text-foreground">{labelize(item.reviewType)}</p>
                      <p className="mt-1 text-xs text-muted-foreground">{labelize(item.status)}</p>
                      <p className="mt-2 text-sm text-muted-foreground">{item.notes ?? "Verification work item"}</p>
                    </div>
                  ))
                ) : (
                  <EmptyQueue label="No verification reviews are open." />
                )}
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-card p-5">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-4 w-4 text-rose-600" />
                <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Stewardship Actions</h3>
              </div>
              <div className="mt-4 space-y-3">
                {data?.stewardshipQueue.length ? (
                  data.stewardshipQueue.slice(0, 6).map((item) => (
                    <div key={item.actionId} className="rounded-2xl border border-border p-4">
                      <p className="font-medium text-foreground">{labelize(item.actionType)}</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {labelize(item.status)} • {item.owner ?? "Unassigned"}
                      </p>
                      <p className="mt-2 text-sm text-muted-foreground">{item.completionNotes ?? "Operational follow-up required."}</p>
                    </div>
                  ))
                ) : (
                  <EmptyQueue label="No stewardship actions are open." />
                )}
              </div>
            </div>
          </div>

          <div>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">Identity Tools</h2>
            <div className="grid gap-4 lg:grid-cols-2">
              <IdentityResolutionPanel />
              <AliasRotationPanel />
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}

function EmptyQueue({ label }: { label: string }) {
  return <div className="rounded-2xl bg-background p-4 text-sm text-muted-foreground">{label}</div>;
}
