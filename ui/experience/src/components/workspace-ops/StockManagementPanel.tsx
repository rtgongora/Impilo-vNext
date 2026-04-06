'use client';

import { useState } from 'react';
import {
  Package, ShoppingCart, Truck, ClipboardCheck, AlertTriangle,
  Plus, Search, ArrowRightLeft, Calendar, TrendingDown, CheckCircle2,
  XCircle, Clock, X,
} from 'lucide-react';

// ─── Types ───

type StockTab = 'inventory' | 'orders' | 'receiving' | 'transfers' | 'alerts';

interface InventoryItem {
  id: string;
  name: string;
  code: string;
  category: string;
  onHand: number;
  reorderLevel: number;
  unit: string;
  expiry: string | null;
  location: string;
  status: 'ok' | 'low' | 'out' | 'expiring';
}

// ─── Mock Data ───

const INVENTORY_ITEMS: InventoryItem[] = [
  { id: '1', name: 'Paracetamol 500mg', code: 'MED-001', category: 'Medication', onHand: 450, reorderLevel: 200, unit: 'tablets', expiry: '2026-12-15', location: 'Pharmacy Store', status: 'ok' },
  { id: '2', name: 'Surgical Gloves (L)', code: 'SUP-012', category: 'Supplies', onHand: 85, reorderLevel: 100, unit: 'boxes', expiry: '2027-03-20', location: 'Main Store', status: 'low' },
  { id: '3', name: 'IV Normal Saline 1L', code: 'MED-044', category: 'Medication', onHand: 0, reorderLevel: 50, unit: 'bags', expiry: null, location: 'Pharmacy Store', status: 'out' },
  { id: '4', name: 'Amoxicillin 250mg', code: 'MED-008', category: 'Medication', onHand: 320, reorderLevel: 150, unit: 'capsules', expiry: '2026-06-10', location: 'Pharmacy Store', status: 'expiring' },
  { id: '5', name: 'Bandage Crepe 10cm', code: 'SUP-003', category: 'Supplies', onHand: 200, reorderLevel: 100, unit: 'rolls', expiry: '2028-01-01', location: 'Ward Store', status: 'ok' },
  { id: '6', name: 'Diazepam 5mg', code: 'MED-CS01', category: 'Controlled', onHand: 24, reorderLevel: 20, unit: 'ampoules', expiry: '2026-09-30', location: 'Pharmacy Safe', status: 'ok' },
  { id: '7', name: 'Oxygen Cylinder (Size F)', code: 'EQP-007', category: 'Equipment', onHand: 3, reorderLevel: 5, unit: 'cylinders', expiry: null, location: 'Gas Store', status: 'low' },
  { id: '8', name: 'Disposable Syringes 5ml', code: 'SUP-021', category: 'Supplies', onHand: 1200, reorderLevel: 500, unit: 'pieces', expiry: '2027-08-15', location: 'Main Store', status: 'ok' },
];

const PURCHASE_ORDERS = [
  { id: 'PO-20260401-0001', supplier: 'MedSupply SA', items: 5, total: 'R12,450', status: 'pending_approval', date: '2026-04-01', priority: 'normal' },
  { id: 'PO-20260403-0002', supplier: 'PharmaWholesale', items: 12, total: 'R34,800', status: 'approved', date: '2026-04-03', priority: 'urgent' },
  { id: 'PO-20260404-0003', supplier: 'SurgicalDirect', items: 3, total: 'R8,200', status: 'shipped', date: '2026-04-04', priority: 'normal' },
  { id: 'PO-20260405-0004', supplier: 'MedSupply SA', items: 8, total: 'R22,100', status: 'delivered', date: '2026-04-05', priority: 'normal' },
  { id: 'PO-20260406-0005', supplier: 'LabEquip Co', items: 2, total: 'R67,500', status: 'draft', date: '2026-04-06', priority: 'normal' },
];

const PENDING_RECEIPTS = [
  { id: 'GRN-001', poNumber: 'PO-20260404-0003', supplier: 'SurgicalDirect', items: 3, expectedDate: '2026-04-07', status: 'in_transit' },
  { id: 'GRN-002', poNumber: 'PO-20260403-0002', supplier: 'PharmaWholesale', items: 12, expectedDate: '2026-04-06', status: 'arrived' },
];

