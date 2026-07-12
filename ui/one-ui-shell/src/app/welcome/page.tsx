import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { IntentLink } from "@/components/public/IntentLink";
import type { GatewayPillar } from "@/lib/gateway-intent";

export const metadata = {
  title: "Welcome to Impilo — Zimbabwe's Health Operating System",
  description:
    "Discover Impilo, find care, get emergency information, and create an account or request a Health ID. No Health ID is needed to get started.",
};

/**
 * Public L0 landing (G-CZO-02) and the gateway intent home ("How can we help you
 * today?"). Reachable WITHOUT login. Explains what Impilo is, routes each of the nine
 * service intents honestly (open now / sign in to continue / coming soon), and never
 * makes authenticated calls or shows personal/health data.
 */
const INTENT_PILLARS: Array<{
  title: string;
  description: string;
  href: string;
  access: "open" | "sign-in" | "coming";
  tone?: "emergency";
  /** Present on sign-in cards: journey context carried across authentication. */
  intent?: { pillar: GatewayPillar; goal: string; dest: string };
}> = [
  {
    title: "Get care",
    description: "Find a facility near you, learn how to visit, and how booking works.",
    href: "/welcome/find-care",
    access: "open",
  },
  {
    title: "Emergency help",
    description: "Emergency numbers and what to do right now. Never blocked by sign-in.",
    href: "/welcome/emergency",
    access: "open",
    tone: "emergency",
  },
  {
    title: "Find or verify a service",
    description: "Search the national facility directory or verify a facility certificate.",
    href: "/welcome/find-care",
    access: "open",
  },
  {
    title: "My health",
    description: "Your records, results, appointments and consent — protected behind sign-in.",
    href: "/auth/login?returnTo=%2Fhome",
    access: "sign-in",
    intent: { pillar: "my-health", goal: "view-records", dest: "/home" },
  },
  {
    title: "Health information",
    description:
      "Trusted health knowledge and prevention guidance is being added. Emergency and public-health notices are available today.",
    href: "/welcome/emergency",
    access: "coming",
  },
  {
    title: "Health cover & payments",
    description: "Check your cover, view bills and receipts, and pay for care.",
    href: "/auth/login?returnTo=%2Fcoverage",
    access: "sign-in",
    intent: { pillar: "cover-and-payments", goal: "view-coverage", dest: "/coverage" },
  },
  {
    title: "Applications & licensing",
    description: "Professional registration, facility licensing, renewals and tracking.",
    href: "/auth/login",
    access: "sign-in",
    intent: { pillar: "applications-licensing", goal: "start-application", dest: "/home" },
  },
  {
    title: "Feedback & complaints",
    description: "Compliment, complain or raise a safety concern, and track the outcome.",
    href: "/auth/login?returnTo=%2Fmy-life%2Ffeedback",
    access: "sign-in",
    intent: { pillar: "feedback-complaints", goal: "give-feedback", dest: "/my-life/feedback" },
  },
  {
    title: "Health products & suppliers",
    description: "Find pharmacies and health products, order and track delivery.",
    href: "/auth/login?returnTo=%2Fmarketplace",
    access: "sign-in",
    intent: { pillar: "products-suppliers", goal: "browse-products", dest: "/marketplace" },
  },
];

export default function WelcomePage() {
  return (
    <PublicShell>
      <section className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <p className="text-sm font-medium uppercase tracking-wide text-emerald-700">
          Ministry of Health &amp; Child Care
        </p>
        <h1 className="mt-2 text-3xl font-bold text-slate-900 sm:text-4xl">
          Your health, connected and protected.
        </h1>
        <p className="mt-4 max-w-2xl text-lg text-slate-600">
          Impilo is Zimbabwe&apos;s national Health Operating System. Create an account to request a
          Health ID, receive care, view your records, and manage who can see them — with strong
          privacy and your consent at every step.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href="/auth/register"
            className="rounded-lg bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700"
          >
            Create an account
          </Link>
          <Link
            href="/auth/login"
            className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-slate-800 hover:bg-slate-50"
          >
            Sign in
          </Link>
          <Link
            href="/auth/register?intent=health-id"
            className="rounded-lg border border-emerald-300 bg-emerald-50 px-5 py-2.5 font-semibold text-emerald-800 hover:bg-emerald-100"
          >
            Request a Health ID
          </Link>
        </div>
        <p className="mt-3 text-sm text-slate-500">
          You do <strong>not</strong> need a Health ID to get started — create an account first, then
          request one.
        </p>
      </section>

      <section className="mt-8 grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">1 · Account</h2>
          <p className="mt-1 text-sm text-slate-600">
            Start an account in minutes. Begin or continue a Health ID request and get help — no
            private records yet.
          </p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">2 · Temporary Health ID</h2>
          <p className="mt-1 text-sm text-slate-600">
            Receive care, show your ID at a facility, and book selected services while your identity
            is being verified. Sensitive records stay protected.
          </p>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold text-slate-900">3 · Verified Health ID</h2>
          <p className="mt-1 text-sm text-slate-600">
            Once verified, see your health summary, appointments, results, prescriptions and care
            plans — and control who can access them.
          </p>
        </div>
      </section>

      <section className="mt-10" aria-labelledby="intent-heading">
        <h2 id="intent-heading" className="text-2xl font-bold text-slate-900">
          How can we help you today?
        </h2>
        <p className="mt-1 text-sm text-slate-600">
          Start with what you need. Many services are available without signing in; the rest tell
          you exactly what they require.
        </p>
        <div className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {INTENT_PILLARS.map((pillar) => {
            const cardClass = `group rounded-xl border bg-white p-5 transition ${
              pillar.tone === "emergency"
                ? "border-red-200 hover:border-red-300 hover:bg-red-50/40"
                : "border-slate-200 hover:border-emerald-300 hover:bg-emerald-50/40"
            }`;
            const inner = (
              <>
                <div className="flex items-start justify-between gap-2">
                  <h3
                    className={`font-semibold ${
                      pillar.tone === "emergency"
                        ? "text-red-700"
                        : "text-slate-900 group-hover:text-emerald-800"
                    }`}
                  >
                    {pillar.title}
                  </h3>
                  <span
                    className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-medium ${
                      pillar.access === "open"
                        ? "bg-emerald-50 text-emerald-700"
                        : pillar.access === "sign-in"
                          ? "bg-slate-100 text-slate-600"
                          : "bg-amber-50 text-amber-700"
                    }`}
                  >
                    {pillar.access === "open"
                      ? "No sign-in needed"
                      : pillar.access === "sign-in"
                        ? "Sign in to continue"
                        : "Coming soon"}
                  </span>
                </div>
                <p className="mt-1 text-sm text-slate-600">{pillar.description}</p>
              </>
            );
            return pillar.intent ? (
              <IntentLink
                key={pillar.title}
                pillar={pillar.intent.pillar}
                goal={pillar.intent.goal}
                dest={pillar.intent.dest}
                from="/welcome"
                href={pillar.href}
                className={cardClass}
              >
                {inner}
              </IntentLink>
            ) : (
              <Link key={pillar.title} href={pillar.href} className={cardClass}>
                {inner}
              </Link>
            );
          })}
        </div>
      </section>

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Built for everyone</h2>
        <p className="mt-1 text-sm text-slate-600">
          Impilo is designed to be usable on low-cost phones and shared devices, with accessibility
          and language options. A health worker can also help you register at a facility if you have
          no smartphone.
        </p>
        <Link
          href="/welcome/accessibility"
          className="mt-3 inline-block text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          Accessibility &amp; language options →
        </Link>
      </section>
    </PublicShell>
  );
}
