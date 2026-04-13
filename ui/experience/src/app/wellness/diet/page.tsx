"use client";
import { useState } from "react";
import { UtensilsCrossed, Plus, Minus, Droplets, X } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

type Meal = { id: number; time: string; name: string; calories: number; protein: number; carbs: number; fat: number };
type MealSlot = "Breakfast" | "Lunch" | "Dinner" | "Snacks";

const INITIAL_MEALS: Record<MealSlot, Meal[]> = {
  Breakfast: [
    { id: 1, time: "07:15", name: "Oats with banana & honey", calories: 340, protein: 10, carbs: 58, fat: 8 },
    { id: 2, time: "07:15", name: "Black coffee", calories: 5, protein: 0, carbs: 1, fat: 0 },
  ],
  Lunch: [
    { id: 3, time: "12:30", name: "Grilled chicken wrap", calories: 480, protein: 35, carbs: 42, fat: 16 },
    { id: 4, time: "12:30", name: "Side salad with olive oil", calories: 120, protein: 2, carbs: 8, fat: 9 },
  ],
  Dinner: [
    { id: 5, time: "18:45", name: "Salmon with roasted vegetables", calories: 520, protein: 38, carbs: 30, fat: 22 },
  ],
  Snacks: [
    { id: 6, time: "10:00", name: "Apple with almond butter", calories: 210, protein: 5, carbs: 26, fat: 12 },
  ],
};

const TARGETS = { calories: 2200, protein: 120, carbs: 280, fat: 75 };

/** Diet & Nutrition — Health OS §6: how people eat. */
export default function DietPage() {
  const [meals, setMeals] = useState(INITIAL_MEALS);
  const [water, setWater] = useState(5);
  const [adding, setAdding] = useState<MealSlot | null>(null);
  const [form, setForm] = useState({ name: "", calories: "", protein: "", carbs: "", fat: "" });

  const allMeals = Object.values(meals).flat();
  const totals = allMeals.reduce(
    (a, m) => ({ calories: a.calories + m.calories, protein: a.protein + m.protein, carbs: a.carbs + m.carbs, fat: a.fat + m.fat }),
    { calories: 0, protein: 0, carbs: 0, fat: 0 },
  );

  const addEntry = (slot: MealSlot) => {
    if (!form.name) return;
    const entry: Meal = { id: Date.now(), time: new Date().toTimeString().slice(0, 5), name: form.name, calories: +form.calories || 0, protein: +form.protein || 0, carbs: +form.carbs || 0, fat: +form.fat || 0 };
    setMeals((prev) => ({ ...prev, [slot]: [...prev[slot], entry] }));
    setForm({ name: "", calories: "", protein: "", carbs: "", fat: "" });
    setAdding(null);
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
            {([["Calories", totals.calories, TARGETS.calories, "kcal"], ["Protein", totals.protein, TARGETS.protein, "g"], ["Carbs", totals.carbs, TARGETS.carbs, "g"], ["Fat", totals.fat, TARGETS.fat, "g"]] as const).map(([l, v, m, u]) => (
              <div key={l} className="text-center">
                <p className="text-2xl font-bold">{v}<span className="text-sm font-normal ml-0.5">{u}</span></p>
                <div className="h-2 mt-1 rounded-full bg-white/25 overflow-hidden"><div className="h-full rounded-full bg-white transition-all" style={{ width: `${Math.min((v / m) * 100, 100)}%` }} /></div>
                <p className="text-xs mt-1 text-white/80">{l} &middot; {Math.round((v / m) * 100)}%</p>
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Meal log */}
          <div className="lg:col-span-2 space-y-4">
            {(["Breakfast", "Lunch", "Dinner", "Snacks"] as MealSlot[]).map((slot) => (
              <div key={slot} className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
                <div className="flex items-center justify-between px-4 py-3 bg-gray-50 border-b border-gray-100">
                  <h3 className="font-semibold text-gray-800">{slot}</h3>
                  <button onClick={() => setAdding(adding === slot ? null : slot)} className="text-emerald-600 hover:text-emerald-700 text-sm font-medium flex items-center gap-1">
                    {adding === slot ? <X className="h-4 w-4" /> : <Plus className="h-4 w-4" />}{adding === slot ? "Cancel" : "Add"}
                  </button>
                </div>
                {adding === slot && (
                  <div className="px-4 py-3 bg-emerald-50 border-b border-emerald-100 flex flex-wrap gap-2 items-end">
                    <input placeholder="Food item" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className="rounded-lg border px-3 py-1.5 text-sm flex-1 min-w-[140px]" />
                    {(["calories", "protein", "carbs", "fat"] as const).map((f) => (
                      <input key={f} placeholder={f.charAt(0).toUpperCase() + f.slice(1)} type="number" value={form[f]} onChange={(e) => setForm({ ...form, [f]: e.target.value })} className="rounded-lg border px-3 py-1.5 text-sm w-20" />
                    ))}
                    <button onClick={() => addEntry(slot)} className="rounded-lg bg-emerald-600 text-white px-4 py-1.5 text-sm font-medium hover:bg-emerald-700">Add</button>
                  </div>
                )}
                <div className="divide-y divide-gray-50">
                  {meals[slot].length === 0 && <p className="px-4 py-3 text-sm text-gray-400">No entries yet</p>}
                  {meals[slot].map((m) => (
                    <div key={m.id} className="px-4 py-2.5 flex items-center justify-between">
                      <div><p className="text-sm font-medium text-gray-800">{m.name}</p><p className="text-xs text-gray-400">{m.time}</p></div>
                      <p className="text-sm font-semibold text-gray-600">{m.calories} kcal</p>
                    </div>
                  ))}
                </div>
              </div>
            ))}
          </div>

          {/* Water tracker + targets sidebar */}
          <div className="space-y-4">
            <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-5 text-center">
              <Droplets className="h-8 w-8 text-impilo-400 mx-auto mb-2" />
              <h3 className="font-semibold text-gray-800 mb-1">Water Intake</h3>
              <p className="text-3xl font-bold text-impilo-500">{water}<span className="text-base font-normal text-gray-500"> / 8 glasses</span></p>
              <div className="h-3 mt-2 rounded-full bg-blue-100 overflow-hidden"><div className="h-full rounded-full bg-impilo-500 transition-all" style={{ width: `${Math.min((water / 8) * 100, 100)}%` }} /></div>
              <div className="flex justify-center gap-4 mt-4">
                <button onClick={() => setWater(Math.max(0, water - 1))} className="h-10 w-10 rounded-full bg-impilo-100 text-impilo-600 flex items-center justify-center hover:bg-impilo-200"><Minus className="h-5 w-5" /></button>
                <button onClick={() => setWater(water + 1)} className="h-10 w-10 rounded-full bg-impilo-500 text-white flex items-center justify-center hover:bg-impilo-500"><Plus className="h-5 w-5" /></button>
              </div>
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