const TRANSFERS = [
  { id: 'TRF-001', from: 'Main Store', to: 'Ward Store A', items: 4, status: 'pending', requestedBy: 'Sr. Moyo', date: '2026-04-06' },
  { id: 'TRF-002', from: 'Pharmacy Store', to: 'Emergency', items: 2, status: 'completed', requestedBy: 'Dr. Nkomo', date: '2026-04-05' },
];

// ─── Helpers ───

function getStatusBadge(status: string) {
  const map: Record<string, { label: string; classes: string }> = {
    ok: { label: 'In Stock', classes: 'bg-green-100 text-green-700' },
    low: { label: 'Low Stock', classes: 'bg-amber-100 text-amber-700' },
    out: { label: 'Out of Stock', classes: 'bg-red-100 text-red-700' },
    expiring: { label: 'Expiring Soon', classes: 'bg-orange-100 text-orange-700' },
    draft: { label: 'Draft', classes: 'bg-gray-100 text-gray-600' },
    pending_approval: { label: 'Pending Approval', classes: 'bg-amber-100 text-amber-700' },
    approved: { label: 'Approved', classes: 'bg-green-100 text-green-700' },
    shipped: { label: 'Shipped', classes: 'bg-blue-100 text-blue-700' },
    delivered: { label: 'Delivered', classes: 'bg-green-100 text-green-700' },
    in_transit: { label: 'In Transit', classes: 'bg-blue-100 text-blue-700' },
    arrived: { label: 'Arrived', classes: 'bg-green-100 text-green-700' },
    pending: { label: 'Pending', classes: 'bg-amber-100 text-amber-700' },
    completed: { label: 'Completed', classes: 'bg-green-100 text-green-700' },
  };
  const cfg = map[status] || { label: status, classes: 'bg-gray-100 text-gray-600' };
  return <span className={`px-2 py-0.5 rounded text-xs font-medium ${cfg.classes}`}>{cfg.label}</span>;
}

// ─── Component ───

