"use client";
import { UtensilsCrossed } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/** Diet & Nutrition — Health OS §6: how people eat. */
export default function DietPage() {
  return (
    <AppLayout>
      <PageShell title="Diet & Nutrition" subtitle="Dietary support, healthy eating guidance, and food tracking" icon={<UtensilsCrossed className="h-6 w-6" />}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[
            { title: "Food Journal", desc: "Log meals, snacks, and water intake" },
            { title: "Nutrition Goals", desc: "Set and track daily nutrition targets" },
            { title: "Dietary Guidance", desc: "Evidence-based eating recommendations" },
            { title: "Recipes & Meal Plans", desc: "Healthy recipes and weekly meal plans" },
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
