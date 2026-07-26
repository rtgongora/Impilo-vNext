import { PublicShell } from "@/components/public/PublicShell";
import { PublicPageHeader } from "@/components/public/PublicPrimitives";
import { ReportIncidentTriage } from "@/components/public/ReportIncidentTriage";
import { NompiloAdaptiveGuidance } from "@/components/intelligent/NompiloAdaptiveGuidance";

export const metadata = {
  title: "Report a health incident — Impilo",
  description:
    "Report an emergency, unsafe care, a safety concern or a complaint. Emergency help is never blocked by sign-in.",
};

/**
 * Public "Report a health incident" triage (gateway doctrine §2 — cards attach to the
 * emergency-help and feedback-complaints pillars). Reachable WITHOUT login; routes each
 * branch honestly: emergencies go straight to the always-open emergency surface; safety
 * concerns and complaints can be reported fully anonymously (claim-code lane, ADR W4)
 * or from a signed-in account.
 */
export default function ReportIncidentPage() {
  return (
    <PublicShell>
      <PublicPageHeader
        crumbs={[{ label: "Impilo", href: "/" }, { label: "Report a health incident" }]}
        title="Report a health incident"
        lede={
          <>
            Tell us what happened. We&apos;ll take you to the right place — emergencies are
            handled immediately and are never blocked by sign-in.
          </>
        }
      />

      {/* Advisory: anonymity and the claim code are consequential choices a person
          must not miss, so this guidance does not auto-collapse. */}
      <NompiloAdaptiveGuidance
        id="report-guidance"
        mode="advisory"
        collapsedLabel="How is my report handled?"
        contextMessage="Tell us what happened in your own words. You can report anonymously — you do not need an Impilo ID, and you are not asked to identify the person involved."
        suggestions={[
          "Anonymous reports cannot be followed up with you directly. Add contact details only if you want a response.",
          "Keep the claim code you are given — it is how you check progress later without signing in.",
          "If someone is in immediate danger, use Emergency instead. This form is not monitored in real time.",
        ]}
      />

      <ReportIncidentTriage />

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">What happens to your report</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>Emergency requests are triaged and dispatched straight away.</li>
          <li>
            Safety concerns and complaints open a tracked case with a reference number, and
            you can follow the outcome.
          </li>
          <li>
            You can report fully anonymously without an account — you receive a one-time
            claim code to check the outcome, and your identity is never recorded.
          </li>
        </ul>
      </section>
    </PublicShell>
  );
}
