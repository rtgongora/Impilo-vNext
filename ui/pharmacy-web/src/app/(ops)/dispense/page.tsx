"use client";

import { Suspense, useState, useEffect, useCallback } from "react";
import { useSearchParams } from "next/navigation";
import {
  pharmacyApi,
  type DispenseOrderDetail,
  type DispenseItem,
  type DispensePriority,
  type DispenseStatus,
  type ItemStatus,
  type PickItemData,
  type SubstitutionOption,
  type BarcodeResult,
  type PickupMethod,
} from "@/lib/pharmacyApi";

const PRIORITY_BADGE: Record<DispensePriority, string> = {
  ROUTINE: "badge-priority-routine",
  URGENT: "badge-priority-urgent",
  STAT: "badge-priority-stat",
  ASAP: "badge-priority-asap",
};

const STATUS_BADGE: Record<DispenseStatus, string> = {
  PENDING: "badge-status-pending",
  ACCEPTED: "badge-status-accepted",
  PICKING: "badge-status-picking",
  PARTIAL: "badge-status-partial",
  COMPLETED: "badge-status-completed",
  BACKORDERED: "badge-status-backordered",
  REVERSED: "badge-status-reversed",
  CANCELLED: "badge-status-cancelled",
};

const ITEM_STATUS_BADGE: Record<ItemStatus, string> = {
  PENDING: "badge-item-pending",
  PICKED: "badge-item-picked",
  SUBSTITUTED: "badge-item-substituted",
  BACKORDERED: "badge-item-backordered",
  RETURNED: "badge-item-returned",
};

