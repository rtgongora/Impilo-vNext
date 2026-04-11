"use client";
import { useState } from "react";
import { Users2, Search, MapPin, Calendar, ChevronRight } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

type Club = { id: number; name: string; type: string; members: number; location: string; nextEvent: string; joined: boolean; color: string };

const CLUBS: Club[] = [
  { id: 1, name: "Sunrise Striders", type: "Walking", members: 134, location: "Green Point Park", nextEvent: "Sat 06:30", joined: true, color: "bg-amber-500" },
  { id: 2, name: "Table Mountain Runners", type: "Running", members: 89, location: "Newlands Forest", nextEvent: "Sun 07:00", joined: true, color: "bg-red-500" },
  { id: 3, name: "Flow Yoga Collective", type: "Yoga", members: 56, location: "Sea Point Studio", nextEvent: "Mon 17:30", joined: false, color: "bg-purple-500" },
  { id: 4, name: "CBD Bootcamp Crew", type: "Fitness", members: 72, location: "Company Gardens", nextEvent: "Tue 06:00", joined: false, color: "bg-emerald-500" },
  { id: 5, name: "Waterfront Wheelers", type: "Cycling", members: 45, location: "V&A Waterfront", nextEvent: "Sat 08:00", joined: false, color: "bg-blue-500" },
  { id: 6, name: "Khayelitsha Pacers", type: "Running", members: 112, location: "Khayelitsha Sports Ground", nextEvent: "Sat 06:00", joined: true, color: "bg-red-500" },
  { id: 7, name: "Pilates & Stretch", type: "Fitness", members: 38, location: "Observatory Community Hall", nextEvent: "Wed 18:00", joined: false, color: "bg-emerald-500" },
  { id: 8, name: "Nature Walk Society", type: "Walking", members: 201, location: "Kirstenbosch Gardens", nextEvent: "Sun 09:00", joined: false, color: "bg-amber-500" },
];

const TYPES = ["All", "Walking", "Running", "Fitness", "Yoga", "Cycling"];

/** Clubs & Communities — Health OS §6: social participation for healthier living. */
export default function ClubsPage() {
  const [clubs, setClubs] = useState(CLUBS);
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("All");

  const toggle = (id: number) => setClubs((prev) => prev.map((c) => c.id === id ? { ...c, joined: !c.joined } : c));
  const myClubs = clubs.filter((c) => c.joined);
  const filtered = clubs.filter((c) => (filter === "All" || c.type === filter) && c.name.toLowerCase().includes(search.toLowerCase()));

  return (
    <AppLayout>
      <PageShell title="Clubs & Communities" subtitle="Walking, running, and fitness clubs near you" icon={<Users2 className="h-6 w-6" />}>
        {/* My Clubs */}
        {myClubs.length > 0 && (
          <div className="mb-6">
            <h2 className="font-semibold text-gray-800 mb-3">My Clubs</h2>
            <div className="flex gap-3 overflow-x-auto pb-2">
              {myClubs.map((c) => (
                <div key={c.id} className="flex-shrink-0 w-56 rounded-xl bg-gradient-to-br from-blue-600 to-indigo-600 text-white p-4 shadow-lg">
                  <div className="flex items-center gap-2 mb-2">
                    <span className={`h-8 w-8 rounded-full ${c.color} flex items-center justify-center text-xs font-bold text-white`}>{c.type[0]}</span>
                    <div><p className="font-semibold text-sm leading-tight">{c.name}</p><p className="text-xs text-blue-200">{c.type}</p></div>
                  </div>
                  <div className="flex items-center gap-1 text-xs text-blue-200 mt-2"><Calendar className="h-3 w-3" /> Next: {c.nextEvent}</div>
                  <div className="flex items-center gap-1 text-xs text-blue-200 mt-1"><Users2 className="h-3 w-3" /> {c.members} members</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Search & filter */}
        <div className="flex flex-col sm:flex-row gap-3 mb-5">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search clubs..." className="w-full rounded-xl border border-gray-200 pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="flex gap-2 flex-wrap">
            {TYPES.map((t) => (
              <button key={t} onClick={() => setFilter(t)} className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${filter === t ? "bg-blue-600 text-white shadow-sm" : "bg-gray-100 text-gray-600 hover:bg-gray-200"}`}>{t}</button>
            ))}
          </div>
        </div>

        {/* Club cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map((c) => (
            <div key={c.id} className="rounded-xl border border-gray-200 bg-white shadow-sm hover:shadow-md transition-shadow overflow-hidden">
              <div className={`h-2 ${c.color}`} />
              <div className="p-4">
                <div className="flex items-start justify-between mb-2">
                  <div>
                    <h3 className="font-semibold text-gray-900">{c.name}</h3>
                    <span className="text-xs font-medium text-gray-500">{c.type}</span>
                  </div>
                  <span className={`h-8 w-8 rounded-full ${c.color} flex items-center justify-center text-xs font-bold text-white`}>{c.type[0]}</span>
                </div>
                <div className="space-y-1.5 text-sm text-gray-600 mb-3">
                  <div className="flex items-center gap-2"><MapPin className="h-3.5 w-3.5 text-gray-400" />{c.location}</div>
                  <div className="flex items-center gap-2"><Calendar className="h-3.5 w-3.5 text-gray-400" />Next: {c.nextEvent}</div>
                  <div className="flex items-center gap-2"><Users2 className="h-3.5 w-3.5 text-gray-400" />{c.members} members</div>
                </div>
                <button onClick={() => toggle(c.id)} className={`w-full rounded-lg py-2 text-sm font-medium transition-colors flex items-center justify-center gap-1 ${c.joined ? "bg-gray-100 text-gray-600 hover:bg-gray-200" : "bg-blue-600 text-white hover:bg-blue-700"}`}>
                  {c.joined ? "Leave Club" : "Join Club"}<ChevronRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          ))}
          {filtered.length === 0 && <p className="col-span-full text-center text-gray-400 py-8">No clubs match your search</p>}
        </div>
      </PageShell>
    </AppLayout>
  );
}
