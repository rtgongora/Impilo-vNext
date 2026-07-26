import Link from "next/link";
import {
  BadgeCheck,
  BookOpenCheck,
  BriefcaseMedical,
  Building2,
  GraduationCap,
  HeartHandshake,
  HeartPulse,
  Megaphone,
  MessageSquareHeart,
  PackageSearch,
  ShieldCheck,
  Siren,
  Smartphone,
  Sparkles,
  Stethoscope,
  UserRound,
  WalletCards,
} from "lucide-react";
import { PublicShell } from "./PublicShell";
import { WelcomeHero } from "./WelcomeHero";
import {
  AdaptiveServiceLauncher,
  type PublicServiceAction,
} from "./AdaptiveServiceLauncher";
import { IntentLink } from "./IntentLink";

const IMMEDIATE_ACTIONS: PublicServiceAction[] = [
  {
    title: "Get Health Services",
    description:
      "Tell Impilo what care you need, optionally share your location, and compare nearby facilities.",
    href: "/welcome/find-care",
    icon: HeartHandshake,
    access: "public",
    emphasis: "primary",
  },
  {
    title: "Emergency Help",
    description:
      "See immediate call actions, danger-sign guidance and guest emergency assistance.",
    href: "/welcome/emergency",
    icon: Siren,
    access: "public",
    emphasis: "emergency",
  },
  {
    title: "Find or verify",
    description:
      "Search facilities or confirm a public practitioner registration without exposing private records.",
    href: "/verify/practitioner",
    icon: BadgeCheck,
    access: "public",
  },
  {
    title: "Feedback and safety",
    description:
      "Give feedback, report unsafe care anonymously, and receive a claim code for status updates.",
    href: "/welcome/report",
    icon: MessageSquareHeart,
    access: "public",
  },
];

// Whole-health category launcher (landing concept). Every card routes to a REAL
// existing public explorer — no dead-ends. Nutrition/exercise live under Wellness;
// equipment under Medicines & products; provider discovery is honest verification.
const CATEGORY_LAUNCHER: PublicServiceAction[] = [
  { title: "Find care", description: "Facilities, clinics and services near you or online.", href: "/welcome/find-care", icon: HeartHandshake, access: "public" },
  { title: "Providers", description: "Verify a practitioner is registered and licensed.", href: "/verify/practitioner", icon: Stethoscope, access: "public" },
  { title: "Medicines & products", description: "Pharmacies, approved suppliers and health goods.", href: "/welcome/marketplace", icon: PackageSearch, access: "public" },
  { title: "Diagnostics", description: "Where to get lab tests, imaging and screening.", href: "/welcome/find-care", icon: HeartPulse, access: "public" },
  { title: "Wellness", description: "Screening, healthy living and wellbeing support.", href: "/welcome/wellness", icon: Sparkles, access: "public" },
  { title: "Health cover", description: "Compare medical-aid and insurance plans.", href: "/welcome/coverage", icon: WalletCards, access: "public" },
  { title: "Learn", description: "Trusted health courses and public information.", href: "/welcome/learning", icon: GraduationCap, access: "public" },
  { title: "Get involved", description: "Shape Impilo — ideas, consultations and communities.", href: "/get-involved", icon: Megaphone, access: "public" },
];

const OPEN_DISCOVERY: PublicServiceAction[] = [
  {
    title: "Health information",
    description: "Trusted, plain-language health guidance and danger-sign callouts.",
    href: "/welcome/health-info",
    icon: BookOpenCheck,
    access: "public",
  },
  {
    title: "Notices and bulletins",
    description: "Published public-health, safety, disaster and regulatory notices.",
    href: "/welcome/notices",
    icon: Megaphone,
    access: "public",
  },
  {
    title: "Health cover",
    description: "Compare published medical-aid and insurance plans and benefits.",
    href: "/welcome/coverage",
    icon: WalletCards,
    access: "public",
  },
  {
    title: "Wellness",
    description: "Screening schedules and private, one-off BMI or blood-pressure checks.",
    href: "/welcome/wellness",
    icon: HeartPulse,
    access: "public",
  },
  {
    title: "Learning",
    description: "Browse citizen, caregiver and first-aid courses before enrolment.",
    href: "/welcome/learning",
    icon: GraduationCap,
    access: "public",
  },
  {
    title: "Products and suppliers",
    description: "Browse published health products, services and approved suppliers.",
    href: "/welcome/marketplace",
    icon: PackageSearch,
    access: "public",
  },
];

