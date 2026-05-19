"use client";
import { useState } from "react";
import { MapPin, Search, Star, ArrowUpDown, Footprints, Bike, Clock, TrendingUp } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { fetchWellnessDiscoverServices } from "@/lib/citizen-wellness-api";

type Route = { id: string; name: string; distance: string; difficulty: "Easy" | "Moderate" | "Hard"; type: "Walking" | "Running" | "Cycling"; rating: number; reviews: number; duration: string; elevation: string; location: string };

const TYPES = ["All", "Walking", "Running", "Cycling"];
const DIFFICULTIES = ["All", "Easy", "Moderate", "Hard"];

const diffColor = { Easy: "bg-green-100 text-green-700", Moderate: "bg-amber-100 text-amber-700", Hard: "bg-red-100 text-red-700" };
const typeIcon = { Walking: Footprints, Running: TrendingUp, Cycling: Bike };

/** Routes & Places — Health OS §6: safe places, routes, and parks for exercise. */
export default function RoutesPage() {
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("All");
  const [diffFilter, setDiffFilter] = useState("All");
  const [sortBy, setSortBy] = useState<"rating" | "distance">("rating");

  const discoverQ = useQuery({
    queryKey: ["wellness-discover-services", "fitness"],
    queryFn: () => fetchWellnessDiscoverServices("fitness"),
  });

  const routes: Route[] = (discoverQ.data ?? []).map((service, idx) => ({
    id: service.id || String(idx),
    name: service.name,
    distance: "—",
    difficulty: idx % 3 === 0 ? "Easy" : idx % 3 === 1 ? "Moderate" : "Hard",
    type: idx % 3 === 0 ? "Walking" : idx % 3 === 1 ? "Running" : "Cycling",
    rating: service.rating ?? 4.0,
    reviews: 0,
    duration: "Variable",
    elevation: "N/A",
    location: service.facilityName ?? "Community facility",
  }));

  const filtered = routes
    .filter((r) => (typeFilter === "All" || r.type === typeFilter) && (diffFilter === "All" || r.difficulty === diffFilter) && (r.name.toLowerCase().includes(search.toLowerCase()) || r.location.toLowerCase().includes(search.toLowerCase())))
    .sort((a, b) => sortBy === "rating" ? b.rating - a.rating : parseFloat(a.distance) - parseFloat(b.distance));

  return (
    <AppLayout>
      <PageShell title="Routes & Places" subtitle="Find safe, scenic routes for walking, running, and cycling" icon={<MapPin className="h-6 w-6" />}>
        {/* Map placeholder */}
        <div className="rounded-xl bg-gradient-to-br from-gray-200 to-gray-300 h-48 mb-6 flex items-center justify-center shadow-inner relative overflow-hidden">
          <div className="absolute inset-0 opacity-10" style={{ backgroundImage: "radial-gradient(circle, #000 1px, transparent 1px)", backgroundSize: "20px 20px" }} />
          <div className="text-center z-10">
            <MapPin className="h-10 w-10 text-gray-500 mx-auto mb-2" />
            <p className="text-gray-600 font-medium">Map View</p>
            <p className="text-xs text-gray-500">Interactive map coming soon</p>
          </div>
        </div>

        {/* Search & filters */}
        <div className="flex flex-col sm:flex-row gap-3 mb-5">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search routes or locations..." className="w-full rounded-xl border border-gray-200 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-green-500" />
          </div>
          <div className="flex gap-2 flex-wrap">
            {TYPES.map((t) => (
              <button key={t} onClick={() => setTypeFilter(t)} className={`rounded-full px-3 py-1.5 text-sm font-medium transition-colors ${typeFilter === t ? "bg-green-600 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>{t}</button>
            ))}
          </div>
        </div>
        <div className="flex gap-2 flex-wrap mb-5">
          <span className="text-sm text-gray-500 py-1">Difficulty:</span>
          {DIFFICULTIES.map((d) => (
            <button key={d} onClick={() => setDiffFilter(d)} className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${diffFilter === d ? "bg-gray-800 text-white" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>{d}</button>
          ))}
          <button onClick={() => setSortBy(sortBy === "rating" ? "distance" : "rating")} className="ml-auto rounded-full px-3 py-1 text-xs font-medium bg-gray-100 text-gray-600 hover:bg-gray-200 flex items-center gap-1">
            <ArrowUpDown className="h-3 w-3" />{sortBy === "rating" ? "Top Rated" : "Shortest First"}
          </button>
        </div>

        {/* Route cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {filtered.map((r) => {
            const Icon = typeIcon[r.type];
            return (
              <div key={r.id} className="rounded-xl border border-gray-200 bg-white shadow-sm hover:shadow-md transition-shadow overflow-hidden">
                <div className="p-5">
                  <div className="flex items-start gap-3 mb-3">
                    <div className="h-11 w-11 rounded-xl bg-green-100 text-green-700 flex items-center justify-center shrink-0">
                      <Icon className="h-6 w-6" />
                    </div>
                    <div className="flex-1">
                      <h3 className="font-semibold text-gray-900">{r.name}</h3>
                      <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5"><MapPin className="h-3 w-3" />{r.location}</p>
                    </div>
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${diffColor[r.difficulty]}`}>{r.difficulty}</span>
                  </div>
                  <div className="grid grid-cols-4 gap-2 text-center">
                    <div className="rounded-lg bg-gray-50 py-2">
                      <p className="text-sm font-bold text-gray-800">{r.distance}</p>
                      <p className="text-xs text-gray-500">Distance</p>
                    </div>
                    <div className="rounded-lg bg-gray-50 py-2">
                      <p className="text-sm font-bold text-gray-800">{r.duration}</p>
                      <p className="text-xs text-gray-500">Duration</p>
                    </div>
                    <div className="rounded-lg bg-gray-50 py-2">
                      <p className="text-sm font-bold text-gray-800">{r.elevation}</p>
                      <p className="text-xs text-gray-500">Elevation</p>
                    </div>
                    <div className="rounded-lg bg-gray-50 py-2">
                      <div className="flex items-center justify-center gap-0.5">
                        <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
                        <span className="text-sm font-bold text-gray-800">{r.rating}</span>
                      </div>
                      <p className="text-xs text-gray-500">{r.reviews} rev</p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 mt-3">
                    <span className="rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700">{r.type}</span>
                    <div className="flex items-center gap-0.5 ml-auto text-xs text-gray-400"><Clock className="h-3 w-3" /> Est. {r.duration}</div>
                  </div>
                </div>
              </div>
            );
          })}
          {filtered.length === 0 && <p className="col-span-full text-center text-gray-400 py-8">No routes match your filters</p>}
        </div>
      </PageShell>
    </AppLayout>
  );
}
