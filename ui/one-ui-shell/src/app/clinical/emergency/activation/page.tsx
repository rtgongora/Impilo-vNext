"use client";

/**
 * Open an emergency episode — the standalone activation surface.
 *
 * The episode board carries a one-dropdown shortcut for the common walk-in case. This is the full
 * form: all fourteen entry routes, the identity mode, and the sensitivity flag, for the cases the
 * shortcut cannot express — an unidentified patient brought in by police, an inter-facility
 * referral, a teleconsult escalation.
 *
 * Care first: an episode can be opened before the patient is identified. `identityMode` records
 * which of those two situations this is, and the identity is linked later on the spine — appended,
 * never overwritten.
 */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useOpenEmergencyEpisode,
  type EmergencyEpisodeRow,
} from "@/hooks/queries/useEmergencyEpisode";
import { bffErrorMessage } from "@/lib/bff-errors";

const ENTRY_ROUTES: { code: string; label: string }[] = [
  { code: "WALK_IN", label: "Walk-in" },
  { code: "AMBULANCE", label: "Ambulance" },
  { code: "STATUTORY_SERVICE", label: "Police, fire or other statutory service" },
  { code: "INTERFACILITY_REFERRAL", label: "Inter-facility referral" },
  { code: "CHW_REFERRAL", label: "Community health worker referral" },
  { code: "PRIVATE_VEHICLE", label: "Private vehicle" },
  { code: "WORKPLACE_OR_SCHOOL", label: "Workplace or school" },
  { code: "MASS_CASUALTY", label: "Mass casualty" },
  { code: "IN_FACILITY_DETERIORATION", label: "In-facility deterioration" },
  { code: "CLINIC_ESCALATION", label: "Clinic escalation" },
  { code: "TELECONSULT_ESCALATION", label: "Teleconsult escalation" },
  { code: "PUBLIC_REQUEST", label: "Public request" },
  { code: "DIRECT_SPECIALIST", label: "Direct specialist" },
  { code: "UNKNOWN_OR_UNRESPONSIVE", label: "Unknown or unresponsive" },
];

const EPISODE_CLASSES = ["MEDICAL", "TRAUMA", "OBSTETRIC", "PAEDIATRIC", "MENTAL_HEALTH", "TOXICOLOGICAL"];

export default function EmergencyActivationPage() {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const openEpisode = useOpenEmergencyEpisode();

  const [entryRoute, setEntryRoute] = useState("WALK_IN");
  const [episodeClass, setEpisodeClass] = useState("MEDICAL");
  const [subjectCpid, setSubjectCpid] = useState("");
  const [sensitive, setSensitive] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  return (
    <AppLayout>
      <PageShell
        title="Open an emergency episode"
        subtitle="pct.emergency_episode — care first: an episode can be opened before the patient is identified"
      >
        <div className="mb-4 flex gap-4 text-sm">
          <Link href="/clinical/emergency/board" className="text-primary underline">
            Episode board
          </Link>
          <Link href="/clinical/emergency/command" className="text-primary underline">
            Emergency command
          </Link>
        </div>

        <form
          className="max-w-xl space-y-4 rounded-lg border border-border bg-card p-4"
          data-testid="activation-form"
          onSubmit={async (event) => {
            event.preventDefault();
            if (!facility?.id) return;
            setServerError(null);
            try {
              const created = (await openEpisode.mutateAsync({
                facilityId: facility.id,
                entryRoute,
                episodeClass,
                subjectCpid: subjectCpid.trim() || undefined,
                sensitive,
              })) as { data?: EmergencyEpisodeRow } & EmergencyEpisodeRow;
              const episodeId = created?.data?.episode_id ?? created?.episode_id;
              if (episodeId) router.push(`/clinical/emergency/spine/${String(episodeId)}`);
            } catch (error) {
              setServerError(bffErrorMessage(error, "this episode"));
            }
          }}
        >
          <label className="block text-sm">
            <span className="font-medium text-foreground">How did this patient reach us?</span>
            <select
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              value={entryRoute}
              onChange={(event) => setEntryRoute(event.target.value)}
              data-testid="activation-entry-route"
            >
              {ENTRY_ROUTES.map((route) => (
                <option key={route.code} value={route.code}>
                  {route.label}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm">
            <span className="font-medium text-foreground">Episode class</span>
            <select
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              value={episodeClass}
              onChange={(event) => setEpisodeClass(event.target.value)}
              data-testid="activation-episode-class"
            >
              {EPISODE_CLASSES.map((value) => (
                <option key={value} value={value}>
                  {value.replaceAll("_", " ").toLowerCase()}
                </option>
              ))}
            </select>
          </label>

          <label className="block text-sm">
            <span className="font-medium text-foreground">Patient CPID, if already known</span>
            <input
              className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
              value={subjectCpid}
              onChange={(event) => setSubjectCpid(event.target.value)}
              placeholder="Leave blank for an unidentified patient"
              data-testid="activation-subject-cpid"
            />
            <span className="mt-1 block text-xs text-muted-foreground">
              Leaving this blank opens the episode unidentified. The identity is linked later on the
              spine — appended to the record, never overwriting what was captured at the door.
            </span>
          </label>

          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-1"
              checked={sensitive}
              onChange={(event) => setSensitive(event.target.checked)}
              data-testid="activation-sensitive"
            />
            <span>
              <span className="font-medium text-foreground">Sensitive episode</span>
              <span className="block text-xs text-muted-foreground">
                Routes this episode through the confidential lane. Set it at the door if in doubt;
                it cannot un-disclose what a board has already shown.
              </span>
            </span>
          </label>

          {!facility?.id ? (
            <p className="text-sm text-warning-foreground">
              Select a facility before opening an episode.
            </p>
          ) : null}

          {serverError ? (
            <p className="rounded border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger" data-testid="activation-error">
              {serverError}
            </p>
          ) : null}

          <button
            type="submit"
            className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            disabled={openEpisode.isPending || !facility?.id}
            data-testid="activation-submit"
          >
            {openEpisode.isPending ? "Opening…" : "Open episode"}
          </button>
        </form>
      </PageShell>
    </AppLayout>
  );
}
