/**
 * Read-only, Rito-sourced verified patient-experience summary for a public facility
 * profile (RW8). Tuso composes it; Rito owns it. Verified experience domains only —
 * never respondent identity. Returns null when no experience data is present, so the
 * profile renders honestly without it.
 */

import { Star } from "lucide-react";
import type { FacilityProfile } from "@/lib/find-care/types";

const EXPERIENCE_DOMAIN_LABELS: Record<string, string> = {
  CLIENT_EXPERIENCE: "Client experience",
  ACCESS_PROCESS: "Access & waiting",
};

export function FacilityExperienceCard({ profile }: { profile: FacilityProfile }) {
  const experience = profile.experience;
  if (!experience || !experience.domains || experience.domains.length === 0) {
    return null;
  }

  return (
    <section
      className="rounded-2xl border border-border bg-card p-6"
      data-testid="facility-experience-card"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 font-semibold text-foreground">
          <Star className="h-4 w-4 text-emerald-600" aria-hidden />
          Patient experience
        </h2>
        <span className="text-[11px] text-muted-foreground">
          {experience.verifiedInteractionTotal} verified interaction
          {experience.verifiedInteractionTotal === 1 ? "" : "s"}
        </span>
      </div>
      <dl className="mt-3 divide-y divide-border/60 text-sm">
        {experience.domains.map((d) => (
          <div key={d.domain} className="flex items-start justify-between gap-4 py-2.5">
            <dt className="text-muted-foreground">
              {EXPERIENCE_DOMAIN_LABELS[d.domain] ?? d.domain}
            </dt>
            <dd className="text-right font-medium text-foreground">
              {d.verifiedMeanScore != null ? `${d.verifiedMeanScore.toFixed(1)} / 5` : "—"}
            </dd>
          </div>
        ))}
      </dl>
      <p className="mt-3 text-[11px] text-muted-foreground">
        Verified patient experience from completed interactions · Source: {experience.source}
      </p>
    </section>
  );
}
