"use client";
import { useMemo, useState } from "react";
import { UtensilsCrossed, Plus, Droplets } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDietEntries, useRecordDietEntry } from "@/hooks/queries/useSimba";

const TARGETS = { calories: 2200, protein: 120, carbs: 280, fat: 75 };
type Totals = { calories: number; protein: number; carbs: number; fat: number; waterMl: number };

/** Diet & Nutrition — Health OS §6: how people eat. */
export default function DietPage() {
  const cpid = useAuthStore((s) => s.user?.id ?? null);
  const dietQ = useDietEntries(cpid);
  const recordDiet = useRecordDietEntry();
  const [form, setForm] = useState({ name: "", calories: "", protein: "", carbs: "", fat: "", water: "" });

  const meals = useMemo(() => {
    const payload = dietQ.data;
    if (!payload) return [] as Array<Record<string, unknown>>;
    if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
    if (Array.isArray((payload as { data?: unknown }).data)) {
      return (payload as { data: Array<Record<string, unknown>> }).data;
    }
    return [] as Array<Record<string, unknown>>;
  }, [dietQ.data]);

  const totals = meals.reduce<Totals>(
    (a, m) => ({
      calories: a.calories + Number(m.calories ?? 0),
      protein: a.protein + Number(m.proteinG ?? m.protein_g ?? 0),
      carbs: a.carbs + Number(m.carbsG ?? m.carbs_g ?? 0),
      fat: a.fat + Number(m.fatG ?? m.fat_g ?? 0),
      waterMl: a.waterMl + Number(m.waterMl ?? m.water_ml ?? 0),
    }),
    { calories: 0, protein: 0, carbs: 0, fat: 0, waterMl: 0 } satisfies Totals,
  );

  const addEntry = async () => {
    if (!cpid || !form.name) return;
    await recordDiet.mutateAsync({
      person_cpid: cpid,
      meal_type: form.name,
      description: form.name,
      calories: Number(form.calories || 0),
      protein_g: Number(form.protein || 0),
      carbs_g: Number(form.carbs || 0),
      fat_g: Number(form.fat || 0),
      water_ml: Number(form.water || 0),
      recorded_at: new Date().toISOString(),
    });
    setForm({ name: "", calories: "", protein: "", carbs: "", fat: "", water: "" });
  };

  const ProgressBar = ({ label, value, max, color }: { label: string; value: number; max: number; color: string }) => (
    <div className="space-y-1">
      <div className="flex justify-between text-xs font-medium text-gray-600">
        <span>{label}</span>
        <span>{value} / {max}</span>
      </div>
      <div className="h-3 rounded-full bg-gray-100 overflow-hidden">
        <div className={`h-full rounded-full transition-all ${color}`} style={{ width: `${Math.min((value / max) * 100, 100)}%` }} />
      </div>
    </div>
  );

  return (
    <AppLayout>
      <PageShell title="Diet & Nutrition" subtitle="Track meals, water intake, and daily nutrition" icon={<UtensilsCrossed className="h-6 w-6" />}>
        {/* Nutrition summary */}
        <div className="rounded-xl bg-gradient-to-r from-emerald-500 to-teal-500 p-5 text-white shadow-lg mb-6">
          <h2 className="font-semibold text-lg mb-4">Today&apos;s Nutrition</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {([
              ["Calories", totals.calories, TARGETS.calories, "kcal"],
              ["Protein", totals.protein, TARGETS.protein, "g"],
              ["Carbs", totals.carbs, TARGETS.carbs, "g"],
              ["Fat", totals.fat, TARGETS.fat, "g"],
            ] as Array<[string, number, number, string]>).map(([l, v, m, u]) => (
              <div key={l} className="text-center">
                <p className="text-2xl font-bold">{v}<span className="text-sm font-normal ml-0.5">{u}</span></p>
                <div className="h-2 mt-1 rounded-full bg-white/25 overflow-hidden"><div className="h-full rounded-full bg-white transition-all" style={{ width: `${Math.min((v / m) * 100, 100)}%` }} /></div>
                <p className="text-xs mt-1 text-white/80">{l} &middot; {Math.round((v / m) * 100)}%</p>
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-4">
            <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3 bg-gray-50 border-b border-gray-100">
                <h3 className="font-semibold text-gray-800">Record nutrition entry</h3>
                <Plus className="h-4 w-4 text-emerald-700" />
              </div>
              <div className="px-4 py-3 bg-emerald-50 border-b border-emerald-100 flex flex-wrap gap-2 items-end">
                <input placeholder="Meal / item" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="rounded-lg border px-3 py-1.5 text-sm flex-1 min-w-[140px]" />
                {(["calories", "protein", "carbs", "fat", "water"] as const).map((f) => (
                  <input key={f} placeholder={f === "water" ? "water ml" : f} type="number" value={form[f]} onChange={(e) => setForm({ ...form, [f]: e.target.value })} className="rounded-lg border px-3 py-1.5 text-sm w-24" />
                ))}
                <button onClick={() => void addEntry()} className="rounded-lg bg-emerald-600 text-white px-4 py-1.5 text-sm font-medium hover:bg-emerald-700">Save</button>
              </div>
              <div className="divide-y divide-gray-50">
                {dietQ.isLoading && <p className="px-4 py-3 text-sm text-gray-400">Loading entries...</p>}
                {!dietQ.isLoading && meals.length === 0 && <p className="px-4 py-3 text-sm text-gray-400">No entries yet</p>}
                {meals.map((m, idx) => (
                  <div key={idx} className="px-4 py-2.5 flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-800">{String(m.description ?? m.mealType ?? m.meal_type ?? "Meal")}</p>
                      <p className="text-xs text-gray-400">{String(m.recordedAt ?? m.recorded_at ?? "")}</p>
                    </div>
                    <p className="text-sm font-semibold text-gray-600">{Number(m.calories ?? 0)} kcal</p>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Water tracker + targets sidebar */}
          <div className="space-y-4">
            <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-5 text-center">
              <Droplets className="h-8 w-8 text-impilo-400 mx-auto mb-2" />
              <h3 className="font-semibold text-gray-800 mb-1">Water Intake</h3>
              <p className="text-3xl font-bold text-impilo-500">{Math.round(totals.waterMl / 250)}<span className="text-base font-normal text-gray-500"> / 8 glasses</span></p>
              <div className="h-3 mt-2 rounded-full bg-blue-100 overflow-hidden"><div className="h-full rounded-full bg-impilo-500 transition-all" style={{ width: `${Math.min((totals.waterMl / 2000) * 100, 100)}%` }} /></div>
            </div>
            <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-5 space-y-3">
              <h3 className="font-semibold text-gray-800">Daily Targets</h3>
              <ProgressBar label="Calories" value={totals.calories} max={TARGETS.calories} color="bg-orange-500" />
              <ProgressBar label="Protein" value={totals.protein} max={TARGETS.protein} color="bg-red-500" />
              <ProgressBar label="Carbs" value={totals.carbs} max={TARGETS.carbs} color="bg-amber-500" />
              <ProgressBar label="Fat" value={totals.fat} max={TARGETS.fat} color="bg-purple-500" />
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
