import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { WelcomeHero } from "@/components/public/WelcomeHero";
import { IntentLink } from "@/components/public/IntentLink";
import { GatewayEscalationExplainer } from "@/components/intelligent/GatewayEscalationExplainer";
import type { GatewayPillar } from "@/lib/gateway-intent";

export const metadata = {
  title: "Welcome to Impilo — Zimbabwe's Health Operating System",
  description:
    "Discover Impilo, find care, get emergency information, and create an account or request an Impilo ID. No Impilo ID is needed to get started.",
};

/**
 * Public L0 landing (G-CZO-02) and the gateway intent home ("How can we help you
 * today?"). Reachable WITHOUT login. Explains what Impilo is, routes the nine intent
 * pillars honestly (open now / sign in to continue / coming soon; cards may share or
 * split a pillar — e.g. incident reporting spans emergency-help and feedback-complaints),
 * and never makes authenticated calls or shows personal/health data.
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
    title: "Report a health incident",
    description:
      "Report an emergency, unsafe care, a safety concern or a complaint — we'll route you to the right place.",
    href: "/welcome/report",
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
      "Trusted, plain-language health knowledge — when to seek care, child health, vaccinations, chronic conditions, medicines safety and prevention.",
    href: "/welcome/health-info",
    access: "open",
  },
  {
    title: "Notices & bulletins",
    description:
      "Official public notices — health campaigns, product and safety recalls, disaster alerts, regulatory notices and procurement opportunities.",
    href: "/welcome/notices",
    access: "open",
  },
  {
    title: "Registration & licensing",
    description:
      "How to register a health professional or license a facility — application routes, requirements, fees and councils. Read freely; sign in to apply.",
    href: "/welcome/regulatory",
    access: "open",
  },
  {
    title: "Get involved",
    description:
      "Impilo is growing — with you. Share an idea, tell us your experience, or help test what comes next.",
    href: "/get-involved",
    access: "open",
  },
  {
    title: "Health cover & payments",
    description:
      "Compare medical-aid and insurance plans and their benefits. Sign in only for your own cover, bills and claims.",
    href: "/welcome/coverage",
    access: "open",
  },
  {
    title: "Wellness",
    description:
      "Screening and prevention schedules and one-off health checks (BMI, blood pressure). Sign in only to track progress.",
    href: "/welcome/wellness",
    access: "open",
  },
  {
    title: "Learning",
    description:
      "Free citizen health courses, caregiver education and first aid. Sign in only for certificates and CPD.",
    href: "/welcome/learning",
    access: "open",
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
    description:
      "Browse medicines, health products, services and approved suppliers with indicative prices. Sign in only to order or pay.",
    href: "/welcome/marketplace",
    access: "open",
  },
];

/**
 * The three-step trust ladder (Account → Temporary → Verified). Colour graduates
 * emerald → teal → sky to read as forward progress. Class strings are written in
 * full (not interpolated) so Tailwind's JIT scanner keeps them.
 */
const STEPS: Array<{
  n: number;
  title: string;
  body: string;
  card: string;
  badge: string;
  heading: string;
}> = [
  {
    n: 1,
    title: "Account",
    body: "Start an account in minutes. Begin or continue an Impilo ID request and get help — no private records yet.",
    card: "border-emerald-200 bg-emerald-50/50",
    badge: "bg-emerald-600",
    heading: "text-emerald-900",
  },
  {
    n: 2,
    title: "Temporary Impilo ID",
    body: "Receive care, show your ID at a facility, and book selected services while your identity is being verified. Sensitive records stay protected.",
    card: "border-teal-200 bg-teal-50/50",
    badge: "bg-teal-600",
    heading: "text-teal-900",
  },
  {
    n: 3,
    title: "Verified Impilo ID",
    body: "Once verified, see your health summary, appointments, results, prescriptions and care plans — and control who can access them.",
    card: "border-sky-200 bg-sky-50/50",
    badge: "bg-sky-600",
    heading: "text-sky-900",
  },
];

export default function WelcomePage() {
  return (
    <PublicShell>
      <WelcomeHero />

      <section className="mt-8 grid gap-4 sm:grid-cols-3">
        {STEPS.map((step) => (
          <div key={step.n} className={`rounded-xl border p-5 ${step.card}`}>
            <div className="flex items-center gap-2.5">
              <span
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white ${step.badge}`}
              >
                {step.n}
              </span>
              <h2 className={`font-semibold ${step.heading}`}>{step.title}</h2>
            </div>
            <p className="mt-2 text-sm text-slate-600">{step.body}</p>
          </div>
        ))}
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
            const cardClass = `group rounded-xl border p-5 transition ${
              pillar.tone === "emergency"
                ? "border-red-200 bg-red-50/40 hover:border-red-300 hover:bg-red-50/70"
                : "border-slate-200 bg-white hover:border-emerald-300 hover:bg-emerald-50/40"
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
        <div className="mt-6 max-w-2xl">
          <GatewayEscalationExplainer stepKey="signin-to-personal" continueWithoutHref={null} />
        </div>
      </section>

      <section className="mt-8 rounded-xl border border-emerald-100 bg-emerald-50/50 p-6">
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
