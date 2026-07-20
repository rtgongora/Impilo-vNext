import Link from "next/link";
import {
  Activity,
  Baby,
  Brain,
  Droplets,
  HeartPulse,
  ShieldPlus,
  Stethoscope,
  Syringe,
} from "lucide-react";

/**
 * Danger-sign cards + expandable public-health guidance. Purposeful interface
 * elements instead of flat text walls: a frightened user scans icons and short
 * labels; detail is progressive disclosure via native <details> (keyboard and
 * screen-reader accessible with zero JS).
 */

const DANGER_SIGNS = [
  {
    icon: HeartPulse,
    label: "Chest pain or severe breathing difficulty",
    detail: "Sit them down, loosen tight clothing, call for help immediately.",
  },
  {
    icon: Brain,
    label: "Sudden weakness, confusion or trouble speaking",
    detail: "Possible stroke — note the time it started and seek help now.",
  },
  {
    icon: Droplets,
    label: "Severe bleeding that won't stop",
    detail: "Press firmly with a clean cloth and keep pressing.",
  },
  {
    icon: Baby,
    label: "High fever in a baby, or a serious injury",
    detail: "Babies get worse quickly — do not wait to see if it improves.",
  },
  {
    icon: Activity,
    label: "Pregnancy emergencies or reduced baby movement",
    detail: "Go to a maternity unit now.",
  },
];

const GUIDANCE = [
  {
    icon: Droplets,
    title: "Cholera & diarrhoea",
    body: "Wash hands with soap and use safe drinking water. Severe watery diarrhoea needs oral rehydration and care the same day.",
  },
  {
    icon: Syringe,
    title: "Immunisation",
    body: "Keep children up to date with routine immunisations at your nearest clinic — they are free at public facilities.",
  },
  {
    icon: Stethoscope,
    title: "Malaria, TB & common illness",
    body: "Seek early testing and treatment. Fever in a malaria area should be tested within 24 hours.",
  },
  {
    icon: ShieldPlus,
    title: "During outbreaks",
    body: "Follow current Ministry of Health advisories. Service advisories appear at the top of this site when active.",
  },
];

export function EmergencyGuidanceCards() {
  return (
    <>
      <section className="mt-8" aria-labelledby="danger-signs-heading">
        <h2 id="danger-signs-heading" className="text-lg font-bold text-slate-900">
          Danger signs — seek urgent care
        </h2>
        <div className="mt-3 grid gap-2.5 sm:grid-cols-2">
          {DANGER_SIGNS.map((sign) => {
            const Icon = sign.icon;
            return (
              <div
                key={sign.label}
                className="flex items-start gap-3 rounded-xl border border-red-200 bg-white p-3.5"
              >
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-red-50 text-red-700">
                  <Icon className="h-5 w-5" aria-hidden />
                </span>
                <div>
                  <p className="text-sm font-semibold text-slate-900">{sign.label}</p>
                  <p className="mt-0.5 text-[13px] text-slate-600">{sign.detail}</p>
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section className="mt-8" aria-labelledby="ph-guidance-heading">
        <h2 id="ph-guidance-heading" className="text-lg font-bold text-slate-900">
          Public-health guidance
        </h2>
        <div className="mt-3 space-y-2">
          {GUIDANCE.map((item) => {
            const Icon = item.icon;
            return (
              <details
                key={item.title}
                className="group rounded-xl border border-slate-200 bg-white open:border-emerald-300"
              >
                <summary className="flex min-h-[48px] cursor-pointer list-none items-center gap-3 px-4 py-3 text-sm font-semibold text-slate-900 [&::-webkit-details-marker]:hidden">
                  <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-emerald-50 text-emerald-700">
                    <Icon className="h-4.5 w-4.5" aria-hidden />
                  </span>
                  {item.title}
                  <span className="ml-auto text-slate-400 transition-transform group-open:rotate-90">
                    ›
                  </span>
                </summary>
                <p className="px-4 pb-4 pl-[60px] text-sm leading-relaxed text-slate-600">
                  {item.body}
                </p>
              </details>
            );
          })}
        </div>
        <Link
          href="/welcome/health-info"
          className="mt-4 inline-block min-h-[44px] py-2 text-sm font-semibold text-emerald-700 hover:text-emerald-800"
        >
          More trusted health information →
        </Link>
      </section>
    </>
  );
}
