"use client";
import { useState } from "react";
import { Package, AlertTriangle, Search, ArrowDown, ArrowUp, CheckCircle2 } from "lucide-react";
const STOCK = [
  { id: 1, name: "Paracetamol 500mg", category: "Analgesic", stock: 120, reorder: 200, unit: "tablets" },
  { id: 2, name: "Amoxicillin 500mg", category: "Antibiotic", stock: 340, reorder: 150, unit: "capsules" },
  { id: 3, name: "Metformin 500mg", category: "Antidiabetic", stock: 85, reorder: 100, unit: "tablets" },
  { id: 4, name: "Amlodipine 5mg", category: "Antihypertensive", stock: 210, reorder: 100, unit: "tablets" },
  { id: 5, name: "Normal Saline 1L", category: "IV Fluid", stock: 45, reorder: 50, unit: "bags" },
  { id: 6, name: "Surgical Gloves (L)", category: "Consumable", stock: 500, reorder: 200, unit: "pairs" },
  { id: 7, name: "Gauze Pads 4x4", category: "Wound Care", stock: 30, reorder: 100, unit: "packs" },
  { id: 8, name: "TLD (TDF/3TC/DTG)", category: "Antiretroviral", stock: 180, reorder: 100, unit: "tablets" },
];
export function StockManagementPanel() {
  const [search, setSearch] = useState("");
  const filtered = STOCK.filter(s => s.name.toLowerCase().includes(search.toLowerCase()) || s.category.toLowerCase().includes(search.toLowerCase()));
  const lowStock = STOCK.filter(s => s.stock < s.reorder);
  return (
    <div className="space-y-4">
      {lowStock.length > 0 && <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 flex items-center gap-2"><AlertTriangle className="w-4 h-4 text-amber-600" /><span className="text-sm text-amber-700 font-medium">{lowStock.length} items below reorder level</span></div>}
      <div className="relative"><Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search stock..." className="w-full pl-10 pr-3 py-2 text-sm border rounded-lg" /></div>
      <div className="bg-white rounded-lg border overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50"><tr><th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Item</th><th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Category</th><th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Stock</th><th className="px-4 py-2 text-right text-xs font-medium text-gray-500">Reorder</th><th className="px-4 py-2 text-center text-xs font-medium text-gray-500">Status</th></tr></thead>
          <tbody className="divide-y divide-gray-100">{filtered.map(s => (
            <tr key={s.id} className="hover:bg-gray-50">
              <td className="px-4 py-2.5 font-medium text-gray-900">{s.name}</td><td className="px-4 py-2.5 text-gray-500">{s.category}</td>
              <td className="px-4 py-2.5 text-right font-medium">{s.stock} {s.unit}</td><td className="px-4 py-2.5 text-right text-gray-500">{s.reorder}</td>
              <td className="px-4 py-2.5 text-center">{s.stock < s.reorder ? <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-medium">Low</span> : <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs font-medium">OK</span>}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>
    </div>
  );
}
