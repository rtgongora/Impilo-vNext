import Link from "next/link";
import {
  Smartphone,
  LogIn,
  ShieldCheck,
  BadgeCheck,
  Building2,
  LayoutGrid,
  ArrowRight,
  Clock,
  LifeBuoy,
  AlertTriangle,
} from "lucide-react";
import { PublicShell } from "@/components/public/PublicShell";

export const metadata = {
  title: "Get provider access",
  description:
    "How verified health workers get access in Impilo — install Impilo Provider, sign in, request Provider Access, verify your registration, confirm your facility context, and unlock your workspaces.",
};

const STEPS = [
  {
    icon: Smartphone,
    title: "Install Impilo Provider (or use the web)",
    body: "Get the Impilo Provider app — or use Impilo on the web while store listings are being prepared. Installing the app alone does not grant clinical access.",
  },
  {
    icon: LogIn,
    title: "Sign in as a person",
    body: "Sign in with your Impilo account. You are always a person first; your professional capacity is activated separately, only where it is verified.",
  },
  {
    icon: BadgeCheck,
    title: "Request Provider Access",
    body: "Start a provider access request and choose how to prove your registration — a claim token, your council or registration number, or supporting documents.",
  },
  {
    icon: ShieldCheck,
    title: "Registration is verified (Varapi)",
    body: "Your details are checked against the national provider register. This protects patients, providers and health information by confirming you are who you say you are.",
  },
  {
    icon: Building2,
    title: "Facility & employment context confirmed",
    body: "Your facility or employer confirms your working context (Tuso facility registry · Vashandi workforce). Access follows your real role and place of work.",
  },
  {
    icon: LayoutGrid,
    title: "Your workspaces unlock",
    body: "Once verified, the right professional workspaces open — for the right role, facility and purpose. Access can be pending or restricted until each step is complete.",
  },
];

/**
 * Provider onboarding explainer (public — guard: none).
 *
 * Honest journey map from "I downloaded the app" to "I have the right access",
 * with a clear entry into the REAL activation/claim flow. It never implies that
 * installing the app grants clinical access.
 */
export default function ProviderGetAccessPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        /{" "}
        <Link href="/download" className="hover:text-slate-900">
          Get the app
        </Link>{" "}
        / Provider access
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-700">
          <ShieldCheck className="h-4 w-4" aria-hidden="true" /> Verified access
        </span>
        <h1 className="mt-3 text-2xl font-bold text-slate-900">How health workers get access</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          <strong>Impilo Provider</strong> is for verified health workers and facilities. Downloading
          the app does not automatically grant access to clinical systems — professional and facility
          access is verified to protect patients, providers and health information. Here is the honest
          journey, end to end.
        </p>
      </section>

      {/* Journey steps */}
      <section className="mt-6 grid gap-4 md:grid-cols-2">
        {STEPS.map((step, i) => {
          const Icon = step.icon;
          return (
            <div key={step.title} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <div className="flex items-center gap-3">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-emerald-600 text-white">
                  <Icon className="h-5 w-5" aria-hidden="true" />
                </span>
                <div className="flex items-center gap-2">
                  <span className="grid h-5 w-5 place-items-center rounded-full bg-slate-100 text-[11px] font-bold text-slate-600">
                    {i + 1}
                  </span>
                  <h2 className="text-[15px] font-bold text-slate-900">{step.title}</h2>
                </div>
              </div>
              <p className="mt-3 text-[13.5px] leading-relaxed text-slate-600">{step.body}</p>
            </div>
          );
        })}
      </section>

      {/* Pending / restricted access */}
      <section className="mt-6 rounded-2xl border border-amber-200 bg-amber-50/70 p-6 shadow-sm">
        <h2 className="flex items-center gap-2 text-[15px] font-bold text-slate-900">
          <Clock className="h-4 w-4 text-amber-700" aria-hidden="true" /> While your access is pending
        </h2>
        <p className="mt-2 max-w-2xl text-[13.5px] leading-relaxed text-slate-600">
          Verification can take time, and some workspaces stay restricted until every check is
          complete. That is by design — access is granted for the right role, at the right facility,
          for the right purpose. You can still use your personal Impilo experience while you wait, and
          you will be notified as each step clears.
        </p>
        <div className="mt-3 flex items-center gap-2 text-[13px] text-slate-600">
          <AlertTriangle className="h-4 w-4 shrink-0 text-amber-700" aria-hidden="true" />
          If you believe your registration is missing or incorrect, contact your facility administrator
          or the Health Professions Authority.
        </div>
      </section>

      {/* Entry into the real flow */}
      <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
        <h2 className="text-lg font-bold text-slate-900">Ready to start?</h2>
        <p className="mt-2 max-w-2xl text-sm leading-relaxed text-slate-600">
          These take you into the real, verified flow. You will be asked to sign in first.
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <Link
            href="/provider/activate"
            className="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-emerald-700 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
          >
            Activate a Provider ID <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
          <Link
            href="/citizen/provider-claim"
            className="inline-flex items-center gap-2 rounded-full bg-white px-5 py-2.5 text-sm font-bold text-emerald-800 ring-1 ring-inset ring-emerald-500/30 transition-colors hover:bg-emerald-50 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
          >
            Claim or recover my provider profile
          </Link>
          <Link
            href="/download"
            className="inline-flex items-center gap-2 rounded-full px-4 py-2.5 text-sm font-semibold text-slate-600 hover:text-slate-900"
          >
            Get the app
          </Link>
        </div>
        <p className="mt-4 flex items-center gap-2 text-xs text-slate-500">
          <LifeBuoy className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" /> Need help? Contact
          your facility administrator or email{" "}
          <a href="mailto:support@impilo.io" className="font-semibold text-emerald-700 underline">
            support@impilo.io
          </a>
          .
        </p>
      </section>
    </PublicShell>
  );
}
