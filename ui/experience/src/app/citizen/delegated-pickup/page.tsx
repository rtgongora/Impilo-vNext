"use client";

import { useState } from "react";
import { citizenPortalApi } from "@/lib/citizenPortalClient";

type Tab = "create" | "redeem";

/** Migrated from ui/portal/(citizen)/pickup */
export default function CitizenDelegatedPickupPage() {
  const [tab, setTab] = useState<Tab>("create");
  const [issuanceRequestId, setIssuanceRequestId] = useState("");
  const [delegateName, setDelegateName] = useState("");
  const [delegateContact, setDelegateContact] = useState("");
  const [delegateIdRef, setDelegateIdRef] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [expiryHours, setExpiryHours] = useState("");
  const [stepUpToken, setStepUpToken] = useState("");
  const [pickupToken, setPickupToken] = useState("");
  const [otp, setOtp] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pickupResult, setPickupResult] = useState<{
    pickupId: number;
    pickupToken: string;
    otp: string;
    expiresAt: string;
    message: string;
  } | null>(null);
  const [redeemSuccess, setRedeemSuccess] = useState(false);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setPickupResult(null);
    if (!stepUpToken) {
      setError("Step-up token is required.");
      setLoading(false);
      return;
    }
    try {
      const id = parseInt(issuanceRequestId, 10);
      if (Number.isNaN(id)) {
        setError("Issuance request ID must be a number.");
        setLoading(false);
        return;
      }
      const result = await citizenPortalApi.createPickup(
        {
          issuanceRequestId: id,
          delegateName,
          delegateContact,
          delegateIdRef: delegateIdRef || undefined,
          facilityId,
          expiryHours: expiryHours ? parseInt(expiryHours, 10) : undefined,
        },
        stepUpToken,
      );
      setPickupResult(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create pickup");
    } finally {
      setLoading(false);
    }
  }

  async function handleRedeem(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setRedeemSuccess(false);
    try {
      await citizenPortalApi.redeemPickup(pickupToken, otp);
      setRedeemSuccess(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to redeem");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-lg mx-auto bg-white rounded-xl border border-gray-200 p-6">
      <h1 className="text-xl font-semibold text-gray-900 mb-1">Delegated pickup</h1>
      <p className="text-sm text-gray-500 mb-6">Authorize collection or redeem a pickup (VITO portal contract).</p>
      <div className="flex rounded-lg bg-gray-100 p-1 mb-6">
        <button
          type="button"
          onClick={() => {
            setTab("create");
            setError(null);
            setPickupResult(null);
          }}
          className={`flex-1 px-3 py-2 rounded-md text-sm font-medium ${tab === "create" ? "bg-white shadow-sm" : "text-gray-600"}`}
        >
          Create
        </button>
        <button
          type="button"
          onClick={() => {
            setTab("redeem");
            setError(null);
            setRedeemSuccess(false);
          }}
          className={`flex-1 px-3 py-2 rounded-md text-sm font-medium ${tab === "redeem" ? "bg-white shadow-sm" : "text-gray-600"}`}
        >
          Redeem
        </button>
      </div>
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 mb-4 text-sm text-red-700">{error}</div>
      )}
      {tab === "create" && !pickupResult && (
        <form onSubmit={handleCreate} className="space-y-3 text-sm">
          <input value={stepUpToken} onChange={(e) => setStepUpToken(e.target.value)} placeholder="Step-up token" className="w-full border rounded px-3 py-2" />
          <input value={issuanceRequestId} onChange={(e) => setIssuanceRequestId(e.target.value)} placeholder="Issuance request ID" className="w-full border rounded px-3 py-2" required type="number" />
          <input value={delegateName} onChange={(e) => setDelegateName(e.target.value)} placeholder="Delegate name" className="w-full border rounded px-3 py-2" required />
          <input value={delegateContact} onChange={(e) => setDelegateContact(e.target.value)} placeholder="Delegate contact" className="w-full border rounded px-3 py-2" required />
          <input value={delegateIdRef} onChange={(e) => setDelegateIdRef(e.target.value)} placeholder="Delegate ID ref (optional)" className="w-full border rounded px-3 py-2" />
          <input value={facilityId} onChange={(e) => setFacilityId(e.target.value)} placeholder="Facility ID" className="w-full border rounded px-3 py-2" required />
          <input value={expiryHours} onChange={(e) => setExpiryHours(e.target.value)} placeholder="Expiry hours (optional)" className="w-full border rounded px-3 py-2" type="number" />
          <button type="submit" disabled={loading} className="w-full bg-blue-600 text-white py-2 rounded-lg font-medium disabled:opacity-50">
            {loading ? "Creating…" : "Create pickup"}
          </button>
        </form>
      )}
      {tab === "create" && pickupResult && (
        <div className="rounded-lg border border-green-200 bg-green-50 p-4 text-sm space-y-2">
          <p className="font-medium text-green-800">{pickupResult.message}</p>
          <p className="text-xs font-mono break-all">Token: {pickupResult.pickupToken}</p>
          <p className="text-xs">OTP: {pickupResult.otp}</p>
          <p className="text-xs text-gray-600">Expires: {pickupResult.expiresAt}</p>
          <button type="button" onClick={() => setPickupResult(null)} className="text-blue-600 text-sm hover:underline">
            Create another
          </button>
        </div>
      )}
      {tab === "redeem" && !redeemSuccess && (
        <form onSubmit={handleRedeem} className="space-y-3 text-sm">
          <input value={pickupToken} onChange={(e) => setPickupToken(e.target.value)} placeholder="Pickup token" className="w-full border rounded px-3 py-2" required />
          <input value={otp} onChange={(e) => setOtp(e.target.value)} placeholder="OTP" className="w-full border rounded px-3 py-2" required />
          <button type="submit" disabled={loading} className="w-full bg-blue-600 text-white py-2 rounded-lg font-medium disabled:opacity-50">
            {loading ? "Redeeming…" : "Redeem"}
          </button>
        </form>
      )}
      {tab === "redeem" && redeemSuccess && (
        <div className="rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-800">Pickup redeemed successfully.</div>
      )}
    </div>
  );
}
