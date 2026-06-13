"use client";

import { useState } from "react";
import { portalApi } from "@/lib/portalApi";

type Tab = "create" | "redeem";

interface CreateForm {
  issuanceRequestId: string;
  delegateName: string;
  delegateContact: string;
  delegateIdRef: string;
  facilityId: string;
  expiryHours: string;
  stepUpToken: string;
}

interface RedeemForm {
  pickupToken: string;
  otp: string;
}

interface PickupResult {
  pickupId: number;
  pickupToken: string;
  otp: string;
  expiresAt: string;
  message: string;
}

const INITIAL_CREATE: CreateForm = {
  issuanceRequestId: "",
  delegateName: "",
  delegateContact: "",
  delegateIdRef: "",
  facilityId: "",
  expiryHours: "",
  stepUpToken: "",
};

const INITIAL_REDEEM: RedeemForm = {
  pickupToken: "",
  otp: "",
};

export default function PickupPage() {
  const [tab, setTab] = useState<Tab>("create");
  const [createForm, setCreateForm] = useState<CreateForm>(INITIAL_CREATE);
  const [redeemForm, setRedeemForm] = useState<RedeemForm>(INITIAL_REDEEM);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pickupResult, setPickupResult] = useState<PickupResult | null>(null);
  const [redeemSuccess, setRedeemSuccess] = useState(false);

  function updateCreate<K extends keyof CreateForm>(
    key: K,
    value: CreateForm[K],
  ) {
    setCreateForm((prev) => ({ ...prev, [key]: value }));
  }

  function updateRedeem<K extends keyof RedeemForm>(
    key: K,
    value: RedeemForm[K],
  ) {
    setRedeemForm((prev) => ({ ...prev, [key]: value }));
  }

  function switchTab(newTab: Tab) {
    setTab(newTab);
    setError(null);
    setPickupResult(null);
    setRedeemSuccess(false);
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setPickupResult(null);

    if (!createForm.stepUpToken) {
      setError(
        "Step-up verification token is required to create a delegated pickup.",
      );
      setLoading(false);
      return;
    }

    try {
      const result = await portalApi.createPickup(
        {
          issuanceRequestId: parseInt(createForm.issuanceRequestId, 10),
          delegateName: createForm.delegateName,
          delegateContact: createForm.delegateContact,
          delegateIdRef: createForm.delegateIdRef || undefined,
          facilityId: createForm.facilityId,
          expiryHours: createForm.expiryHours
            ? parseInt(createForm.expiryHours, 10)
            : undefined,
        },
        createForm.stepUpToken,
      );
      setPickupResult(result);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to create pickup",
      );
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
      await portalApi.redeemPickup(redeemForm.pickupToken, redeemForm.otp);
      setRedeemSuccess(true);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to redeem pickup",
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-lg mx-auto">
      <div className="bg-card rounded-xl shadow-sm border border-neutral-200 p-6">
        <h1 className="text-xl font-semibold text-neutral-900 mb-1">
          Delegated Pickup
        </h1>
        <p className="text-sm text-neutral-500 mb-6">
          Authorize someone else to collect your health identity document on
          your behalf, or redeem a pickup authorization.
        </p>

        {/* Tab switcher */}
        <div className="flex rounded-lg bg-neutral-100 p-1 mb-6">
          <button
            type="button"
            onClick={() => switchTab("create")}
            className={`flex-1 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
              tab === "create"
                ? "bg-card text-neutral-900 shadow-sm"
                : "text-neutral-600 hover:text-neutral-900"
            }`}
          >
            Create Pickup
          </button>
          <button
            type="button"
            onClick={() => switchTab("redeem")}
            className={`flex-1 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
              tab === "redeem"
                ? "bg-card text-neutral-900 shadow-sm"
                : "text-neutral-600 hover:text-neutral-900"
            }`}
          >
            Redeem Pickup
          </button>
        </div>

        {/* Error display */}
        {error && (
          <div className="bg-danger-soft border border-danger/28 rounded-lg p-3 mb-4">
            <p className="text-sm text-danger">{error}</p>
          </div>
        )}

        {/* ========== CREATE TAB ========== */}
        {tab === "create" && !pickupResult && (
          <form onSubmit={handleCreate} className="space-y-4">
            {/* Step-up notice */}
            <div className="bg-warning-soft border border-warning/35 rounded-lg p-3">
              <p className="text-xs text-warning-foreground">
                Creating a delegated pickup requires step-up verification.
                Provide your step-up token below.
              </p>
            </div>

            <div>
              <label
                htmlFor="stepUpToken"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Step-Up Token
              </label>
              <input
                id="stepUpToken"
                type="text"
                value={createForm.stepUpToken}
                onChange={(e) => updateCreate("stepUpToken", e.target.value)}
                placeholder="Enter step-up verification token"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="issuanceRequestId"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Issuance Request ID
              </label>
              <input
                id="issuanceRequestId"
                type="number"
                value={createForm.issuanceRequestId}
                onChange={(e) =>
                  updateCreate("issuanceRequestId", e.target.value)
                }
                placeholder="Enter issuance request ID"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="delegateName"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Delegate Name
              </label>
              <input
                id="delegateName"
                type="text"
                value={createForm.delegateName}
                onChange={(e) => updateCreate("delegateName", e.target.value)}
                placeholder="Full name of the person collecting"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="delegateContact"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Delegate Contact
              </label>
              <input
                id="delegateContact"
                type="text"
                value={createForm.delegateContact}
                onChange={(e) =>
                  updateCreate("delegateContact", e.target.value)
                }
                placeholder="Phone number or email"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="delegateIdRef"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Delegate ID Reference{" "}
                <span className="text-neutral-400 font-normal">(optional)</span>
              </label>
              <input
                id="delegateIdRef"
                type="text"
                value={createForm.delegateIdRef}
                onChange={(e) => updateCreate("delegateIdRef", e.target.value)}
                placeholder="National ID or passport number"
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="facilityId"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Facility ID
              </label>
              <input
                id="facilityId"
                type="text"
                value={createForm.facilityId}
                onChange={(e) => updateCreate("facilityId", e.target.value)}
                placeholder="Collection facility ID"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="expiryHours"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Expiry Hours{" "}
                <span className="text-neutral-400 font-normal">(optional)</span>
              </label>
              <input
                id="expiryHours"
                type="number"
                min="1"
                max="168"
                value={createForm.expiryHours}
                onChange={(e) => updateCreate("expiryHours", e.target.value)}
                placeholder="Hours until pickup expires (default: 48)"
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? "Creating..." : "Create Pickup Authorization"}
            </button>
          </form>
        )}

        {/* Create success */}
        {tab === "create" && pickupResult && (
          <div className="space-y-4">
            <div className="bg-green-50 border border-green-200 rounded-lg p-4">
              <h3 className="text-sm font-semibold text-green-800 mb-2">
                Pickup Authorization Created
              </h3>
              <p className="text-sm text-green-700 mb-3">
                {pickupResult.message}
              </p>
            </div>

            <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-4 space-y-3">
              <div>
                <p className="text-xs text-neutral-500 mb-1">Pickup Token</p>
                <p className="font-mono text-sm text-neutral-900 bg-card border border-neutral-300 rounded-lg p-2 break-all">
                  {pickupResult.pickupToken}
                </p>
              </div>

              <div>
                <p className="text-xs text-neutral-500 mb-1">OTP Code</p>
                <p className="font-mono text-2xl font-bold text-neutral-900 text-center bg-card border border-neutral-300 rounded-lg p-3 tracking-widest">
                  {pickupResult.otp}
                </p>
              </div>

              <div className="flex justify-between text-sm">
                <span className="text-neutral-500">Pickup ID</span>
                <span className="font-mono text-neutral-900">
                  {pickupResult.pickupId}
                </span>
              </div>

              <div className="flex justify-between text-sm">
                <span className="text-neutral-500">Expires</span>
                <span className="text-neutral-900">
                  {new Date(pickupResult.expiresAt).toLocaleString()}
                </span>
              </div>
            </div>

            <div className="bg-info-soft border border-info/25 rounded-lg p-3">
              <p className="text-xs text-primary-hover">
                Share the pickup token and OTP with your delegate. They will
                need both to collect your document at the designated facility.
                Do not share these with anyone else.
              </p>
            </div>

            <button
              type="button"
              onClick={() => {
                setPickupResult(null);
                setCreateForm(INITIAL_CREATE);
                setError(null);
              }}
              className="w-full bg-neutral-100 text-neutral-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-200 transition-colors"
            >
              Create Another
            </button>
          </div>
        )}

        {/* ========== REDEEM TAB ========== */}
        {tab === "redeem" && !redeemSuccess && (
          <form onSubmit={handleRedeem} className="space-y-4">
            <div>
              <label
                htmlFor="redeemPickupToken"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                Pickup Token
              </label>
              <input
                id="redeemPickupToken"
                type="text"
                value={redeemForm.pickupToken}
                onChange={(e) => updateRedeem("pickupToken", e.target.value)}
                placeholder="Enter the pickup token you received"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <div>
              <label
                htmlFor="redeemOtp"
                className="block text-sm font-medium text-neutral-700 mb-1"
              >
                OTP Code
              </label>
              <input
                id="redeemOtp"
                type="text"
                value={redeemForm.otp}
                onChange={(e) => updateRedeem("otp", e.target.value)}
                placeholder="Enter the OTP code"
                required
                className="w-full px-3 py-2 border border-neutral-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? "Redeeming..." : "Redeem Pickup"}
            </button>
          </form>
        )}

        {/* Redeem success */}
        {tab === "redeem" && redeemSuccess && (
          <div className="space-y-4">
            <div className="bg-green-50 border border-green-200 rounded-lg p-4 text-center">
              <h3 className="text-sm font-semibold text-green-800 mb-1">
                Pickup Redeemed Successfully
              </h3>
              <p className="text-sm text-green-700">
                The delegated pickup has been verified. The document can now be
                released to the delegate.
              </p>
            </div>

            <button
              type="button"
              onClick={() => {
                setRedeemSuccess(false);
                setRedeemForm(INITIAL_REDEEM);
                setError(null);
              }}
              className="w-full bg-neutral-100 text-neutral-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-200 transition-colors"
            >
              Redeem Another
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
