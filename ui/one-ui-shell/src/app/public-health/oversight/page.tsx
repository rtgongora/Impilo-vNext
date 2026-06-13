"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ClipboardList, Globe2, Loader2, MapPin } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NdilaMap } from "@/components/ndila/NdilaMap";
import {
  usePublicHealthFieldTasks,
  usePublicHealthMapMarkers,
  usePublicHealthOperationsHome,
  useTransitionPublicHealthFieldTask,
} from "@/hooks/queries/usePublicHealth";
import { useSiteRegistrySites } from "@/hooks/queries/useSiteRegistry";
import { computeMapCenter, DEFAULT_MAP_CENTER } from "@/lib/ndila/geo-map-markers";

export default function PublicHealthOversightPage() {
  const [province, setProvince] = useState("");
  const [district, setDistrict] = useState("");

  const opsHomeQ = usePublicHealthOperationsHome();
  const sitesQ = useSiteRegistrySites({
    province: province.trim() || undefined,
    district: district.trim() || undefined,
    page: 0,
    size: 100,
  });
  const mapMarkersQ = usePublicHealthMapMarkers();
  const fieldTasksQ = usePublicHealthFieldTasks();
  const transitionTask = useTransitionPublicHealthFieldTask();

  const provinces = useMemo(() => {
    const set = new Set<string>();
    for (const site of sitesQ.data ?? []) {
      if (site.province) set.add(site.province);
    }
    for (const marker of mapMarkersQ.data ?? []) {
      void marker;
    }
    return [...set].sort();
  }, [sitesQ.data, mapMarkersQ.data]);

  const districts = useMemo(() => {
    const set = new Set<string>();
    for (const site of sitesQ.data ?? []) {
      if (site.district && (!province.trim() || site.province === province.trim())) {
        set.add(site.district);
      }
    }
    return [...set].sort();
  }, [sitesQ.data, province]);

  const ndilaMarkers = useMemo(
    () =>
      (sitesQ.data ?? [])
        .filter((site) => site.latitude != null && site.longitude != null)
        .map((site) => ({
          id: site.siteId,
          label: site.name,
          latitude: site.latitude!,
          longitude: site.longitude!,
          markerType: "PUBLIC_HEALTH_SITE",
          status: site.regulatoryStatus,
        })),
    [sitesQ.data],
  );

  const center = computeMapCenter(
    ndilaMarkers.map((m) => ({ id: m.id, label: m.label, latitude: m.latitude, longitude: m.longitude })),
    DEFAULT_MAP_CENTER,
  );

  const fieldTasks = fieldTasksQ.data ?? [];
  const openTasks = fieldTasks.filter((t) => t.status !== "COMPLETED" && t.status !== "CANCELLED");

  return (
    <AppLayout>
      <PageShell
        title="National public health oversight"
        subtitle="Province and district drill-down — Indawo site registry, Ndila map, and field operations task queue"
        icon={<Globe2 className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/public-health" className="text-sm text-warning-foreground hover:underline">
            ← Public health operations
          </Link>
        </div>

        <div className="space-y-6">
          {opsHomeQ.data ? (
            <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              {Object.entries(opsHomeQ.data.kpis)
                .slice(0, 4)
                .map(([label, value]) => (
                  <div key={label} className="rounded-xl border border-border bg-card p-4 shadow-sm">
                    <p className="text-xs uppercase tracking-wide text-muted-foreground">{label.replace(/_/g, " ")}</p>
                    <p className="mt-2 text-2xl font-semibold text-foreground">{value}</p>
                  </div>
                ))}
            </section>
          ) : null}

          <section className="rounded-xl border border-border bg-card p-4">
            <h2 className="text-sm font-semibold text-foreground">Jurisdiction drill-down</h2>
            <div className="mt-3 flex flex-wrap gap-3">
              <label className="text-xs text-muted-foreground">
                Province
                <select
                  value={province}
                  onChange={(e) => {
                    setProvince(e.target.value);
                    setDistrict("");
                  }}
                  className="mt-1 block rounded-lg border border-border bg-card px-3 py-2 text-sm min-w-[160px]"
                >
                  <option value="">All provinces</option>
                  {provinces.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-xs text-muted-foreground">
                District
                <select
                  value={district}
                  onChange={(e) => setDistrict(e.target.value)}
                  className="mt-1 block rounded-lg border border-border bg-card px-3 py-2 text-sm min-w-[160px]"
                  disabled={!province.trim() && districts.length === 0}
                >
                  <option value="">All districts</option>
                  {districts.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <p className="mt-2 text-xs text-muted-foreground">
              {sitesQ.isLoading
                ? "Loading Indawo sites…"
                : `${sitesQ.data?.length ?? 0} site(s) · ${ndilaMarkers.length} with coordinates`}
            </p>
          </section>

          <section className="rounded-xl border border-border bg-card p-4">
            <div className="mb-3 flex items-center gap-2">
              <MapPin className="h-4 w-4 text-rose-600" />
              <h2 className="text-sm font-semibold text-foreground">Ndila oversight map</h2>
            </div>
            <NdilaMap
              center={center}
              zoom={ndilaMarkers.length > 1 ? 8 : 6}
              markers={ndilaMarkers}
              height={360}
              mode="DASHBOARD"
              layers={["public-health-sites"]}
              fitToMarkers={ndilaMarkers.length > 0}
            />
            <p className="mt-2 text-xs text-muted-foreground">
              Sites from <code className="text-[10px]">useSiteRegistry</code>
              {province.trim() ? ` · province ${province}` : ""}
              {district.trim() ? ` · district ${district}` : ""}
            </p>
          </section>

          <section className="rounded-xl border border-warning/35 bg-warning-soft/50 p-4">
            <div className="flex items-center gap-2">
              <ClipboardList className="h-4 w-4 text-warning-foreground" />
              <h2 className="text-sm font-semibold text-warning-foreground">Field ops task queue</h2>
            </div>
            <p className="mt-1 text-xs text-warning-foreground">
              GET <code className="text-[10px]">/internal/v1/public-health/field-tasks</code> ·{" "}
              {openTasks.length} open task(s)
            </p>
            {fieldTasksQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-warning-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading field tasks…
              </p>
            ) : fieldTasksQ.isError ? (
              <p className="mt-3 text-sm text-danger">Could not load field operations tasks.</p>
            ) : openTasks.length === 0 ? (
              <p className="mt-3 text-sm text-warning-foreground">No open field tasks in the queue.</p>
            ) : (
              <ul className="mt-3 space-y-2">
                {openTasks.slice(0, 12).map((task) => (
                  <li
                    key={task.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-100 bg-card px-3 py-2 text-xs"
                  >
                    <div>
                      <p className="font-medium text-foreground">{task.title}</p>
                      <p className="text-muted-foreground">
                        {task.taskType} · {task.status} · {task.assignedTo}
                      </p>
                    </div>
                    {task.status === "PLANNED" ? (
                      <button
                        type="button"
                        disabled={transitionTask.isPending}
                        onClick={() =>
                          transitionTask.mutate({ taskId: task.id, status: "IN_PROGRESS" })
                        }
                        className="rounded-md bg-amber-700 px-2 py-1 text-white disabled:opacity-50"
                      >
                        Start
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
            <Link href="/public-health?tab=field" className="mt-3 inline-block text-xs font-medium text-warning-foreground underline">
              Full field operations workspace →
            </Link>
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
