'use client';

import { useState } from 'react';
import { useFacilityStore } from '@/hooks/useFacilityStore';
import {
  BarChart3, Package, Clock, DollarSign, Settings,
  ArrowLeft, Building2, Stethoscope, Shield, Headphones,
} from 'lucide-react';
import { WorkspaceDashboardPanel } from './WorkspaceDashboardPanel';
import { StockManagementPanel } from './StockManagementPanel';
import { HRShiftsPanel } from './HRShiftsPanel';
import { BillingPanel } from './BillingPanel';

export type WorkspaceOpsType = 'clinical' | 'admin' | 'support' | 'pharmacy' | 'lab' | 'radiology';

type OpsTab = 'dashboard' | 'stock' | 'hr-shifts' | 'billing' | 'settings';

interface WorkspaceOpsHubProps {
  /** Required: name of the current workspace */
  workspaceName: string;
  /** Required: type determines visible tabs and header styling */
  workspaceType: WorkspaceOpsType;
  facilityName?: string;
  onBack?: () => void;
  visibleTabs?: OpsTab[];
}

const WORKSPACE_TYPE_TABS: Record<WorkspaceOpsType, OpsTab[]> = {
  clinical: ['dashboard', 'stock', 'hr-shifts', 'billing'],
  admin: ['dashboard', 'hr-shifts', 'billing', 'stock', 'settings'],
  support: ['dashboard', 'stock', 'hr-shifts', 'settings'],
  pharmacy: ['dashboard', 'stock', 'billing'],
  lab: ['dashboard', 'stock', 'hr-shifts'],
  radiology: ['dashboard', 'stock', 'hr-shifts', 'billing'],
};

const TAB_CONFIG: Record<OpsTab, { label: string; icon: React.ComponentType<{ className?: string }> }> = {
  dashboard: { label: 'Dashboard', icon: BarChart3 },
  stock: { label: 'Stock & Supplies', icon: Package },
  'hr-shifts': { label: 'HR & Shifts', icon: Clock },
  billing: { label: 'Billing & Finance', icon: DollarSign },
  settings: { label: 'Settings', icon: Settings },
};

const WORKSPACE_TYPE_META: Record<WorkspaceOpsType, { icon: React.ComponentType<{ className?: string }>; color: string; label: string }> = {
  clinical: { icon: Stethoscope, color: 'bg-impilo-500', label: 'Clinical Workspace' },
  admin: { icon: Shield, color: 'bg-amber-500', label: 'Administration Workspace' },
  support: { icon: Headphones, color: 'bg-green-500', label: 'Support Services Workspace' },
  pharmacy: { icon: Package, color: 'bg-emerald-500', label: 'Pharmacy Workspace' },
  lab: { icon: BarChart3, color: 'bg-purple-500', label: 'Laboratory Workspace' },
  radiology: { icon: Building2, color: 'bg-cyan-500', label: 'Radiology Workspace' },
};

export function WorkspaceOpsHub({
  workspaceName,
  workspaceType,
  facilityName,
  onBack,
  visibleTabs,
}: WorkspaceOpsHubProps) {
  const facilityId = useFacilityStore((s) => s.facility?.id);
  const tabs = visibleTabs || WORKSPACE_TYPE_TABS[workspaceType];
  const [activeTab, setActiveTab] = useState<OpsTab>(tabs[0]);
  const meta = WORKSPACE_TYPE_META[workspaceType];
  const TypeIcon = meta.icon;

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Header */}
      <div className="flex items-center gap-3 mb-4 shrink-0">
        {onBack && (
          <button
            onClick={onBack}
            className="h-8 w-8 flex items-center justify-center rounded-md hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
          </button>
        )}
        <div className={`h-9 w-9 rounded-lg ${meta.color} flex items-center justify-center text-white`}>
          <TypeIcon className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold truncate">{workspaceName}</h2>
            <span className="px-2 py-0.5 rounded border border-gray-200 text-[10px] text-gray-600 shrink-0">
              {meta.label}
            </span>
          </div>
          {facilityName && (
            <p className="text-xs text-gray-500 flex items-center gap-1">
              <Building2 className="h-3 w-3" />{facilityName}
            </p>
          )}
        </div>
      </div>

      {/* Tab buttons */}
      <div className="flex gap-1 border-b border-gray-200 shrink-0">
        {tabs.map(tabId => {
          const cfg = TAB_CONFIG[tabId];
          const Icon = cfg.icon;
          return (
            <button
              key={tabId}
              onClick={() => setActiveTab(tabId)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tabId
                  ? 'border-impilo-500 text-impilo-500'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">{cfg.label}</span>
            </button>
          );
        })}
      </div>

      {/* Tab content */}
      <div className="flex-1 overflow-auto mt-3">
        {activeTab === 'dashboard' && <WorkspaceDashboardPanel facilityId={facilityId} />}
        {activeTab === 'stock' && <StockManagementPanel />}
        {activeTab === 'hr-shifts' && <HRShiftsPanel />}
        {activeTab === 'billing' && <BillingPanel />}
        {activeTab === 'settings' && (
          <div className="text-center py-12 text-gray-400 text-sm">
            Workspace settings coming soon
          </div>
        )}
      </div>
    </div>
  );
}
