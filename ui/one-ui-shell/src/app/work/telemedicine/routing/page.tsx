"use client";

/**
 * Telemedicine routing directory — /work/telemedicine/routing
 *
 * Find the right clinical capability, then open the governed request flow:
 *  - People: real Varapi search via /internal/v1/teleconsult/routing/providers
 *  - Facilities: real Tuso search via /internal/v1/teleconsult/routing/facilities
 *  - Workspaces: real Tuso list via /internal/v1/teleconsult/routing/workspaces
 *  - Virtual hospitals: configured institutions (substrate honesty preserved)
 *
 * Pinning is client-side discovery convenience and never bypasses policy —
 * every target resolves into the same governed teleconsult request flow.
 * ON_CALL/POOL/NATIONAL_POOL routing is honestly absent (BFF 501, HO-1).
 */

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  ArrowLeft,
  ArrowUpRight,
  Building2,
  Compass,
  Hospital,
  Layout,
  Loader2,
  Pin,
  PinOff,
  Search,
  UserRound,
} from "lucide-react";
import {
  useRoutingFacilitySearch,
  useRoutingProviderSearch,
  useRoutingWorkspaces,
  type RoutingFacilityHit,
} from "@/hooks/queries/useTeleconsultRouting";
import { ALL_VIRTUAL_HOSPITALS, SUBSTRATE_STATUS_LABELS } from "@/lib/telemedicine/virtual-hospitals";
import {
  addPin,
  isPinned,
  loadPins,
  removePin,
  type PinnedTarget,
  type PinnedTargetKind,
} from "@/lib/telemedicine/pinning";

type Tab = "people" | "facilities" | "virtual-hospitals";

