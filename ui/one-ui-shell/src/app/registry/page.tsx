"use client";

/**
 * Registry Hub — Cards for Providers, Facilities, Terminology, Products.
 * Route: /registry | pageTitle: "Registry Hub"
 */

import { useState } from "react";
import Link from "next/link";
import { UserCheck, Building2, BookOpen, Package, Route, ShieldCheck, HeartHandshake, MapPin, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { PageShell } from "@/components/PageShell";
import {
  useCreateProviderIdentity,
  useIdentitySearch,
  useProviderIdentity,
  useRegisterPatientIdentity,
  useResolvePatientIdentity,
  useStartPatientRecovery,
  useVerifyPatientRecovery,
} from "@/hooks/queries/useIdentityOperations";

type AnyRecord = Record<string, unknown>;

const inputClass = "mt-1 w-full rounded-md border border-border bg-card px-2 py-1.5 text-sm text-foreground";
const labelClass = "text-xs font-medium text-muted-foreground";
const buttonClass =
  "rounded-md border border-border bg-card px-2 py-1 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50";

const REGISTRY_SECTIONS = [
  {
    title: "Intake & onboarding",
    description: "Bootstrap snapshot, progressive intake sessions, recovery tickets, and governed CSV import",
    href: "/registry/intake",
    icon: Route,
    color: "bg-sky-100 text-sky-700",
  },
  {
    title: "Providers",
    description: "View and manage registered healthcare providers",
    href: "/registry/providers",
    icon: UserCheck,
    color: "bg-primary-soft text-primary",
    serviceSlug: "varapi",
  },
  {
    title: "Facilities",
    description: "Browse the facility registry and profiles",
    href: "/registry/facilities",
    icon: Building2,
    color: "bg-green-100 text-green-600",
    serviceSlug: "tuso",
  },
  {
    title: "Terminology",
    description: "Browse clinical terminology and coding systems",
    href: "/registry/terminology",
    icon: BookOpen,
    color: "bg-purple-100 text-purple-600",
    serviceSlug: "zibo",
  },
  {
    title: "ZIBO Studio",
    description: "Open national terminology curation (external ZIBO workspace)",
    href: process.env.NEXT_PUBLIC_ZIBO_URL ?? "https://zibo.impilo.health",
    icon: BookOpen,
    color: "bg-fuchsia-100 text-muted-foreground",
    external: true,
  },
  {
    title: "Products",
    description: "View registered pharmaceutical and medical products",
    href: "/registry/products",
    icon: Package,
    color: "bg-orange-100 text-orange-600",
  },
  {
    title: "Trust & Federation",
    description: "Registry trust controls, federation posture, and identity alignment",
    href: "/registry/trust",
    icon: ShieldCheck,
    color: "bg-indigo-100 text-primary-hover",
    serviceSlug: "tshepo",
  },
  {
    title: "Mvumo Consent",
    description: "Digital consent orchestration and consent-governance workflows",
    href: "/registry/mvumo",
    icon: HeartHandshake,
    color: "bg-pink-100 text-pink-700",
    serviceSlug: "mvumo",
  },
  {
    title: "Locality Review",
    description: "Gazetteer and locality normalization review for registry quality",
    href: "/registry/locality-review",
    icon: MapPin,
    color: "bg-teal-100 text-teal-700",
  },
  {
    title: "Facility Lifecycle",
    description: "Regulatory lifecycle view across facility onboarding and governance",
    href: "/registry/facility-lifecycle",
    icon: Building2,
    color: "bg-cyan-100 text-cyan-700",
  },
] as const;

export default function RegistryHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Registry Hub"
        subtitle="Central registries for providers, facilities, terminology, and products"
      >
        <IdentityOperationsPanel />

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {REGISTRY_SECTIONS.map((section) => {
            const Icon = section.icon;
            const external = "external" in section && section.external === true;
            return (
              <Link
                key={section.href}
                href={section.href}
                target={external ? "_blank" : undefined}
                rel={external ? "noopener noreferrer" : undefined}
                className="bg-card rounded-lg border border-border p-6 hover:border-primary/25 hover:shadow-md transition-all group"
              >
                <div className="flex items-start gap-4">
                  {"serviceSlug" in section && section.serviceSlug ? (
                    <ServiceLogo slug={section.serviceSlug} size="card" />
                  ) : (
                  <div
                    className={`w-12 h-12 rounded-lg ${section.color} flex items-center justify-center shrink-0`}
                  >
                    <Icon className="w-6 h-6" />
                  </div>
                  )}
                  <div>
                    <h3 className="font-medium text-foreground group-hover:text-primary transition-colors">
                      {section.title}
                    </h3>
                    <p className="text-sm text-muted-foreground mt-1">{section.description}</p>
                  </div>
                </div>
              </Link>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function fieldValue(record: AnyRecord, keys: string[], fallback: string) {
  for (const key of keys) {
    const value = record[key];
    if (typeof value === "string" && value.trim()) return value;
    if (typeof value === "number") return String(value);
  }
  return fallback;
}

function compactRecord(record: Record<string, string>) {
  return Object.fromEntries(
    Object.entries(record)
      .map(([key, value]) => [key, value.trim()])
      .filter(([, value]) => value.length > 0),
  );
}

function missingFields(record: Record<string, string>, fields: string[]) {
  return fields.filter((field) => !record[field]?.trim());
}

function CompactJsonResult({ value }: { value: unknown }) {
  if (!value) return null;
  const entries = Object.entries((value as { data?: unknown })?.data ?? value)
    .filter(([, entryValue]) => entryValue == null || typeof entryValue !== "object")
    .slice(0, 6);

  if (entries.length === 0) {
    return <p className="mt-2 text-xs text-muted-foreground">Command accepted. Response contains structured data.</p>;
  }

  return (
    <dl className="mt-2 grid gap-2 sm:grid-cols-2">
      {entries.map(([key, entryValue]) => (
        <div key={key} className="rounded-md border border-emerald-100 bg-success-soft p-2">
          <dt className="text-[10px] uppercase tracking-wide text-primary-hover">{key}</dt>
          <dd className="mt-0.5 break-words text-xs font-medium text-foreground">{String(entryValue)}</dd>
        </div>
      ))}
    </dl>
  );
}

function IdentityOperationsPanel() {
  const [searchInput, setSearchInput] = useState("");
  const [submittedSearch, setSubmittedSearch] = useState("");
  const [resolveHealthId, setResolveHealthId] = useState("");
  const [providerLookupId, setProviderLookupId] = useState("");
  const [submittedProviderLookupId, setSubmittedProviderLookupId] = useState("");
  const [patientForm, setPatientForm] = useState({
    givenName: "Example",
    familyName: "Client",
    dateOfBirth: "1990-01-01",
    province: "HA",
    phone: "+263000000000",
    idType: "patient",
  });
  const [providerForm, setProviderForm] = useState({
    title: "Dr",
    givenName: "Example",
    familyName: "Provider",
    profession: "GENERAL_PRACTITIONER",
    cadre: "CLINICIAN",
    nationality: "ZW",
    gender: "UNKNOWN",
    practiceNumber: "",
  });
  const [recoveryStartForm, setRecoveryStartForm] = useState({
    method: "phone_otp",
    contact: "+263000000000",
    fullName: "Example Client",
  });
  const [recoveryVerifyForm, setRecoveryVerifyForm] = useState({
    recoveryId: "RECOVERY-ID",
    otp: "000000",
  });
  const [patientFeedback, setPatientFeedback] = useState<string | null>(null);
  const [providerFeedback, setProviderFeedback] = useState<string | null>(null);
  const [recoveryFeedback, setRecoveryFeedback] = useState<string | null>(null);

  const search = useIdentitySearch(submittedSearch);
  const providerLookup = useProviderIdentity(submittedProviderLookupId);
  const resolvePatient = useResolvePatientIdentity();
  const registerPatient = useRegisterPatientIdentity();
  const createProvider = useCreateProviderIdentity();
  const startRecovery = useStartPatientRecovery();
  const verifyRecovery = useVerifyPatientRecovery();

  function submitPatientRegistration() {
    setPatientFeedback(null);
    const missing = missingFields(patientForm, ["givenName", "familyName", "dateOfBirth", "idType"]);
    if (missing.length > 0) {
      setPatientFeedback(`Missing required field(s): ${missing.join(", ")}.`);
      return;
    }
    registerPatient.mutate(compactRecord(patientForm), {
      onSuccess: () => setPatientFeedback("Patient identity registration accepted. This command is live-only and not queued offline."),
      onError: () => setPatientFeedback("Patient identity registration failed. Verify fields and trust context."),
    });
  }

  function submitProviderCreation() {
    setProviderFeedback(null);
    const missing = missingFields(providerForm, ["givenName", "familyName", "profession", "cadre", "nationality"]);
    if (missing.length > 0) {
      setProviderFeedback(`Missing required field(s): ${missing.join(", ")}.`);
      return;
    }
    createProvider.mutate(compactRecord(providerForm), {
      onSuccess: () => setProviderFeedback("Provider identity creation accepted. This command is live-only and not queued offline."),
      onError: () => setProviderFeedback("Provider identity creation failed. Verify fields and trust context."),
    });
  }

  function submitRecoveryStart() {
    setRecoveryFeedback(null);
    const missing = missingFields(recoveryStartForm, ["method", "contact", "fullName"]);
    if (missing.length > 0) {
      setRecoveryFeedback(`Missing required field(s): ${missing.join(", ")}.`);
      return;
    }
    startRecovery.mutate(compactRecord(recoveryStartForm), {
      onSuccess: () => setRecoveryFeedback("Patient recovery start accepted. Recovery is live-only; no offline replay is stored."),
      onError: () => setRecoveryFeedback("Patient recovery start failed. Verify fields and trust context."),
    });
  }

  function submitRecoveryVerify() {
    setRecoveryFeedback(null);
    const missing = missingFields(recoveryVerifyForm, ["recoveryId", "otp"]);
    if (missing.length > 0) {
      setRecoveryFeedback(`Missing required field(s): ${missing.join(", ")}.`);
      return;
    }
    verifyRecovery.mutate(compactRecord(recoveryVerifyForm), {
      onSuccess: () => setRecoveryFeedback("Patient recovery verification accepted. Verification is live-only; no offline replay is stored."),
      onError: () => setRecoveryFeedback("Patient recovery verification failed. Verify fields and trust context."),
    });
  }

  return (
    <section className="mb-6 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Registry plane</p>
          <h2 className="mt-1 text-lg font-semibold text-foreground">Identity Operations</h2>
          <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
            Live BFF-backed controls for VITO patient identity and VARAPI provider identity. Facility identity
            creation remains intentionally unsupported here until a canonical Experience contract exists.
          </p>
        </div>
        <span className="rounded-full border border-success/25 bg-success-soft px-3 py-1 text-xs font-medium text-primary-hover">
          actionable partial
        </span>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <div className="rounded-xl border border-border p-4">
          <h3 className="text-sm font-semibold text-foreground">Search and resolve identities</h3>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            <label className="text-xs text-muted-foreground">
              Identity search
              <div className="mt-1 flex gap-2">
                <input
                  className={inputClass}
                  value={searchInput}
                  onChange={(event) => setSearchInput(event.target.value)}
                  placeholder="Name, PHID, phone"
                />
                <button
                  type="button"
                  className={buttonClass}
                  onClick={() => setSubmittedSearch(searchInput)}
                  disabled={searchInput.trim().length < 2}
                >
                  <Search className="h-4 w-4" />
                </button>
              </div>
            </label>
            <label className="text-xs text-muted-foreground">
              Resolve patient Health ID
              <input
                className={inputClass}
                value={resolveHealthId}
                onChange={(event) => setResolveHealthId(event.target.value)}
                placeholder="Health ID / PHID"
              />
            </label>
          </div>
          <button
            type="button"
            className={`mt-3 ${buttonClass}`}
            onClick={() => resolvePatient.mutate(resolveHealthId.trim())}
            disabled={resolvePatient.isPending || !resolveHealthId.trim()}
          >
            {resolvePatient.isPending ? "Resolving..." : "Resolve patient identity"}
          </button>
          <div className="mt-3 space-y-2">
            {search.isLoading ? <p className="text-xs text-muted-foreground">Searching identities...</p> : null}
            {search.isError ? <p className="text-xs text-danger">Identity search unavailable.</p> : null}
            {search.items.slice(0, 4).map((item, index) => {
              const id = fieldValue(item, ["healthId", "id", "cpid", "providerId"], `identity-${index + 1}`);
              return (
                <div key={`${id}-${index}`} className="rounded-md border border-border bg-background p-2">
                  <p className="text-xs font-medium text-foreground">{id}</p>
                  <p className="text-xs text-muted-foreground">
                    {fieldValue(item, ["displayName", "name", "fullName", "givenName"], "Registry identity")}
                  </p>
                </div>
              );
            })}
            <CompactJsonResult value={resolvePatient.data} />
            {resolvePatient.isError ? <p className="text-xs text-danger">Patient identity resolution failed.</p> : null}
          </div>
        </div>

        <div className="rounded-xl border border-border p-4">
          <h3 className="text-sm font-semibold text-foreground">Patient registration command</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Guided live command to <code>/internal/v1/identity/patient/register</code>. Identity registration is not
            provisionally queued because replay can create high-trust identity conflicts.
          </p>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            {[
              ["givenName", "Given name"],
              ["familyName", "Family name"],
              ["dateOfBirth", "Date of birth"],
              ["province", "Province"],
              ["phone", "Phone"],
              ["idType", "ID type"],
            ].map(([key, label]) => (
              <label key={key} className={labelClass}>
                {label}
                <input
                  className={inputClass}
                  value={patientForm[key as keyof typeof patientForm]}
                  onChange={(event) => setPatientForm({ ...patientForm, [key]: event.target.value })}
                  placeholder={label}
                />
              </label>
            ))}
          </div>
          <button
            type="button"
            className={`mt-3 ${buttonClass}`}
            onClick={submitPatientRegistration}
            disabled={registerPatient.isPending}
          >
            {registerPatient.isPending ? "Registering..." : "Register patient identity"}
          </button>
          {patientFeedback ? <p className="mt-2 text-xs text-foreground">{patientFeedback}</p> : null}
          <CompactJsonResult value={registerPatient.data} />
        </div>

        <div className="rounded-xl border border-border p-4">
          <h3 className="text-sm font-semibold text-foreground">Provider identity operations</h3>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            <label className="text-xs text-muted-foreground">
              Provider lookup ID
              <input
                className={inputClass}
                value={providerLookupId}
                onChange={(event) => setProviderLookupId(event.target.value)}
                placeholder="Provider ID"
              />
            </label>
            <div className="flex items-end">
              <button
                type="button"
                className={buttonClass}
                onClick={() => setSubmittedProviderLookupId(providerLookupId)}
                disabled={!providerLookupId.trim()}
              >
                Lookup provider
              </button>
            </div>
          </div>
          <p className="mt-3 text-xs text-muted-foreground">
            Guided provider command uses <code>/internal/v1/identity/provider/create</code>. Facility identity and
            lifecycle work remains under the dedicated facility registry routes.
          </p>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            {[
              ["title", "Title"],
              ["givenName", "Given name"],
              ["familyName", "Family name"],
              ["profession", "Profession"],
              ["cadre", "Cadre"],
              ["nationality", "Nationality"],
              ["gender", "Gender"],
              ["practiceNumber", "Practice number"],
            ].map(([key, label]) => (
              <label key={key} className={labelClass}>
                {label}
                <input
                  className={inputClass}
                  value={providerForm[key as keyof typeof providerForm]}
                  onChange={(event) => setProviderForm({ ...providerForm, [key]: event.target.value })}
                  placeholder={label}
                />
              </label>
            ))}
          </div>
          <button
            type="button"
            className={`mt-3 ${buttonClass}`}
            onClick={submitProviderCreation}
            disabled={createProvider.isPending}
          >
            {createProvider.isPending ? "Creating..." : "Create provider identity"}
          </button>
          {providerFeedback ? <p className="mt-2 text-xs text-foreground">{providerFeedback}</p> : null}
          {providerLookup.isLoading ? <p className="mt-2 text-xs text-muted-foreground">Loading provider...</p> : null}
          {providerLookup.isError ? <p className="mt-2 text-xs text-danger">Provider lookup failed.</p> : null}
          <CompactJsonResult value={providerLookup.data ?? createProvider.data} />
        </div>

        <div className="rounded-xl border border-border p-4">
          <h3 className="text-sm font-semibold text-foreground">Patient recovery commands</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            Patient recovery is live; provider recovery stays explicitly unsupported until VARAPI exposes a contract.
            Recovery start and verification are not queued offline because OTP/recovery replay must remain synchronous.
          </p>
          <div className="mt-3 grid gap-3 md:grid-cols-3">
            {[
              ["method", "Method"],
              ["contact", "Contact"],
              ["fullName", "Full name"],
            ].map(([key, label]) => (
              <label key={key} className={labelClass}>
                {label}
                <input
                  className={inputClass}
                  value={recoveryStartForm[key as keyof typeof recoveryStartForm]}
                  onChange={(event) => setRecoveryStartForm({ ...recoveryStartForm, [key]: event.target.value })}
                  placeholder={label}
                />
              </label>
            ))}
          </div>
          <button
            type="button"
            className={`mt-3 ${buttonClass}`}
            onClick={submitRecoveryStart}
            disabled={startRecovery.isPending}
          >
            {startRecovery.isPending ? "Starting..." : "Start patient recovery"}
          </button>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            {[
              ["recoveryId", "Recovery ID"],
              ["otp", "OTP"],
            ].map(([key, label]) => (
              <label key={key} className={labelClass}>
                {label}
                <input
                  className={inputClass}
                  value={recoveryVerifyForm[key as keyof typeof recoveryVerifyForm]}
                  onChange={(event) => setRecoveryVerifyForm({ ...recoveryVerifyForm, [key]: event.target.value })}
                  placeholder={label}
                />
              </label>
            ))}
          </div>
          <button
            type="button"
            className={`mt-3 ${buttonClass}`}
            onClick={submitRecoveryVerify}
            disabled={verifyRecovery.isPending}
          >
            {verifyRecovery.isPending ? "Verifying..." : "Verify patient recovery"}
          </button>
          {recoveryFeedback ? <p className="mt-2 text-xs text-foreground">{recoveryFeedback}</p> : null}
          <CompactJsonResult value={verifyRecovery.data ?? startRecovery.data} />
        </div>
      </div>
    </section>
  );
}
