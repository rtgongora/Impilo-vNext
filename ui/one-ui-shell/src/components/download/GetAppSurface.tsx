"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  HeartPulse,
  Stethoscope,
  Monitor,
  Apple,
  Play,
  Bell,
  ShieldCheck,
  ShieldAlert,
  ArrowRight,
  Check,
  QrCode,
  MapPin,
  CalendarClock,
  Pill,
  Video,
  Wallet,
  MessageSquareHeart,
  IdCard,
  Repeat,
  WifiOff,
  Workflow,
  BadgeCheck,
  type LucideIcon,
} from "lucide-react";
import { WebAppQr, QR_WEB_URL } from "./WebAppQr";

// ── Device awareness ─────────────────────────────────────────────────────────
type Platform = "unknown" | "android" | "ios" | "desktop";

function useDevicePlatform(): Platform {
  const [platform, setPlatform] = useState<Platform>("unknown");
  useEffect(() => {
    if (typeof navigator === "undefined") return;
    const ua = navigator.userAgent || "";
    if (/android/i.test(ua)) setPlatform("android");
    else if (/iphone|ipad|ipod/i.test(ua) || (/Macintosh/.test(ua) && "ontouchend" in document))
      setPlatform("ios");
    else setPlatform("desktop");
  }, []);
  return platform;
}

// Honest, official notify address (matches the legal/support pages).
const NOTIFY_EMAIL = "support@impilo.io";

interface AppDef {
  icon: LucideIcon;
  name: string;
  who: string;
  bundle: string;
  blurb: string;
  features: { icon: LucideIcon; label: string }[];
}

const APPS: Record<"citizen" | "provider", AppDef> = {
  citizen: {
    icon: HeartPulse,
    name: "Impilo",
    who: "For citizens, patients & caregivers",
    bundle: "zw.gov.impilo.citizen",
    blurb:
      "Your health in your pocket — find care, hold your Health ID and records, and stay on top of appointments and cover.",
    features: [
      { icon: MapPin, label: "Find care & book visits" },
      { icon: IdCard, label: "Hold your Health ID & records" },
      { icon: CalendarClock, label: "Appointment reminders" },
      { icon: Pill, label: "Medicine reminders" },
      { icon: Video, label: "Telemedicine visits" },
      { icon: Wallet, label: "Bills, payments & health cover" },
      { icon: MessageSquareHeart, label: "Feedback & Get Involved" },
    ],
  },
  provider: {
    icon: Stethoscope,
    name: "Impilo Provider",
    who: "For verified health workers & facilities",
    bundle: "zw.gov.impilo.provider",
    blurb:
      "The workspace for authorised professionals — deliver care, manage referrals and workflows, and keep working on the move.",
    features: [
      { icon: BadgeCheck, label: "Authorised professional & facility workspaces" },
      { icon: Repeat, label: "Referrals & care coordination" },
      { icon: Video, label: "Virtual care & consults" },
      { icon: Workflow, label: "Patient queues & clinical workflows" },
      { icon: Bell, label: "Work notifications & handovers" },
      { icon: WifiOff, label: "Offline-capable field support" },
    ],
  },
};

const PLATFORMS = [
  { key: "android", label: "Google Play", comingLabel: "Coming to Google Play", Icon: Play },
  { key: "ios", label: "the App Store", comingLabel: "Coming to the App Store", Icon: Apple },
] as const;

