"use client";
import { MapPin } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Routes & Places — Health OS §6: safe places, routes, and parks for exercise. */
export default function RoutesPage() {
  return (
    <AppLayout>
      <PageShell title="Routes & Places" subtitle="Find safe places, routes, and parks for exercise" icon={<MapPin className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[
            { title: "Walking Routes", desc: "Discover scenic and safe walking routes nearby" },
            { title: "Running Routes", desc: "Find measured running routes with elevation profiles" },
            { title: "Parks & Open Spaces", desc: "Locate parks, sports fields, and exercise areas" },
            { title: "Safe Cycling Paths", desc: "Cycling-friendly routes and infrastructure" },
          ].map((s) => (
            <div key={s.title} className="rounded-lg border border-gray-200 bg-white p-5">
              <h3 className="font-semibold text-gray-900 mb-1">{s.title}</h3>
              <p className="text-sm text-gray-600">{s.desc}</p>
            </div>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
