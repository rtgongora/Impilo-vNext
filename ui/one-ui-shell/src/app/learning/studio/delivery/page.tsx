"use client";

import { useState } from "react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import {
  useCreateFundoFacilitator,
  useCreateFundoVenue,
  useFundoFacilitators,
  useFundoVenues,
  useSetFundoFacilitatorStatus,
} from "@/hooks/queries/useFundoDelivery";

type Facilitator = { id: string; displayName: string; facilitatorKind?: string; cadre?: string; status?: string };
type Venue = { id: string; name: string; venueKind?: string; facilityLevel?: string; capacity?: number; status?: string };

export default function FundoStudioDeliveryPage() {
  const { data: facData } = useFundoFacilitators();
  const { data: venueData } = useFundoVenues();
  const createFacilitator = useCreateFundoFacilitator();
  const setStatus = useSetFundoFacilitatorStatus();
  const createVenue = useCreateFundoVenue();

  const [facName, setFacName] = useState("");
  const [facKind, setFacKind] = useState("TRAINER");
  const [venueName, setVenueName] = useState("");
  const [venueKind, setVenueKind] = useState("PHYSICAL");

  const facilitators = (((facData?.data as { items?: Facilitator[] } | undefined)?.items) ?? []);
  const venues = (((venueData?.data as { items?: Venue[] } | undefined)?.items) ?? []);

  async function addFacilitator() {
    await createFacilitator.mutateAsync({ displayName: facName, facilitatorKind: facKind });
    setFacName("");
  }
  async function addVenue() {
    await createVenue.mutateAsync({ name: venueName, venueKind });
    setVenueName("");
  }

  return (
    <FundoStudioWorkspace title="Delivery: Facilitators & Venues" subtitle="Manage trainers, teachers, preceptors and training venues used by cohorts and sessions.">
      <div className="grid gap-4 md:grid-cols-2">
        <section className="rounded-lg border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Facilitators</p>
          <div className="mt-2 grid gap-2 md:grid-cols-3">
            <input value={facName} onChange={(e) => setFacName(e.target.value)} placeholder="Display name" className="rounded border border-border px-3 py-2 text-sm md:col-span-2" />
            <select value={facKind} onChange={(e) => setFacKind(e.target.value)} className="rounded border border-border px-3 py-2 text-sm">
              {["TRAINER", "TEACHER", "PRECEPTOR", "GUEST"].map((k) => <option key={k}>{k}</option>)}
            </select>
          </div>
          <button onClick={addFacilitator} disabled={!facName.trim() || createFacilitator.isPending} className="mt-2 rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60">
            Add facilitator
          </button>
          <ul className="mt-3 space-y-2 text-sm" data-testid="fundo-facilitator-list">
            {facilitators.map((f) => (
              <li key={f.id} className="flex items-center justify-between rounded border border-border px-3 py-2">
                <span>{f.displayName} · {f.facilitatorKind ?? "TRAINER"}{f.cadre ? ` · ${f.cadre}` : ""} · {f.status ?? "ACTIVE"}</span>
                <button
                  onClick={() => setStatus.mutate({ id: f.id, status: f.status === "ACTIVE" ? "INACTIVE" : "ACTIVE" })}
                  className="text-teal-700 hover:underline"
                >
                  {f.status === "ACTIVE" ? "Deactivate" : "Activate"}
                </button>
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-lg border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Training venues</p>
          <div className="mt-2 grid gap-2 md:grid-cols-3">
            <input value={venueName} onChange={(e) => setVenueName(e.target.value)} placeholder="Venue name" className="rounded border border-border px-3 py-2 text-sm md:col-span-2" />
            <select value={venueKind} onChange={(e) => setVenueKind(e.target.value)} className="rounded border border-border px-3 py-2 text-sm">
              {["PHYSICAL", "VIRTUAL", "HYBRID"].map((k) => <option key={k}>{k}</option>)}
            </select>
          </div>
          <button onClick={addVenue} disabled={!venueName.trim() || createVenue.isPending} className="mt-2 rounded bg-teal-600 px-3 py-2 text-sm text-white disabled:opacity-60">
            Add venue
          </button>
          <ul className="mt-3 space-y-2 text-sm" data-testid="fundo-venue-list">
            {venues.map((v) => (
              <li key={v.id} className="rounded border border-border px-3 py-2">
                {v.name} · {v.venueKind ?? "PHYSICAL"}{v.facilityLevel ? ` · ${v.facilityLevel}` : ""}{v.capacity ? ` · cap ${v.capacity}` : ""}
              </li>
            ))}
          </ul>
        </section>
      </div>
    </FundoStudioWorkspace>
  );
}