// Un-fakeable "notify me": opens the visitor's own email client with a
// pre-addressed registration-of-interest. No store listing to subscribe to yet,
// so we never render a fabricated "Subscribed!" confirmation.
function NotifyMe({ appName }: { appName: string }) {
  const [email, setEmail] = useState("");
  const [opened, setOpened] = useState(false);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const subject = encodeURIComponent(`Notify me when ${appName} is available`);
    const body = encodeURIComponent(
      `Please let me know when ${appName} is published on the app stores.` +
        (email ? `\n\nMy email: ${email}` : ""),
    );
    window.location.href = `mailto:${NOTIFY_EMAIL}?subject=${subject}&body=${body}`;
    setOpened(true);
  }

  return (
    <form onSubmit={handleSubmit} className="mt-3">
      <label htmlFor={`notify-${appName}`} className="sr-only">
        Your email address to be notified when {appName} launches
      </label>
      <div className="flex flex-col gap-2 sm:flex-row">
        <input
          id={`notify-${appName}`}
          type="email"
          inputMode="email"
          autoComplete="email"
          value={email}
          onChange={(ev) => setEmail(ev.target.value)}
          placeholder="you@example.com (optional)"
          className="min-w-0 flex-grow rounded-full border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
        />
        <button
          type="submit"
          className="inline-flex shrink-0 items-center justify-center gap-1.5 rounded-full bg-white px-4 py-2.5 text-sm font-bold text-emerald-800 ring-1 ring-inset ring-emerald-500/40 transition-colors hover:bg-emerald-50 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
        >
          <Bell className="h-4 w-4" aria-hidden="true" /> Notify me
        </button>
      </div>
      <p className="mt-1.5 text-xs leading-relaxed text-slate-500" role={opened ? "status" : undefined}>
        {opened
          ? "We've opened your email app — send the message to register your interest."
          : "Opens your email app to register interest. We'll email you when it's live — nothing is submitted from this page."}
      </p>
    </form>
  );
}

function AppCard({ appKey, platform }: { appKey: "citizen" | "provider"; platform: Platform }) {
  const app = APPS[appKey];
  const Icon = app.icon;

  const orderedPlatforms = useMemo(() => {
    if (platform === "android") return [...PLATFORMS].sort((a) => (a.key === "android" ? -1 : 1));
    if (platform === "ios") return [...PLATFORMS].sort((a) => (a.key === "ios" ? -1 : 1));
    return [...PLATFORMS];
  }, [platform]);

  return (
    <div className="flex h-full flex-col rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center gap-3">
        <span className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl bg-emerald-600 text-white">
          <Icon className="h-6 w-6" aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <div className="text-lg font-bold tracking-tight text-slate-900">{app.name}</div>
          <div className="text-[13px] font-medium text-emerald-700">{app.who}</div>
        </div>
      </div>

      <p className="mt-3 text-sm leading-relaxed text-slate-600">{app.blurb}</p>

      <ul className="mt-4 grid gap-1.5">
        {app.features.map((f) => {
          const FIcon = f.icon;
          return (
            <li key={f.label} className="flex items-center gap-2 text-[13.5px] text-slate-700">
              <FIcon className="h-4 w-4 shrink-0 text-emerald-600" aria-hidden="true" />
              {f.label}
            </li>
          );
        })}
      </ul>

      <div className="mt-5 flex flex-grow flex-col justify-end">
        <div className="grid gap-2 sm:grid-cols-2">
          {orderedPlatforms.map((p) => {
            const emphasised =
              (platform === "android" && p.key === "android") ||
              (platform === "ios" && p.key === "ios");
            return (
              <div
                key={p.key}
                aria-disabled="true"
                title={`${app.name} is not on ${p.label} yet — listings are being prepared.`}
                className={`flex items-center gap-2 rounded-2xl border px-3 py-2.5 text-left ${
                  emphasised ? "border-emerald-400 bg-emerald-50" : "border-slate-200 bg-slate-50"
                }`}
              >
                <p.Icon className="h-5 w-5 shrink-0 text-emerald-700" aria-hidden="true" />
                <span className="min-w-0">
                  <span className="block text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                    Coming soon
                  </span>
                  <span className="block truncate text-[12.5px] font-bold text-slate-900">
                    {p.comingLabel}
                  </span>
                </span>
              </div>
            );
          })}
        </div>

        <NotifyMe appName={app.name} />

        <p className="mt-3 text-[11.5px] text-slate-500">
          Bundle ID{" "}
          <code className="rounded bg-slate-100 px-1 py-0.5 text-[11px] text-slate-700">{app.bundle}</code>
        </p>
      </div>
    </div>
  );
}