function DispensePageContent() {
  const searchParams = useSearchParams();
  const orderId = searchParams.get("id");

  const [order, setOrder] = useState<DispenseOrderDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Barcode scan
  const [barcodeInput, setBarcodeInput] = useState("");
  const [barcodeResult, setBarcodeResult] = useState<BarcodeResult | null>(null);
  const [barcodeError, setBarcodeError] = useState<string | null>(null);

  // Pick state
  const [pickItems, setPickItems] = useState<Record<string, PickItemData>>({});

  // Substitution modal
  const [substitutingItemId, setSubstitutingItemId] = useState<string | null>(null);
  const [substitutionOptions, setSubstitutionOptions] = useState<SubstitutionOption[]>([]);
  const [selectedSubstitution, setSelectedSubstitution] = useState<SubstitutionOption | null>(null);
  const [substitutionReason, setSubstitutionReason] = useState("");
  const [substitutionQty, setSubstitutionQty] = useState(1);
  const [substitutionBatch, setSubstitutionBatch] = useState("");
  const [substitutionExpiry, setSubstitutionExpiry] = useState("");

  // Backorder
  const [showBackorderForm, setShowBackorderForm] = useState(false);
  const [backorderNotes, setBackorderNotes] = useState("");
  const [backorderRestockDate, setBackorderRestockDate] = useState("");

  // Pickup
  const [showPickupForm, setShowPickupForm] = useState(false);
  const [pickupMethod, setPickupMethod] = useState<PickupMethod>("OTP");
  const [delegatedName, setDelegatedName] = useState("");
  const [delegatedIdNumber, setDelegatedIdNumber] = useState("");
  const [delegatedRelationship, setDelegatedRelationship] = useState("");

  const loadOrder = useCallback(async () => {
    if (!orderId) return;
    try {
      setLoading(true);
      setError(null);
      const data = await pharmacyApi.getDispenseOrder(orderId);
      setOrder(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load dispense order");
    } finally {
      setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    loadOrder();
  }, [loadOrder]);

  const handleAccept = async () => {
    if (!orderId) return;
    setActionLoading(true);
    setError(null);
    try {
      const updated = await pharmacyApi.acceptOrder(orderId);
      setOrder(updated);
      setSuccessMessage("Order accepted. Ready for picking.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to accept order");
    } finally {
      setActionLoading(false);
    }
  };

  const handlePick = async () => {
    if (!orderId) return;
    const items = Object.values(pickItems).filter((p) => p.quantityPicked > 0);
    if (items.length === 0) {
      setError("Select at least one item to pick with a quantity greater than 0.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      const updated = await pharmacyApi.pickItems(orderId, { items });
      setOrder(updated);
      setPickItems({});
      setSuccessMessage("Items picked successfully.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to pick items");
    } finally {
      setActionLoading(false);
    }
  };

  const handlePartialDispense = async () => {
    if (!orderId) return;
    setActionLoading(true);
    setError(null);
    try {
      const updated = await pharmacyApi.partialDispense(orderId);
      setOrder(updated);
      setSuccessMessage("Partial dispense recorded.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to record partial dispense");
    } finally {
      setActionLoading(false);
    }
  };

  const handleCompleteDispense = async () => {
    if (!orderId) return;
    setActionLoading(true);
    setError(null);
    try {
      const updated = await pharmacyApi.completeDispense(orderId);
      setOrder(updated);
      setSuccessMessage("Dispense completed.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to complete dispense");
    } finally {
      setActionLoading(false);
    }
  };

  const handleBackorder = async () => {
    if (!orderId || !backorderNotes.trim()) {
      setError("Backorder notes are required.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      const updated = await pharmacyApi.backorder(orderId, {
        notes: backorderNotes,
        expectedRestockDate: backorderRestockDate || undefined,
      });
      setOrder(updated);
      setShowBackorderForm(false);
      setBackorderNotes("");
      setBackorderRestockDate("");
      setSuccessMessage("Order backordered.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to backorder");
    } finally {
      setActionLoading(false);
    }
  };

  const handleBarcodeScan = async () => {
    if (!barcodeInput.trim()) return;
    setBarcodeError(null);
    setBarcodeResult(null);
    try {
      const result = await pharmacyApi.lookupBarcode(barcodeInput.trim());
      setBarcodeResult(result);
    } catch (err) {
      setBarcodeError(err instanceof Error ? err.message : "Barcode lookup failed");
    }
  };

  const handleBarcodeKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleBarcodeScan();
    }
  };

  const openSubstitutionModal = async (itemId: string) => {
    setSubstitutingItemId(itemId);
    setSelectedSubstitution(null);
    setSubstitutionReason("");
    setSubstitutionQty(1);
    setSubstitutionBatch("");
    setSubstitutionExpiry("");
    try {
      const options = await pharmacyApi.getSubstitutionOptions(itemId);
      setSubstitutionOptions(options);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load substitution options");
      setSubstitutingItemId(null);
    }
  };

  const handleSubstitute = async () => {
    if (!substitutingItemId || !selectedSubstitution || !substitutionReason.trim()) {
      setError("Please select a substitution and provide a reason.");
      return;
    }
    setActionLoading(true);
    setError(null);
    try {
      await pharmacyApi.substituteItem(substitutingItemId, {
        replacementDrugCode: selectedSubstitution.drugCode,
        replacementDrugName: selectedSubstitution.drugName,
        replacementDrugForm: selectedSubstitution.drugForm,
        replacementDrugStrength: selectedSubstitution.drugStrength,
        reason: substitutionReason,
        quantity: substitutionQty,
        batchNumber: substitutionBatch,
        expiryDate: substitutionExpiry,
      });
      setSubstitutingItemId(null);
      setSuccessMessage("Item substituted successfully.");
      await loadOrder();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to substitute item");
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreatePickup = async () => {
    if (!orderId) return;
    setActionLoading(true);
    setError(null);
    try {
      const delegatedTo =
        delegatedName.trim()
          ? {
              name: delegatedName,
              idNumber: delegatedIdNumber,
              relationship: delegatedRelationship,
            }
          : undefined;
      await pharmacyApi.createPickup(orderId, {
        method: pickupMethod,
        delegatedTo,
      });
      setShowPickupForm(false);
      setDelegatedName("");
      setDelegatedIdNumber("");
      setDelegatedRelationship("");
      setSuccessMessage("Pickup proof created.");
      await loadOrder();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create pickup proof");
    } finally {
      setActionLoading(false);
    }
  };

  const updatePickItem = (item: DispenseItem, field: keyof PickItemData, value: string | number) => {
    setPickItems((prev) => ({
      ...prev,
      [item.id]: {
        itemId: item.id,
        quantityPicked: field === "quantityPicked" ? (value as number) : (prev[item.id]?.quantityPicked ?? 0),
        batchNumber: field === "batchNumber" ? (value as string) : (prev[item.id]?.batchNumber ?? item.batchNumber ?? ""),
        expiryDate: field === "expiryDate" ? (value as string) : (prev[item.id]?.expiryDate ?? item.expiryDate ?? ""),
        barcode: field === "barcode" ? (value as string) : (prev[item.id]?.barcode ?? item.barcode ?? ""),
      },
    }));
  };

  if (!orderId) {
    return (
      <div className="p-8">
        <div className="card p-12 text-center">
          <h2 className="text-lg font-semibold text-neutral-900 mb-2">No Order Selected</h2>
          <p className="text-sm text-neutral-500">
            Select an order from the worklist to view dispense details.
          </p>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-neutral-200 rounded w-1/3" />
          <div className="h-4 bg-neutral-200 rounded w-2/3" />
          <div className="card p-6 space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-10 bg-neutral-100 rounded" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="p-8">
        <div className="card p-12 text-center text-neutral-500">
          {error || "Order not found."}
        </div>
      </div>
    );
  }

  const canAccept = order.status === "PENDING";
  const canPick = order.status === "ACCEPTED" || order.status === "PICKING";
  const canPartial = order.status === "PICKING";
  const canComplete = order.status === "PICKING" || order.status === "PARTIAL";
  const canBackorder = order.status === "ACCEPTED" || order.status === "PICKING";
  const canCreatePickup = order.status === "COMPLETED" && !order.pickupProof;

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-neutral-900">Dispense Detail</h1>
          <p className="text-sm text-neutral-500 mt-1">
            Order {order.id.substring(0, 8)}... &mdash; OROS Ref: {order.orosOrderNumber}
          </p>
        </div>
        <button onClick={loadOrder} className="btn-secondary">
          Refresh
        </button>
      </div>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-800">
          {error}
        </div>
      )}

      {successMessage && (
        <div className="mb-4 p-3 rounded-lg bg-emerald-50 border border-emerald-200 text-sm text-emerald-800">
          {successMessage}
          <button
            onClick={() => setSuccessMessage(null)}
            className="ml-2 text-emerald-600 hover:text-emerald-800 font-medium"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Order header card */}
      <div className="card p-5 mb-6">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div>
            <span className="text-xs text-neutral-500 block mb-1">Patient CPID</span>
            <span className="font-mono text-sm font-medium text-neutral-900">
              {order.patientCpid}
            </span>
          </div>
          <div>
            <span className="text-xs text-neutral-500 block mb-1">OROS Reference</span>
            <span className="font-mono text-sm text-neutral-900">
              {order.orosOrderNumber}
            </span>
          </div>
          <div>
            <span className="text-xs text-neutral-500 block mb-1">Status</span>
            <span className={STATUS_BADGE[order.status] || "badge bg-neutral-100 text-neutral-600"}>
              {order.status}
            </span>
          </div>
          <div>
            <span className="text-xs text-neutral-500 block mb-1">Priority</span>
            <span className={PRIORITY_BADGE[order.priority]}>
              {order.priority}
            </span>
          </div>
        </div>
        {order.pharmacistName && (
          <div className="mt-3 text-xs text-neutral-500">
            Pharmacist: <span className="text-neutral-700 font-medium">{order.pharmacistName}</span>
          </div>
        )}
      </div>

      {/* Action buttons */}
      <div className="flex gap-2 mb-6 flex-wrap">
        {canAccept && (
          <button onClick={handleAccept} disabled={actionLoading} className="btn-primary">
            {actionLoading ? "Accepting..." : "Accept Order"}
          </button>
        )}
        {canPick && (
          <button onClick={handlePick} disabled={actionLoading} className="btn-primary">
            {actionLoading ? "Picking..." : "Confirm Pick"}
          </button>
        )}
        {canPartial && (
          <button onClick={handlePartialDispense} disabled={actionLoading} className="btn-warning">
            {actionLoading ? "Processing..." : "Partial Dispense"}
          </button>
        )}
        {canComplete && (
          <button onClick={handleCompleteDispense} disabled={actionLoading} className="btn-primary">
            {actionLoading ? "Completing..." : "Complete Dispense"}
          </button>
        )}
        {canBackorder && (
          <button
            onClick={() => setShowBackorderForm(!showBackorderForm)}
            className="btn-danger"
          >
            Backorder
          </button>
        )}
        {canCreatePickup && (
          <button
            onClick={() => setShowPickupForm(!showPickupForm)}
            className="btn-secondary"
          >
            Create Pickup Proof
          </button>
        )}
      </div>

      {/* Backorder form */}
      {showBackorderForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Backorder</h2>
          <div>
            <label htmlFor="backorderNotes" className="block text-sm font-medium text-neutral-700 mb-1">
              Notes
            </label>
            <textarea
              id="backorderNotes"
              value={backorderNotes}
              onChange={(e) => setBackorderNotes(e.target.value)}
              placeholder="Reason for backorder..."
              rows={3}
              className="input-field"
            />
          </div>
          <div>
            <label htmlFor="restockDate" className="block text-sm font-medium text-neutral-700 mb-1">
              Expected Restock Date (optional)
            </label>
            <input
              id="restockDate"
              type="date"
              value={backorderRestockDate}
              onChange={(e) => setBackorderRestockDate(e.target.value)}
              className="input-field w-48"
            />
          </div>
          <div className="flex gap-2">
            <button onClick={handleBackorder} disabled={actionLoading} className="btn-danger">
              {actionLoading ? "Processing..." : "Confirm Backorder"}
            </button>
            <button onClick={() => setShowBackorderForm(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Pickup proof form */}
      {showPickupForm && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Create Pickup Proof</h2>
          <div>
            <label htmlFor="pickupMethod" className="block text-sm font-medium text-neutral-700 mb-1">
              Pickup Method
            </label>
            <select
              id="pickupMethod"
              value={pickupMethod}
              onChange={(e) => setPickupMethod(e.target.value as PickupMethod)}
              className="select-field w-48"
            >
              <option value="OTP">OTP</option>
              <option value="QR">QR Code</option>
              <option value="BIOMETRIC">Biometric</option>
            </select>
          </div>
          <div>
            <h3 className="text-sm font-medium text-neutral-700 mb-2">Delegated Pickup (optional)</h3>
            <div className="grid grid-cols-3 gap-3">
              <input
                type="text"
                value={delegatedName}
                onChange={(e) => setDelegatedName(e.target.value)}
                placeholder="Delegate name"
                className="input-field"
              />
              <input
                type="text"
                value={delegatedIdNumber}
                onChange={(e) => setDelegatedIdNumber(e.target.value)}
                placeholder="ID number"
                className="input-field"
              />
              <input
                type="text"
                value={delegatedRelationship}
                onChange={(e) => setDelegatedRelationship(e.target.value)}
                placeholder="Relationship"
                className="input-field"
              />
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={handleCreatePickup} disabled={actionLoading} className="btn-primary">
              {actionLoading ? "Creating..." : "Create Pickup"}
            </button>
            <button onClick={() => setShowPickupForm(false)} className="btn-secondary">
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Existing pickup proof */}
      {order.pickupProof && (
        <div className="card p-5 mb-6">
          <h3 className="text-sm font-semibold text-neutral-700 mb-3">Pickup Proof</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
            <div>
              <span className="text-xs text-neutral-500 block">Method</span>
              <span className="font-medium">{order.pickupProof.method}</span>
            </div>
            <div>
              <span className="text-xs text-neutral-500 block">Token</span>
              <span className="font-mono text-xs">{order.pickupProof.token}</span>
            </div>
            <div>
              <span className="text-xs text-neutral-500 block">Claimed</span>
              <span className={order.pickupProof.claimed ? "text-emerald-700 font-medium" : "text-neutral-500"}>
                {order.pickupProof.claimed
                  ? `Yes (${new Date(order.pickupProof.claimedAt!).toLocaleString()})`
                  : "No"}
              </span>
            </div>
            <div>
              <span className="text-xs text-neutral-500 block">Expires</span>
              <span className="text-xs">{new Date(order.pickupProof.expiresAt).toLocaleString()}</span>
            </div>
          </div>
          {order.pickupProof.delegatedTo && (
            <div className="mt-2 text-xs text-neutral-500">
              Delegated to: {order.pickupProof.delegatedTo.name} ({order.pickupProof.delegatedTo.relationship})
            </div>
          )}
        </div>
      )}

      {/* Barcode scan */}
      <div className="card p-5 mb-6">
        <h3 className="text-sm font-semibold text-neutral-700 mb-3">Barcode Scanner</h3>
        <div className="flex gap-3">
          <input
            type="text"
            value={barcodeInput}
            onChange={(e) => setBarcodeInput(e.target.value)}
            onKeyDown={handleBarcodeKeyDown}
            placeholder="Scan or enter barcode..."
            className="input-field flex-1"
            autoFocus={canPick}
          />
          <button onClick={handleBarcodeScan} className="btn-secondary">
            Lookup
          </button>
        </div>
        {barcodeError && (
          <div className="mt-2 text-sm text-red-600">{barcodeError}</div>
        )}
        {barcodeResult && (
          <div className="mt-3 p-3 bg-neutral-50 rounded-lg text-sm">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              <div>
                <span className="text-xs text-neutral-500 block">Drug</span>
                <span className="font-medium">{barcodeResult.drugName}</span>
              </div>
              <div>
                <span className="text-xs text-neutral-500 block">Code</span>
                <span className="font-mono text-xs">{barcodeResult.drugCode}</span>
              </div>
              <div>
                <span className="text-xs text-neutral-500 block">Batch</span>
                <span className="font-mono text-xs">{barcodeResult.batchNumber}</span>
              </div>
              <div>
                <span className="text-xs text-neutral-500 block">Expiry</span>
                <span className="text-xs">{barcodeResult.expiryDate}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Items table */}
      <div className="card overflow-hidden mb-6">
        <div className="px-5 py-3 border-b border-neutral-200 bg-neutral-50">
          <h3 className="text-sm font-semibold text-neutral-700">Dispense Items</h3>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-200 bg-neutral-50">
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Drug</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Qty Requested</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Qty Dispensed</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Remaining</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Status</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Batch</th>
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Expiry</th>
              {canPick && (
                <>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Pick Qty</th>
                  <th className="text-left px-4 py-3 font-medium text-neutral-600">Pick Batch</th>
                </>
              )}
              <th className="text-left px-4 py-3 font-medium text-neutral-600">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-100">
            {order.items.map((item) => (
              <tr key={item.id} className="hover:bg-neutral-50 transition-colors">
                <td className="px-4 py-3">
                  <div className="text-neutral-900 font-medium">{item.drugName}</div>
                  <div className="text-xs text-neutral-500">
                    {item.drugCode}
                    {item.drugStrength && ` | ${item.drugStrength}`}
                    {item.drugForm && ` | ${item.drugForm}`}
                  </div>
                  {item.substitutedFromName && (
                    <div className="text-xs text-purple-600 mt-0.5">
                      Substituted from: {item.substitutedFromName}
                    </div>
                  )}
                  {item.fefoSuggestion && (
                    <div className="text-xs text-emerald-600 mt-0.5">
                      FEFO: Batch {item.fefoSuggestion.batchNumber} (exp {item.fefoSuggestion.expiryDate},
                      {" "}{item.fefoSuggestion.quantityAvailable} avail)
                    </div>
                  )}
                </td>
                <td className="px-4 py-3 text-neutral-900">{item.quantityRequested}</td>
                <td className="px-4 py-3 text-neutral-900">{item.quantityDispensed}</td>
                <td className="px-4 py-3 text-neutral-600">{item.quantityRemaining}</td>
                <td className="px-4 py-3">
                  <span className={ITEM_STATUS_BADGE[item.status] || "badge bg-neutral-100 text-neutral-600"}>
                    {item.status}
                  </span>
                </td>
                <td className="px-4 py-3 font-mono text-xs text-neutral-600">
                  {item.batchNumber || "--"}
                </td>
                <td className="px-4 py-3 text-xs text-neutral-600">
                  {item.expiryDate || "--"}
                </td>
                {canPick && (
                  <>
                    <td className="px-4 py-3">
                      <input
                        type="number"
                        min={0}
                        max={item.quantityRemaining}
                        value={pickItems[item.id]?.quantityPicked ?? 0}
                        onChange={(e) =>
                          updatePickItem(item, "quantityPicked", parseInt(e.target.value) || 0)
                        }
                        className="input-field w-20"
                      />
                    </td>
                    <td className="px-4 py-3">
                      <input
                        type="text"
                        value={pickItems[item.id]?.batchNumber ?? item.batchNumber ?? ""}
                        onChange={(e) => updatePickItem(item, "batchNumber", e.target.value)}
                        placeholder="Batch #"
                        className="input-field w-28"
                      />
                    </td>
                  </>
                )}
                <td className="px-4 py-3">
                  {item.status === "PENDING" && canPick && (
                    <button
                      onClick={() => openSubstitutionModal(item.id)}
                      className="text-xs text-purple-600 hover:text-purple-800 font-medium"
                    >
                      Substitute
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {order.items.length === 0 && (
              <tr>
                <td colSpan={canPick ? 10 : 8} className="px-4 py-12 text-center text-neutral-500">
                  No items in this dispense order.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Substitution modal */}
      {substitutingItemId && (
        <div className="card p-6 mb-6 space-y-4">
          <h2 className="text-lg font-semibold text-neutral-900">Substitute Item</h2>

          {substitutionOptions.length > 0 ? (
            <div className="border border-neutral-200 rounded-lg overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-neutral-200 bg-neutral-50">
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Drug</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Form</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Strength</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Stock</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Equivalence</th>
                    <th className="text-left px-3 py-2 font-medium text-neutral-600">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-100">
                  {substitutionOptions.map((opt) => (
                    <tr key={opt.drugCode} className="hover:bg-neutral-50">
                      <td className="px-3 py-2">{opt.drugName}</td>
                      <td className="px-3 py-2 text-xs">{opt.drugForm}</td>
                      <td className="px-3 py-2 text-xs">{opt.drugStrength}</td>
                      <td className="px-3 py-2 text-xs">{opt.stockOnHand}</td>
                      <td className="px-3 py-2 text-xs">{opt.therapeuticEquivalence}</td>
                      <td className="px-3 py-2">
                        <button
                          onClick={() => setSelectedSubstitution(opt)}
                          className={`text-xs font-medium ${
                            selectedSubstitution?.drugCode === opt.drugCode
                              ? "text-emerald-700"
                              : "text-indigo-600 hover:text-indigo-800"
                          }`}
                        >
                          {selectedSubstitution?.drugCode === opt.drugCode ? "Selected" : "Select"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-sm text-neutral-500">No substitution options available.</p>
          )}

          {selectedSubstitution && (
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <div>
                <label className="block text-sm font-medium text-neutral-700 mb-1">Quantity</label>
                <input
                  type="number"
                  min={1}
                  value={substitutionQty}
                  onChange={(e) => setSubstitutionQty(parseInt(e.target.value) || 1)}
                  className="input-field"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-neutral-700 mb-1">Batch Number</label>
                <input
                  type="text"
                  value={substitutionBatch}
                  onChange={(e) => setSubstitutionBatch(e.target.value)}
                  placeholder="Batch #"
                  className="input-field"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-neutral-700 mb-1">Expiry Date</label>
                <input
                  type="date"
                  value={substitutionExpiry}
                  onChange={(e) => setSubstitutionExpiry(e.target.value)}
                  className="input-field"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-neutral-700 mb-1">Reason</label>
                <input
                  type="text"
                  value={substitutionReason}
                  onChange={(e) => setSubstitutionReason(e.target.value)}
                  placeholder="Reason for substitution"
                  className="input-field"
                />
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <button
              onClick={handleSubstitute}
              disabled={actionLoading || !selectedSubstitution}
              className="btn-primary"
            >
              {actionLoading ? "Substituting..." : "Confirm Substitution"}
            </button>
            <button
              onClick={() => setSubstitutingItemId(null)}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function DispensePage() {
  return (
    <Suspense fallback={<div className="p-8 text-sm text-neutral-500">Loading dispense order...</div>}>
      <DispensePageContent />
    </Suspense>
  );
}
