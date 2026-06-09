"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { Loader2, MapPin, Search, Star } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { fetchWellnessDiscoverServices, type WellnessDiscoverService } from "@/lib/citizen-wellness-api";
import { useAuthStore } from "@/hooks/useAuthStore";
import { WellnessPlacesMapPanel } from "@/components/maps/WellnessPlacesMapPanel";
import { useLogWellnessActivity } from "@/hooks/queries/useCitizenWellness";
import { useWellnessRoutes } from "@/hooks/queries/useSimba";

/** Routes & Places — surfaces only fields returned by the wellness discover API. */
export default function RoutesPage() {
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("All");
  const [sortBy, setSortBy] = useState<"rating" | "name">("rating");

  const discoverQ = useQuery({
    queryKey: ["wellness-discover-services", "fitness"],
    queryFn: () => fetchWellnessDiscoverServices("fitness"),
  });
  const simbaRoutesQ = useWellnessRoutes();

  const services = discoverQ.data ?? [];
  const simbaRoutes = useMemo(() => {
    const payload = simbaRoutesQ.data;
    if (!payload) return [] as Array<Record<string, unknown>>;
    if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
    if (Array.isArray((payload as { data?: unknown }).data)) {
      return (payload as { data: Array<Record<string, unknown>> }).data;
    }
    return [] as Array<Record<string, unknown>>;
  }, [simbaRoutesQ.data]);

  const categories = useMemo(() => {
    const unique = new Set<string>();
    for (const service of services) {
      if (service.category?.trim()) unique.add(service.category.trim());
    }
    return ["All", ...Array.from(unique).sort()];
  }, [services]);

  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return services
      .filter((service) => {
        if (categoryFilter !== "All" && (service.category ?? "") !== categoryFilter) return false;
        if (!needle) return true;
        const haystack = [
          service.name,
          service.description,
          service.facilityName,
          service.category,
        ]
          .filter(Boolean)
          .join(" ")
          .toLowerCase();
        return haystack.includes(needle);
      })
      .sort((a, b) => {
        if (sortBy === "name") return a.name.localeCompare(b.name);
        const ar = a.rating ?? -1;
        const br = b.rating ?? -1;
        return br - ar;
      });
  }, [services, search, categoryFilter, sortBy]);

  return (
    <AppLayout>
      <PageShell
        title="Routes & Places"
        subtitle="Fitness venues and community exercise services from the wellness discover catalogue"
        icon={<MapPin className="h-6 w-6" />}
      >
        <WellnessPlacesMapPanel />

        {simbaRoutes.length > 0 && (
          <div className="rounded-xl border border-impilo-200 bg-impilo-50 mb-6 p-5">
            <h2 className="font-semibold text-impilo-900 mb-3">Governed wellness routes (Simba)</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              {simbaRoutes.map((route, idx) => (
                <div key={String(route.routeId ?? route.route_id ?? idx)} className="rounded-lg border border-impilo-100 bg-white p-4 text-sm">
                  <p className="font-medium text-gray-900">{String(route.title ?? route.routeCode ?? "Route")}</p>
                  <p className="text-gray-600 mt-1">
                    {route.distanceKm != null || route.distance_km != null
                      ? `${String(route.distanceKm ?? route.distance_km)} km`
                      : "Distance n/a"}
                    {route.elevationM != null || route.elevation_m != null
                      ? ` · ${String(route.elevationM ?? route.elevation_m)} m elevation`
                      : ""}
                    {route.difficulty ? ` · ${String(route.difficulty)}` : ""}
                  </p>
                  {route.facilityName || route.facility_name ? (
                    <p className="text-xs text-gray-500 mt-1">{String(route.facilityName ?? route.facility_name)}</p>
                  ) : null}
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="rounded-xl border border-green-200 bg-gradient-to-br from-green-50 to-emerald-50 mb-6 p-6 shadow-inner">
          <div className="flex items-start gap-3">
            <MapPin className="h-8 w-8 shrink-0 text-green-600" aria-hidden />
            <div>
              <p className="text-green-900 font-medium">
                {discoverQ.isLoading
                  ? "Loading fitness services…"
                  : `${services.length} discoverable service${services.length === 1 ? "" : "s"}`}
              </p>
              <p className="text-xs text-green-800/80 mt-1">
                Simba routes above expose distance, elevation, and difficulty. Discover catalogue below lists
                only live service fields (name, facility, category, description, rating, price).
              </p>
            </div>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row gap-3 mb-5">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by name, facility, or description…"
              className="w-full rounded-xl border border-gray-200 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
            />
          </div>
          <div className="flex gap-2 flex-wrap items-center">
            {categories.map((category) => (
              <button
                key={category}
                type="button"
                onClick={() => setCategoryFilter(category)}
                className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${
                  categoryFilter === category
                    ? "bg-green-600 text-white"
                    : "bg-gray-100 text-gray-600 hover:bg-gray-200"
                }`}
              >
                {category}
              </button>
            ))}
            <button
              type="button"
              onClick={() => setSortBy(sortBy === "rating" ? "name" : "rating")}
              className="rounded-full px-3 py-1.5 text-sm font-medium bg-gray-100 text-gray-600 hover:bg-gray-200"
            >
              Sort: {sortBy === "rating" ? "Top rated" : "Name A–Z"}
            </button>
          </div>
        </div>

        {discoverQ.isError ? (
          <p className="text-center text-red-600 py-8">Could not load wellness discover services.</p>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {filtered.map((service) => (
            <WellnessRouteServiceCard key={service.id} service={service} />
          ))}
          {!discoverQ.isLoading && filtered.length === 0 ? (
            <p className="col-span-full text-center text-gray-400 py-8">No services match your filters</p>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function WellnessRouteServiceCard({ service }: { service: WellnessDiscoverService }) {
  const { user } = useAuthStore();
  const patientId = user?.id;
  const logActivity = useLogWellnessActivity(patientId);
  const [logged, setLogged] = useState(false);
  const priceLabel =
    service.price != null && service.currency
      ? `${service.currency} ${service.price.toFixed(2)}`
      : service.price != null
        ? String(service.price)
        : null;

  async function handleLogVisit() {
    if (!patientId) return;
    await logActivity.mutateAsync({
      patientId,
      activeMinutes: 30,
    });
    setLogged(true);
  }

  return (
    <article className="rounded-xl border border-gray-200 bg-white shadow-sm hover:shadow-md transition-shadow overflow-hidden">
      <div className="p-5">
        <div className="flex items-start gap-3 mb-2">
          <div className="h-11 w-11 rounded-xl bg-green-100 text-green-700 flex items-center justify-center shrink-0">
            <MapPin className="h-6 w-6" aria-hidden />
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="font-semibold text-gray-900">{service.name}</h3>
            {service.facilityName ? (
              <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">
                <MapPin className="h-3 w-3 shrink-0" />
                {service.facilityName}
              </p>
            ) : null}
          </div>
          {service.category ? (
            <span className="rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700 shrink-0">
              {service.category}
            </span>
          ) : null}
        </div>

        {service.description ? (
          <p className="text-sm text-gray-600 mb-3 line-clamp-3">{service.description}</p>
        ) : (
          <p className="text-sm text-gray-400 mb-3 italic">No description provided.</p>
        )}

        <div className="flex flex-wrap items-center gap-3 text-sm mb-3">
          {service.rating != null ? (
            <span className="inline-flex items-center gap-1 text-gray-800">
              <Star className="h-4 w-4 fill-amber-400 text-amber-400" aria-hidden />
              {service.rating.toFixed(1)}
            </span>
          ) : (
            <span className="text-xs text-gray-400">Rating not available</span>
          )}
          {priceLabel ? (
            <span className="text-xs font-medium text-gray-700">{priceLabel}</span>
          ) : null}
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void handleLogVisit()}
            disabled={!patientId || logActivity.isPending || logged}
            className="inline-flex items-center gap-1 rounded-lg bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
          >
            {logActivity.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
            {logged ? "Visit logged" : "Log planned visit"}
          </button>
          <Link
            href="/wellness/dashboard"
            className="inline-flex items-center rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
          >
            Open wellness dashboard
          </Link>
        </div>
      </div>
    </article>
  );
}