/**
 * Get-app surface — the honest, device-aware "get the Impilo apps" experience.
 * Mirror of the public website /apps page for signed-in / returning users.
 * Public route (guard: none): safe to view before authentication.
 */
export function GetAppSurface() {
  const platform = useDevicePlatform();

  const providerSteps = [
    "Install Impilo Provider (or use Impilo on the web)",
    "Sign in as a person with your Impilo account",
    "Request Provider Access and choose how to prove your registration",
    "Your registration is verified against the national provider register (Varapi)",
    "Your facility or employer confirms your context (Tuso · Vashandi)",
    "Your professional workspaces unlock — with the right access, for the right role",
  ];

  return (
    <div className="space-y-5">
      <div className="grid gap-4 md:grid-cols-2">
        <AppCard appKey="citizen" platform={platform} />
        <AppCard appKey="provider" platform={platform} />
      </div>

      {/* Web-first primary action + device-aware QR */}
      <div className="grid items-center gap-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm md:grid-cols-[1fr_auto] md:p-8">
        <div>
          <span className="inline-flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-emerald-700">
            <Monitor className="h-4 w-4" aria-hidden="true" /> Available now
          </span>
          <h2 className="mt-3 text-xl font-bold tracking-tight text-slate-900">
            Use Impilo on the web today
          </h2>
          <p className="mt-2 max-w-xl text-sm leading-relaxed text-slate-600">
            You don&apos;t have to wait for the app stores. Everything Impilo offers runs in your
            phone or computer browser — no install needed.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Link
              href="/home"
              className="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-6 py-3 text-sm font-bold text-white transition-colors hover:bg-emerald-700 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
            >
              <Monitor className="h-4 w-4" aria-hidden="true" /> Open Impilo on the web
            </Link>
            <Link
              href="/welcome"
              className="inline-flex items-center gap-2 rounded-full bg-white px-5 py-3 text-sm font-bold text-emerald-800 ring-1 ring-inset ring-emerald-500/30 transition-colors hover:bg-emerald-50 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
            >
              Explore without signing in
            </Link>
          </div>
        </div>

        <div className="flex flex-col items-center gap-2 justify-self-center rounded-2xl bg-slate-50 p-4 ring-1 ring-slate-200">
          <WebAppQr size={140} />
          <p className="flex items-center gap-1.5 text-center text-xs font-semibold text-slate-900">
            <QrCode className="h-3.5 w-3.5 text-emerald-600" aria-hidden="true" /> Scan to open Impilo
          </p>
          <p className="max-w-[160px] text-center text-[11px] leading-snug text-slate-500">
            Opens the web app on your phone — not a store download.
          </p>
        </div>
      </div>

      {/* Provider path — honest access boundary */}
      <div className="rounded-2xl border border-emerald-200 bg-emerald-50/60 p-6 shadow-sm md:p-8">
        <div className="flex items-start gap-3">
          <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl border border-emerald-200 bg-white text-emerald-700">
            <ShieldCheck className="h-5 w-5" aria-hidden="true" />
          </span>
          <div>
            <h2 className="text-lg font-bold tracking-tight text-slate-900">Are you a health worker?</h2>
            <p className="mt-1.5 max-w-2xl text-sm leading-relaxed text-slate-600">
              Downloading <strong>Impilo Provider</strong> does not automatically grant access to
              clinical systems. Professional and facility access is verified — to protect patients,
              providers and health information.
            </p>
            <ol className="mt-4 grid gap-2 sm:grid-cols-2">
              {providerSteps.map((step, i) => (
                <li key={step} className="flex items-start gap-2 text-[13.5px] text-slate-700">
                  <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full bg-emerald-600 text-[11px] font-bold text-white">
                    {i + 1}
                  </span>
                  {step}
                </li>
              ))}
            </ol>
            <div className="mt-4 flex flex-wrap gap-2">
              <Link
                href="/provider/activate"
                className="inline-flex items-center gap-2 rounded-full bg-emerald-600 px-5 py-2.5 text-sm font-bold text-white transition-colors hover:bg-emerald-700 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
              >
                Request Provider Access <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
              <Link
                href="/provider/get-access"
                className="inline-flex items-center gap-2 rounded-full bg-white px-5 py-2.5 text-sm font-bold text-emerald-800 ring-1 ring-inset ring-emerald-500/30 transition-colors hover:bg-emerald-50 focus:outline-none focus-visible:ring-4 focus-visible:ring-emerald-500/25"
              >
                How access works
              </Link>
            </div>
          </div>
        </div>
      </div>

      {/* Trust & legitimacy */}
      <div className="grid gap-4 md:grid-cols-3">
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm md:col-span-2">
          <h2 className="flex items-center gap-2 text-[15px] font-bold text-slate-900">
            <ShieldAlert className="h-4 w-4 text-emerald-600" aria-hidden="true" /> Download only the
            official apps
          </h2>
          <p className="mt-2 text-[13.5px] leading-relaxed text-slate-600">
            Only download <strong>Impilo</strong> and <strong>Impilo Provider</strong> from the
            official links on the Ministry website or from verified Ministry store listings once they
            are published. Beware of unofficial copies — the official publisher is the{" "}
            <strong>Ministry of Health and Child Care, Zimbabwe</strong>.
          </p>
          <ul className="mt-3 grid gap-1.5 text-[13px] text-slate-700">
            <li className="flex items-center gap-2">
              <Check className="h-4 w-4 text-emerald-600" aria-hidden="true" /> Publisher: Ministry of
              Health and Child Care (MoHCC)
            </li>
            <li className="flex items-center gap-2">
              <Check className="h-4 w-4 text-emerald-600" aria-hidden="true" /> Bundles:{" "}
              <code className="rounded bg-slate-100 px-1 text-[11px] text-slate-700">zw.gov.impilo.citizen</code>{" "}
              /{" "}
              <code className="rounded bg-slate-100 px-1 text-[11px] text-slate-700">zw.gov.impilo.provider</code>
            </li>
            <li className="flex items-center gap-2">
              <Check className="h-4 w-4 text-emerald-600" aria-hidden="true" /> Minimum OS: Android
              8.0+ · iOS 15+
            </li>
          </ul>
          <p className="mt-3 text-xs text-slate-500">
            <Link href="/privacy/app-stores" className="font-semibold text-emerald-700 underline hover:text-emerald-800">
              App privacy summary
            </Link>{" "}
            · The QR above encodes <span className="font-mono text-[11.5px]">{QR_WEB_URL}</span>
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-[15px] font-bold text-slate-900">Release status</h2>
          <dl className="mt-2 space-y-2 text-[13px]">
            <div className="flex items-center justify-between gap-2">
              <dt className="text-slate-500">Version</dt>
              <dd className="font-semibold text-slate-900">0.1.0 (pre-release)</dd>
            </div>
            <div className="flex items-center justify-between gap-2">
              <dt className="text-slate-500">Store listings</dt>
              <dd className="font-semibold text-amber-700">Being prepared</dd>
            </div>
            <div className="flex items-center justify-between gap-2">
              <dt className="text-slate-500">Web app</dt>
              <dd className="font-semibold text-emerald-700">Available now</dd>
            </div>
            <div className="flex items-center justify-between gap-2">
              <dt className="text-slate-500">Reviewed</dt>
              <dd className="font-semibold text-slate-900">July 2026</dd>
            </div>
          </dl>
          <p className="mt-3 text-xs leading-relaxed text-slate-500">
            The apps are in pre-release. Store availability will be announced here once the Ministry
            publishes the verified listings.
          </p>
        </div>
      </div>
    </div>
  );
}
