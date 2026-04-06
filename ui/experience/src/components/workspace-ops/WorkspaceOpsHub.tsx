"use client";
import { useState } from "react";
import { LayoutDashboard, Package, Users, Receipt } from "lucide-react";
import { WorkspaceDashboardPanel } from "./WorkspaceDashboardPanel";
import { StockManagementPanel } from "./StockManagementPanel";
import { HRShiftsPanel } from "./HRShiftsPanel";
import { BillingPanel } from "./BillingPanel";

const TABS = [
  { id: "dashboard", label: "Dashboard", icon: LayoutDashboard },
  { id: "stock", label: "Stock", icon: Package },
  { id: "hr", label: "HR & Shifts", icon: Users },
  { id: "billing", label: "Billing", icon: Receipt },
];

export function WorkspaceOpsHub() {
  const [activeTab, setActiveTab] = useState("dashboard");
  return (
    <div className="space-y-4">
      <div className="flex gap-1 border-b">
        {TABS.map(t => (
          <button key={t.id} onClick={() => setActiveTab(t.id)} className={`flex items-center gap-1.5 px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${activeTab === t.id ? "border-blue-600 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700"}`}>
            <t.icon className="w-4 h-4" /> {t.label}
          </button>
        ))}
      </div>
      {activeTab === "dashboard" && <WorkspaceDashboardPanel />}
      {activeTab === "stock" && <StockManagementPanel />}
      {activeTab === "hr" && <HRShiftsPanel />}
      {activeTab === "billing" && <BillingPanel />}
    </div>
  );
}