export default function TelemedicineRoutingPage() {
  const [tab, setTab] = useState<Tab>("people");
  const [query, setQuery] = useState("");
  const [selectedFacility, setSelectedFacility] = useState<RoutingFacilityHit | null>(null);
  const [pins, setPins] = useState<PinnedTarget[]>([]);

  useEffect(() => {
    setPins(loadPins());
  }, []);

  const togglePin = (kind: PinnedTargetKind, id: string, label: string, detail?: string) => {
    setPins((current) =>
      isPinned(current, kind, id)
        ? removePin(current, kind, id)
        : addPin(current, { kind, id, label, detail }),
    );
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header>
        <Link
          href="/work/telemedicine"
          className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" /> Telemedicine operating model
        </Link>
        <div className="flex items-center gap-3">
          <Compass className="h-7 w-7 text-teal-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Routing directory</h1>
            <p className="text-sm text-slate-500">
              Route to the right clinical capability — a person, a facility and its workspaces, or
              a virtual hospital — then open a governed teleconsult request. Pinning speeds up
              discovery; it never bypasses policy.
            </p>
          </div>
        </div>
      </header>

      {/* Pinned strip */}
      {pins.length > 0 ? (
        <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-400">Pinned</p>
          <ul className="flex flex-wrap gap-2">
            {pins.map((pin) => (
              <li
                key={`${pin.kind}:${pin.id}`}
                className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-700"
              >
                <span className="font-medium">{pin.label}</span>
                {pin.detail ? <span className="text-slate-400">{pin.detail}</span> : null}
                <button
                  onClick={() => togglePin(pin.kind, pin.id, pin.label, pin.detail)}
                  className="text-slate-400 hover:text-red-500"
                  aria-label={`Unpin ${pin.label}`}
                >
                  <PinOff className="h-3.5 w-3.5" />
                </button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {/* Tabs */}
      <div className="flex gap-1 border-b border-slate-200">
        {(
          [
            { id: "people", label: "People", icon: UserRound },
            { id: "facilities", label: "Facilities & workspaces", icon: Building2 },
            { id: "virtual-hospitals", label: "Virtual hospitals", icon: Hospital },
          ] as Array<{ id: Tab; label: string; icon: typeof UserRound }>
        ).map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => {
              setTab(id);
              setSelectedFacility(null);
            }}
            className={`inline-flex items-center gap-2 border-b-2 px-4 py-2 text-sm font-medium transition ${
              tab === id
                ? "border-teal-600 text-teal-700"
                : "border-transparent text-slate-500 hover:text-slate-700"
            }`}
          >
            <Icon className="h-4 w-4" /> {label}
          </button>
        ))}
      </div>

      {tab !== "virtual-hospitals" ? (
        <div className="relative w-96 max-w-full">
          <Search className="pointer-events-none absolute left-2 top-2.5 h-4 w-4 text-slate-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={
              tab === "people"
                ? "Search verified providers (Varapi)…"
                : "Search facilities (Tuso)…"
            }
            className="w-full rounded-md border border-slate-300 py-2 pl-8 pr-3 text-sm focus:border-teal-500 focus:outline-none"
          />
        </div>
      ) : null}

      {tab === "people" ? (
        <PeopleTab query={query} pins={pins} togglePin={togglePin} />
      ) : tab === "facilities" ? (
        <FacilitiesTab
          query={query}
          pins={pins}
          togglePin={togglePin}
          selectedFacility={selectedFacility}
          setSelectedFacility={setSelectedFacility}
        />
      ) : (
        <VirtualHospitalsTab pins={pins} togglePin={togglePin} />
      )}

      <footer className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
        On-call, pool and national-pool routing types are intentionally unavailable — the BFF
        fails closed (501) until the pool/on-call directory backend exists. Direct person,
        team/specialty-pool, workspace and facility-service routing are live in the request flow.
      </footer>
    </div>
  );
}

function PeopleTab({
  query,
  pins,
  togglePin,
}: {
  query: string;
  pins: PinnedTarget[];
  togglePin: (kind: PinnedTargetKind, id: string, label: string, detail?: string) => void;
}) {
  const providers = useRoutingProviderSearch(query);

  if (query.trim().length < 2) {
    return (
      <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
        Type at least two characters to search the verified provider register.
      </p>
    );
  }
  if (providers.isLoading) return <LoadingRow label="Searching providers…" />;
  if (providers.isError) {
    return (
      <p className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
        Provider search failed (Varapi unavailable via BFF). Try again.
      </p>
    );
  }
  const hits = providers.data ?? [];
  if (hits.length === 0) {
    return (
      <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
        No verified providers match this search.
      </p>
    );
  }
  return (
    <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white shadow-sm">
      {hits.map((p, index) => {
        const id = String(p.providerPublicId ?? `row-${index}`);
        const name = [p.title, p.givenName, p.familyName].filter(Boolean).join(" ") || id;
        const detail = [p.profession, p.cadre].filter(Boolean).join(" · ");
        const pinned = isPinned(pins, "PROVIDER", id);
        return (
          <li key={id} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <p className="text-sm font-medium text-slate-800">{name}</p>
              <p className="text-xs text-slate-500">
                {detail || "profession not recorded"}
                {p.status ? <span className="ml-2 rounded bg-slate-100 px-1.5 py-0.5">{p.status}</span> : null}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <button
                onClick={() => togglePin("PROVIDER", id, name, detail)}
                className="rounded-md border border-slate-200 p-1.5 text-slate-400 hover:border-teal-300 hover:text-teal-600"
                aria-label={pinned ? `Unpin ${name}` : `Pin ${name}`}
              >
                {pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
              </button>
              <Link
                href="/telemedicine/new"
                className="inline-flex items-center gap-1 rounded-md bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700"
              >
                <ArrowUpRight className="h-3.5 w-3.5" /> Request consult
              </Link>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function FacilitiesTab({
  query,
  pins,
  togglePin,
  selectedFacility,
  setSelectedFacility,
}: {
  query: string;
  pins: PinnedTarget[];
  togglePin: (kind: PinnedTargetKind, id: string, label: string, detail?: string) => void;
  selectedFacility: RoutingFacilityHit | null;
  setSelectedFacility: (f: RoutingFacilityHit | null) => void;
}) {
  const facilities = useRoutingFacilitySearch(query);
  const facilityId =
    selectedFacility != null
      ? String(selectedFacility.facilityId ?? selectedFacility.id ?? "")
      : null;
  const workspaces = useRoutingWorkspaces(facilityId);

  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <div>
        {query.trim().length < 2 ? (
          <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
            Type at least two characters to search the facility register — then drill into its
            workspaces (casualty, wards, theatre, radiology…).
          </p>
        ) : facilities.isLoading ? (
          <LoadingRow label="Searching facilities…" />
        ) : facilities.isError ? (
          <p className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            Facility search failed (Tuso unavailable via BFF). Try again.
          </p>
        ) : (facilities.data ?? []).length === 0 ? (
          <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
            No facilities match this search.
          </p>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white shadow-sm">
            {(facilities.data ?? []).map((f, index) => {
              const id = String(f.facilityId ?? f.id ?? `row-${index}`);
              const name = String(f.name ?? id);
              const detail = [f.facilityType, f.province, f.district].filter(Boolean).join(" · ");
              const pinned = isPinned(pins, "FACILITY", id);
              const isSelected = facilityId === id;
              return (
                <li
                  key={id}
                  className={`flex items-center justify-between gap-3 px-4 py-3 ${isSelected ? "bg-teal-50/50" : ""}`}
                >
                  <button className="text-left" onClick={() => setSelectedFacility(f)}>
                    <p className="text-sm font-medium text-slate-800">{name}</p>
                    <p className="text-xs text-slate-500">{detail || "type not recorded"}</p>
                  </button>
                  <div className="flex shrink-0 items-center gap-2">
                    <button
                      onClick={() => togglePin("FACILITY", id, name, detail)}
                      className="rounded-md border border-slate-200 p-1.5 text-slate-400 hover:border-teal-300 hover:text-teal-600"
                      aria-label={pinned ? `Unpin ${name}` : `Pin ${name}`}
                    >
                      {pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
                    </button>
                    <button
                      onClick={() => setSelectedFacility(f)}
                      className="inline-flex items-center gap-1 rounded-md border border-teal-600 px-3 py-1.5 text-xs font-medium text-teal-700 hover:bg-teal-50"
                    >
                      <Layout className="h-3.5 w-3.5" /> Workspaces
                    </button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <div>
        {!selectedFacility ? (
          <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-400">
            Select a facility to list its routable workspaces.
          </p>
        ) : workspaces.isLoading ? (
          <LoadingRow label="Loading workspaces…" />
        ) : workspaces.isError ? (
          <p className="rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
            Workspace lookup failed for this facility (the routing endpoint requires the numeric
            Tuso facility id).
          </p>
        ) : (workspaces.data ?? []).length === 0 ? (
          <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
            No workspaces are configured for {String(selectedFacility.name ?? "this facility")}.
          </p>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white shadow-sm">
            {(workspaces.data ?? []).map((w, index) => {
              const id = String(w.workspaceId ?? w.id ?? `ws-${index}`);
              const name = String(w.name ?? id);
              const detail = [String(selectedFacility.name ?? ""), w.workspaceType]
                .filter(Boolean)
                .join(" · ");
              const pinned = isPinned(pins, "WORKSPACE", id);
              return (
                <li key={id} className="flex items-center justify-between gap-3 px-4 py-3">
                  <div>
                    <p className="text-sm font-medium text-slate-800">{name}</p>
                    <p className="text-xs text-slate-500">{detail}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    <button
                      onClick={() => togglePin("WORKSPACE", id, name, detail)}
                      className="rounded-md border border-slate-200 p-1.5 text-slate-400 hover:border-teal-300 hover:text-teal-600"
                      aria-label={pinned ? `Unpin ${name}` : `Pin ${name}`}
                    >
                      {pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
                    </button>
                    <Link
                      href="/telemedicine/new"
                      className="inline-flex items-center gap-1 rounded-md bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700"
                    >
                      <ArrowUpRight className="h-3.5 w-3.5" /> Request
                    </Link>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

function VirtualHospitalsTab({
  pins,
  togglePin,
}: {
  pins: PinnedTarget[];
  togglePin: (kind: PinnedTargetKind, id: string, label: string, detail?: string) => void;
}) {
  const routable = useMemo(
    () => ALL_VIRTUAL_HOSPITALS.filter((vh) => vh.substrateStatus === "ROUTABLE_VIA_EXISTING_SEAMS"),
    [],
  );
  const configured = useMemo(
    () => ALL_VIRTUAL_HOSPITALS.filter((vh) => vh.substrateStatus !== "ROUTABLE_VIA_EXISTING_SEAMS"),
    [],
  );

  const renderRow = (vhList: typeof ALL_VIRTUAL_HOSPITALS, canRequest: boolean) => (
    <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white shadow-sm">
      {vhList.map((vh) => {
        const pinned = isPinned(pins, "VIRTUAL_HOSPITAL", vh.id);
        return (
          <li key={vh.id} className="flex items-center justify-between gap-3 px-4 py-3">
            <div>
              <Link
                href={`/work/telemedicine/virtual-hospitals/${vh.id}`}
                className="text-sm font-medium text-slate-800 hover:text-teal-700"
              >
                {vh.name}
              </Link>
              <p className="text-xs text-slate-500">
                {vh.level} · {SUBSTRATE_STATUS_LABELS[vh.substrateStatus]}
              </p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <button
                onClick={() => togglePin("VIRTUAL_HOSPITAL", vh.id, vh.name, vh.level)}
                className="rounded-md border border-slate-200 p-1.5 text-slate-400 hover:border-teal-300 hover:text-teal-600"
                aria-label={pinned ? `Unpin ${vh.name}` : `Pin ${vh.name}`}
              >
                {pinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
              </button>
              {canRequest ? (
                <Link
                  href="/telemedicine/new"
                  className="inline-flex items-center gap-1 rounded-md bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700"
                >
                  <ArrowUpRight className="h-3.5 w-3.5" /> Request
                </Link>
              ) : (
                <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[11px] text-amber-700">
                  not routable yet
                </span>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );

  return (
    <div className="space-y-4">
      <div>
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-400">
          Routable today (via real teleconsult specialty-pool seams)
        </p>
        {renderRow(routable, true)}
      </div>
      <div>
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-slate-400">
          Configured — awaiting backend substrate
        </p>
        {renderRow(configured, false)}
      </div>
    </div>
  );
}

function LoadingRow({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
      <Loader2 className="h-4 w-4 animate-spin" /> {label}
    </div>
  );
}