export function StockManagementPanel() {
  const [activeTab, setActiveTab] = useState<StockTab>('inventory');
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [newOrderOpen, setNewOrderOpen] = useState(false);

  const filteredItems = INVENTORY_ITEMS.filter(item => {
    const matchesSearch = item.name.toLowerCase().includes(searchQuery.toLowerCase()) || item.code.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory = categoryFilter === 'all' || item.category === categoryFilter;
    return matchesSearch && matchesCategory;
  });

  const lowStockCount = INVENTORY_ITEMS.filter(i => i.status === 'low' || i.status === 'out').length;
  const expiringCount = INVENTORY_ITEMS.filter(i => i.status === 'expiring').length;

  const tabs: { key: StockTab; label: string; icon: React.ComponentType<{ className?: string }>; badge?: number }[] = [
    { key: 'inventory', label: 'Inventory', icon: Package },
    { key: 'orders', label: 'Purchase Orders', icon: ShoppingCart },
    { key: 'receiving', label: 'Receiving', icon: Truck },
    { key: 'transfers', label: 'Transfers', icon: ArrowRightLeft },
    { key: 'alerts', label: 'Alerts', icon: AlertTriangle, badge: lowStockCount > 0 ? lowStockCount : undefined },
  ];

  return (
    <div className="space-y-3">
      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><Package className="h-4 w-4 text-blue-500" /><span className="text-xs text-gray-500">Total Items</span></div>
          <p className="text-lg font-bold">{INVENTORY_ITEMS.length}</p>
        </div>
        <div className={`bg-white border rounded-lg pt-3 pb-2 px-3 ${lowStockCount > 0 ? 'border-amber-300' : 'border-gray-200'}`}>
          <div className="flex items-center gap-2"><TrendingDown className="h-4 w-4 text-amber-500" /><span className="text-xs text-gray-500">Low/Out</span></div>
          <p className="text-lg font-bold text-amber-600">{lowStockCount}</p>
        </div>
        <div className="bg-white border border-gray-200 rounded-lg pt-3 pb-2 px-3">
          <div className="flex items-center gap-2"><ShoppingCart className="h-4 w-4 text-green-500" /><span className="text-xs text-gray-500">Open Orders</span></div>
          <p className="text-lg font-bold">{PURCHASE_ORDERS.filter(po => !['delivered', 'cancelled'].includes(po.status)).length}</p>
        </div>
        <div className={`bg-white border rounded-lg pt-3 pb-2 px-3 ${expiringCount > 0 ? 'border-red-300' : 'border-gray-200'}`}>
          <div className="flex items-center gap-2"><Calendar className="h-4 w-4 text-red-500" /><span className="text-xs text-gray-500">Expiring Soon</span></div>
          <p className="text-lg font-bold text-red-600">{expiringCount}</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {tabs.map(tab => {
          const Icon = tab.icon;
          return (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tab.key ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {tab.label}
              {tab.badge && (
                <span className="ml-1 px-1.5 py-0.5 rounded-full bg-red-100 text-red-700 text-[10px] font-medium">{tab.badge}</span>
              )}
            </button>
          );
        })}
      </div>

      {/* Inventory Tab */}
      {activeTab === 'inventory' && (
        <div>
          <div className="flex items-center gap-2 mb-3">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-gray-400" />
              <input
                placeholder="Search items..."
                className="w-full pl-9 pr-3 h-9 text-sm border border-gray-200 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
              />
            </div>
            <select
              value={categoryFilter}
              onChange={e => setCategoryFilter(e.target.value)}
              className="h-9 border border-gray-200 rounded-md px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="all">All Categories</option>
              <option value="Medication">Medication</option>
              <option value="Supplies">Supplies</option>
              <option value="Controlled">Controlled</option>
              <option value="Equipment">Equipment</option>
            </select>
            <button
              onClick={() => setNewOrderOpen(true)}
              className="inline-flex items-center gap-1 px-3 h-9 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              <Plus className="h-3.5 w-3.5" />New Order
            </button>
          </div>
          <div className="space-y-1 max-h-[400px] overflow-auto">
            {filteredItems.map(item => (
              <div key={item.id} className="flex items-center justify-between p-3 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors">
                <div className="flex items-center gap-3 min-w-0">
                  <div className={`h-8 w-8 rounded-lg flex items-center justify-center text-xs font-bold ${
                    item.status === 'out' ? 'bg-red-100 text-red-600' : item.status === 'low' ? 'bg-amber-100 text-amber-600' : 'bg-blue-100 text-blue-600'
                  }`}>
                    {item.code.split('-')[0]}
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-medium truncate">{item.name}</p>
                    <p className="text-xs text-gray-500">{item.code} &middot; {item.location}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4 shrink-0">
                  <div className="text-right">
                    <p className="text-sm font-semibold">{item.onHand} <span className="text-xs font-normal text-gray-500">{item.unit}</span></p>
                    {item.onHand <= item.reorderLevel && <p className="text-[10px] text-amber-600">Reorder at {item.reorderLevel}</p>}
                  </div>
                  {getStatusBadge(item.status)}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Purchase Orders Tab */}
      {activeTab === 'orders' && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <p className="text-sm text-gray-500">{PURCHASE_ORDERS.length} orders</p>
            <button
              onClick={() => setNewOrderOpen(true)}
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm bg-blue-600 text-white rounded-md hover:bg-blue-700"
            >
              <Plus className="h-3.5 w-3.5" />Create Order
            </button>
          </div>
          <div className="space-y-2 max-h-[400px] overflow-auto">
            {PURCHASE_ORDERS.map(po => (
              <div key={po.id} className="bg-white border border-gray-200 rounded-lg py-3 px-4 hover:bg-gray-50 cursor-pointer">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="text-sm font-semibold">{po.id}</p>
                      {po.priority === 'urgent' && <span className="px-2 py-0.5 rounded bg-red-100 text-red-700 text-[10px] font-medium">Urgent</span>}
                    </div>
                    <p className="text-xs text-gray-500">{po.supplier} &middot; {po.items} items &middot; {po.total}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{po.date}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {getStatusBadge(po.status)}
                    {po.status === 'pending_approval' && (
                      <button className="px-2 py-1 text-xs border border-gray-200 rounded hover:bg-gray-100">Approve</button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Receiving Tab */}
      {activeTab === 'receiving' && (
        <div>
          <p className="text-sm text-gray-500 mb-3">{PENDING_RECEIPTS.length} pending deliveries</p>
          <div className="space-y-2">
            {PENDING_RECEIPTS.map(grn => (
              <div key={grn.id} className="bg-white border border-gray-200 rounded-lg py-3 px-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-semibold">{grn.id}</p>
                    <p className="text-xs text-gray-500">PO: {grn.poNumber} &middot; {grn.supplier} &middot; {grn.items} items</p>
                    <p className="text-xs text-gray-500">Expected: {grn.expectedDate}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {getStatusBadge(grn.status)}
                    {grn.status === 'arrived' && (
                      <button className="inline-flex items-center gap-1 px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700">
                        <ClipboardCheck className="h-3 w-3" />Receive
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
            {PENDING_RECEIPTS.length === 0 && (
              <div className="text-center py-8 text-gray-400 text-sm">No pending deliveries</div>
            )}
          </div>
        </div>
      )}

      {/* Transfers Tab */}
      {activeTab === 'transfers' && (
        <div>
          <div className="flex items-center justify-between mb-3">
            <p className="text-sm text-gray-500">{TRANSFERS.length} transfers</p>
            <button className="inline-flex items-center gap-1 px-3 py-1.5 text-sm border border-gray-200 rounded-md hover:bg-gray-50">
              <ArrowRightLeft className="h-3.5 w-3.5" />Request Transfer
            </button>
          </div>
          <div className="space-y-2">
            {TRANSFERS.map(t => (
              <div key={t.id} className="bg-white border border-gray-200 rounded-lg py-3 px-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-semibold">{t.id}</p>
                    <p className="text-xs text-gray-500">{t.from} &rarr; {t.to} &middot; {t.items} items</p>
                    <p className="text-xs text-gray-500">By {t.requestedBy} &middot; {t.date}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {getStatusBadge(t.status)}
                    {t.status === 'pending' && (
                      <button className="px-2 py-1 text-xs border border-gray-200 rounded hover:bg-gray-100">Approve</button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Alerts Tab */}
      {activeTab === 'alerts' && (
        <div className="space-y-2">
          {INVENTORY_ITEMS.filter(i => i.status !== 'ok').map(item => (
            <div
              key={item.id}
              className={`bg-white border-l-4 border rounded-lg py-3 px-4 ${
                item.status === 'out' ? 'border-l-red-500' : item.status === 'low' ? 'border-l-amber-500' : 'border-l-orange-400'
              }`}
            >
              <div className="flex items-center justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    {item.status === 'out' ? <XCircle className="h-4 w-4 text-red-500" /> : item.status === 'expiring' ? <Clock className="h-4 w-4 text-orange-500" /> : <AlertTriangle className="h-4 w-4 text-amber-500" />}
                    <p className="text-sm font-medium">{item.name}</p>
                  </div>
                  <p className="text-xs text-gray-500 ml-6">
                    {item.status === 'out' ? 'Out of stock - immediate reorder required' : item.status === 'low' ? `${item.onHand} ${item.unit} remaining (reorder level: ${item.reorderLevel})` : `Expires ${item.expiry}`}
                  </p>
                </div>
                <button className="inline-flex items-center gap-1 px-2 py-1 text-xs border border-gray-200 rounded hover:bg-gray-100">
                  <ShoppingCart className="h-3 w-3" />Reorder
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* New Order Dialog */}
      {newOrderOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className="absolute inset-0 bg-black/30" onClick={() => setNewOrderOpen(false)} />
          <div className="relative bg-white rounded-xl shadow-xl w-full max-w-md p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold">Create Purchase Order</h3>
              <button onClick={() => setNewOrderOpen(false)} className="p-1 rounded hover:bg-gray-100">
                <X className="h-5 w-5 text-gray-500" />
              </button>
            </div>
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Supplier</label>
                <select className="w-full h-9 border border-gray-200 rounded-md px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="">Select supplier</option>
                  <option value="medsupply">MedSupply SA</option>
                  <option value="pharma">PharmaWholesale</option>
                  <option value="surgical">SurgicalDirect</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Priority</label>
                <select className="w-full h-9 border border-gray-200 rounded-md px-3 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="normal">Normal</option>
                  <option value="urgent">Urgent</option>
                  <option value="emergency">Emergency</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-700 mb-1">Notes</label>
                <textarea
                  placeholder="Order notes..."
                  className="w-full h-20 border border-gray-200 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setNewOrderOpen(false)} className="px-4 py-2 text-sm border border-gray-200 rounded-lg hover:bg-gray-50">
                Cancel
              </button>
              <button onClick={() => setNewOrderOpen(false)} className="px-4 py-2 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                Create Order
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