const TRUST_STEPS = [
  {
    title: "Start publicly",
    body: "Find care, get emergency help, browse information, verify services and give feedback without an account.",
    icon: Sparkles,
  },
  {
    title: "Sign in when it adds value",
    body: "Impilo explains why identity is needed and keeps the public journey you already started.",
    icon: UserRound,
  },
  {
    title: "Unlock protected services",
    body: "Verified identity and TSHEPO authority unlock only the personal or professional context you are permitted to use.",
    icon: ShieldCheck,
  },
] as const;

export function PublicLanding() {
  return (
    <PublicShell>
      <WelcomeHero />

      <section className="mt-12" aria-labelledby="categories-title">
        <div className="max-w-3xl">
          <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
            Explore all of Impilo
          </p>
          <h2 id="categories-title" className="mt-2 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">
            Find whatever helps you get well and stay well
          </h2>
          <p className="mt-3 text-base leading-7 text-slate-600">
            Care, providers, medicines, diagnostics, wellness and cover — one place, and you can look
            without signing in.
          </p>
        </div>
        {/* The launcher's own container queries can't resolve their own width here,
            so drive the columns from the viewport for this full-width section. */}
        <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 [&_.public-service-launcher]:contents">
          <AdaptiveServiceLauncher actions={CATEGORY_LAUNCHER} compact />
        </div>
      </section>

      <section id="services" className="mt-14 scroll-mt-28" aria-labelledby="services-title">
        <div className="max-w-3xl">
          <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
            Help available now
          </p>
          <h2 id="services-title" className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            Start with the need, not the login screen
          </h2>
          <p className="mt-3 text-base leading-7 text-slate-600">
            These journeys use live public services. They do not require an Impilo ID, and they do
            not expose personal health information.
          </p>
        </div>
        <div className="mt-6">
          <AdaptiveServiceLauncher actions={IMMEDIATE_ACTIONS} />
        </div>
      </section>

      <section
        className="mt-14 grid gap-8 rounded-[2rem] border border-slate-200 bg-slate-50/70 p-6 sm:p-8 lg:grid-cols-[minmax(0,.75fr)_minmax(0,1.25fr)] lg:p-10"
        aria-labelledby="explore-title"
      >
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
            Explore freely
          </p>
          <h2 id="explore-title" className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            Public knowledge and services in one place
          </h2>
          <p className="mt-3 text-base leading-7 text-slate-600">
            Browse first. Impilo introduces sign-in only for saved history, personalised records,
            transactions, certificates or authority-bound work.
          </p>
          <div className="mt-6 rounded-2xl border border-emerald-100 bg-white p-5">
            <Stethoscope className="h-6 w-6 text-emerald-700" aria-hidden />
            <h3 className="mt-3 font-semibold text-slate-950">Need help choosing?</h3>
            <p className="mt-1 text-sm leading-6 text-slate-600">
              Ask Nompilo in the living canvas above. Nompilo gives general guidance and routes you
              to a real public journey; it does not impersonate a clinician.
            </p>
            <a
              href="#living-canvas-title"
              className="mt-3 inline-flex min-h-10 items-center text-sm font-semibold text-emerald-700 hover:text-emerald-900"
            >
              Ask Nompilo
            </a>
          </div>
        </div>
        <AdaptiveServiceLauncher actions={OPEN_DISCOVERY} compact />
      </section>

      <section
        className="mt-14 grid gap-6 lg:grid-cols-2"
        aria-label="Personal and professional Impilo access"
      >
        <article
          id="my-impilo"
          className="rounded-[2rem] border border-sky-200 bg-gradient-to-br from-sky-50 to-white p-7 sm:p-8"
        >
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-sky-700 text-white">
            <UserRound className="h-6 w-6" aria-hidden />
          </span>
          <p className="mt-5 text-sm font-semibold uppercase tracking-[0.08em] text-sky-800">
            My Impilo
          </p>
          <h2 className="mt-2 text-2xl font-bold text-slate-950">
            Your protected health life, when you are ready
          </h2>
          <p className="mt-3 text-sm leading-6 text-slate-700">
            Sign in for records, results, prescriptions, appointments, referrals, dependants,
            coverage, payments, saved services and Khuluma follow-up.
          </p>
          <p className="mt-3 text-sm font-medium text-sky-900">
            Why sign in? These services work with information that belongs to you personally.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <IntentLink
              pillar="my-health"
              goal="open-my-impilo"
              dest="/home"
              from="/"
              href="/auth/login?returnTo=%2Fhome"
              className="inline-flex min-h-11 items-center rounded-xl bg-sky-800 px-5 py-2.5 text-sm font-semibold text-white hover:bg-sky-900"
            >
              Sign in to My Impilo
            </IntentLink>
            <Link
              href="/auth/register/contact"
              className="inline-flex min-h-11 items-center rounded-xl border border-sky-300 bg-white px-5 py-2.5 text-sm font-semibold text-sky-900 hover:bg-sky-50"
            >
              Create account
            </Link>
          </div>
        </article>

        <article
          id="work-on-impilo"
          className="rounded-[2rem] border border-amber-200 bg-gradient-to-br from-amber-50 to-white p-7 sm:p-8"
        >
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-amber-700 text-white">
            <BriefcaseMedical className="h-6 w-6" aria-hidden />
          </span>
          <p className="mt-5 text-sm font-semibold uppercase tracking-[0.08em] text-amber-800">
            Work on Impilo
          </p>
          <h2 className="mt-2 text-2xl font-bold text-slate-950">
            Professional and institutional entry
          </h2>
          <p className="mt-3 text-sm leading-6 text-slate-700">
            Learn how provider access, practice registration, facility onboarding, regulatory work
            and professional development operate before signing in.
          </p>
          <p className="mt-3 text-sm font-medium text-amber-900">
            Authentication proves identity. TSHEPO still verifies professional standing,
            organisational relationships and delegated authority.
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <Link
              href="/provider/get-access"
              className="inline-flex min-h-11 items-center rounded-xl bg-amber-800 px-5 py-2.5 text-sm font-semibold text-white hover:bg-amber-900"
            >
              Explore professional access
            </Link>
            <Link
              href="/welcome/regulatory"
              className="inline-flex min-h-11 items-center rounded-xl border border-amber-300 bg-white px-5 py-2.5 text-sm font-semibold text-amber-950 hover:bg-amber-50"
            >
              Regulatory services
            </Link>
          </div>
        </article>
      </section>

      <section
        className="mt-14 rounded-[2rem] border border-emerald-100 bg-white p-7 shadow-sm sm:p-9"
        aria-labelledby="trust-title"
      >
        <div className="max-w-3xl">
          <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-700">
            Progressive trust
          </p>
          <h2 id="trust-title" className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            Impilo unlocks value in clear, safe steps
          </h2>
          <p className="mt-3 text-base leading-7 text-slate-600">
            You can leave, return or ask for help without losing the intent that brought you here.
            High-impact personal, clinical, financial and regulatory actions remain protected.
          </p>
        </div>
        <ol className="mt-7 grid gap-4 md:grid-cols-3">
          {TRUST_STEPS.map((step, index) => {
            const Icon = step.icon;
            return (
              <li key={step.title} className="rounded-2xl border border-emerald-100 bg-emerald-50/50 p-5">
                <div className="flex items-center gap-3">
                  <span className="grid h-10 w-10 place-items-center rounded-xl bg-emerald-700 text-white">
                    <Icon className="h-5 w-5" aria-hidden />
                  </span>
                  <span className="text-xs font-semibold uppercase tracking-[0.08em] text-emerald-800">
                    Step {index + 1}
                  </span>
                </div>
                <h3 className="mt-4 font-semibold text-slate-950">{step.title}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-600">{step.body}</p>
              </li>
            );
          })}
        </ol>
        <div className="mt-6 flex flex-wrap gap-4 text-sm font-semibold">
          <Link href="/privacy" className="text-emerald-700 hover:text-emerald-900">
            Privacy and consent
          </Link>
          <Link
            href="/welcome/accessibility"
            className="text-emerald-700 hover:text-emerald-900"
          >
            Accessibility and language
          </Link>
          <Link href="/status" className="text-emerald-700 hover:text-emerald-900">
            Service status
          </Link>
        </div>
      </section>

      <section
        id="about-impilo"
        className="mt-14 grid gap-6 rounded-[2rem] bg-emerald-950 p-7 text-white sm:p-10 lg:grid-cols-[minmax(0,1.2fr)_minmax(18rem,.8fr)]"
      >
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.08em] text-emerald-200">
            One Impilo
          </p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight">
            Zimbabwe&apos;s National Health Operating System
          </h2>
          <p className="mt-4 max-w-3xl text-base leading-7 text-emerald-50/85">
            Impilo connects care, records, telemedicine, learning, diagnostics, public health,
            regulation and citizen services in one governed environment led by the Ministry of
            Health and Child Care. People do not leave a public website for another product:
            public discovery and protected work are stages of the same Impilo experience.
          </p>
          <Link
            href="/about"
            className="mt-5 inline-flex min-h-11 items-center rounded-xl bg-white px-5 py-2.5 text-sm font-semibold text-emerald-950 hover:bg-emerald-50"
          >
            About Impilo
          </Link>
        </div>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
          <div className="rounded-2xl bg-white/10 p-5 ring-1 ring-white/15">
            <Building2 className="h-5 w-5 text-emerald-200" aria-hidden />
            <p className="mt-2 font-semibold">Nationally governed</p>
            <p className="mt-1 text-sm text-emerald-50/75">Led by MoHCC with explicit public and protected trust boundaries.</p>
          </div>
          <div className="rounded-2xl bg-white/10 p-5 ring-1 ring-white/15">
            <ShieldCheck className="h-5 w-5 text-emerald-200" aria-hidden />
            <p className="mt-2 font-semibold">Open enough to help now</p>
            <p className="mt-1 text-sm text-emerald-50/75">Private enough to protect the person, professional and institution.</p>
          </div>
        </div>
      </section>

      <section className="mt-14 grid gap-6 pb-4 md:grid-cols-2">
        <article className="rounded-[2rem] border border-violet-200 bg-violet-50 p-7 sm:p-8">
          <Sparkles className="h-7 w-7 text-violet-700" aria-hidden />
          <h2 className="mt-4 text-2xl font-bold text-slate-950">Shape Impilo with us</h2>
          <p className="mt-3 text-sm leading-6 text-slate-700">
            We welcome your suggestions and contributions on how digital health can improve your
            health, your community and Zimbabwe&apos;s health system.
          </p>
          <Link
            href="/get-involved"
            className="mt-5 inline-flex min-h-11 items-center rounded-xl bg-violet-800 px-5 py-2.5 text-sm font-semibold text-white hover:bg-violet-900"
          >
            Get involved
          </Link>
        </article>
        <article className="rounded-[2rem] border border-sky-200 bg-sky-50 p-7 sm:p-8">
          <Smartphone className="h-7 w-7 text-sky-700" aria-hidden />
          <h2 className="mt-4 text-2xl font-bold text-slate-950">Take Impilo with you</h2>
          <p className="mt-3 text-sm leading-6 text-slate-700">
            Use Impilo on the web today and see the honest release status for the citizen and
            provider mobile apps.
          </p>
          <Link
            href="/download"
            className="mt-5 inline-flex min-h-11 items-center rounded-xl bg-sky-800 px-5 py-2.5 text-sm font-semibold text-white hover:bg-sky-900"
          >
            Download and app information
          </Link>
        </article>
      </section>
    </PublicShell>
  );
}
