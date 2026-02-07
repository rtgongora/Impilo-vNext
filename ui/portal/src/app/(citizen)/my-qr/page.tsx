"use client";

import { useEffect, useState } from "react";
import { portalApi } from "@/lib/portalApi";

type Status = "loading" | "not-registered" | "registered" | "error";

interface ProfileData {
  impiloId?: string;
  healthId?: string;
  status?: string;
}

export default function MyQrPage() {
  const [status, setStatus] = useState<Status>("loading");
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [qrToken, setQrToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadProfile();
  }, []);

  async function loadProfile() {
    setStatus("loading");
    setError(null);

    try {
      const me = await portalApi.getMe();

      if (!me.registered) {
        setStatus("not-registered");
        return;
      }

      setProfile(me);

      // Fetch QR token
      const qr = await portalApi.getHealthIdQr();
      setQrToken(qr.qr);
      setStatus("registered");
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load profile",
      );
      setStatus("error");
    }
  }

  // Loading state
  if (status === "loading") {
    return (
      <div className="max-w-lg mx-auto">
        <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-6 text-center">
          <div className="animate-pulse space-y-3">
            <div className="h-4 bg-neutral-200 rounded w-1/3 mx-auto" />
            <div className="h-32 bg-neutral-100 rounded-lg" />
            <div className="h-3 bg-neutral-200 rounded w-1/2 mx-auto" />
          </div>
          <p className="text-sm text-neutral-500 mt-4">
            Loading your Health ID...
          </p>
        </div>
      </div>
    );
  }

  // Not registered state
  if (status === "not-registered") {
    return (
      <div className="max-w-lg mx-auto">
        <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-6 text-center">
          <h1 className="text-xl font-semibold text-neutral-900 mb-2">
            My Health ID QR
          </h1>
          <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-6 my-4">
            <p className="text-sm text-neutral-600 font-medium mb-1">
              Not Registered
            </p>
            <p className="text-sm text-neutral-500">
              You do not have a registered Health ID yet. Please submit a
              request first using the Request ID page.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (status === "error") {
    return (
      <div className="max-w-lg mx-auto">
        <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-6 text-center">
          <h1 className="text-xl font-semibold text-neutral-900 mb-2">
            My Health ID QR
          </h1>
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 my-4">
            <p className="text-sm text-red-700">{error}</p>
          </div>
          <button
            type="button"
            onClick={loadProfile}
            className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
          >
            Retry
          </button>
        </div>
      </div>
    );
  }

  // Registered — show QR
  return (
    <div className="max-w-lg mx-auto">
      <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-6">
        <h1 className="text-xl font-semibold text-neutral-900 mb-1 text-center">
          My Health ID QR
        </h1>
        <p className="text-sm text-neutral-500 text-center mb-6">
          Present this QR code at any participating health facility for
          identification.
        </p>

        {/* Profile info */}
        {profile && (
          <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-4 mb-4 space-y-1">
            {profile.impiloId && (
              <div className="flex justify-between text-sm">
                <span className="text-neutral-500">Impilo ID</span>
                <span className="font-mono text-neutral-900">
                  {profile.impiloId}
                </span>
              </div>
            )}
            {profile.healthId && (
              <div className="flex justify-between text-sm">
                <span className="text-neutral-500">Health ID</span>
                <span className="font-mono text-neutral-900">
                  {profile.healthId}
                </span>
              </div>
            )}
            {profile.status && (
              <div className="flex justify-between text-sm">
                <span className="text-neutral-500">Status</span>
                <span className="font-medium text-green-700">
                  {profile.status}
                </span>
              </div>
            )}
          </div>
        )}

        {/* QR token display */}
        {qrToken && (
          <div className="bg-neutral-50 border border-neutral-200 rounded-lg p-4">
            <p className="text-xs text-neutral-500 mb-2 text-center">
              Signed QR Token
            </p>
            <div className="bg-white border border-neutral-300 rounded-lg p-4 break-all font-mono text-xs text-neutral-800 leading-relaxed max-h-48 overflow-y-auto">
              {qrToken}
            </div>
            <p className="text-xs text-neutral-400 mt-2 text-center">
              In production, this token is rendered as a scannable QR code.
            </p>
          </div>
        )}

        {/* Refresh */}
        <button
          type="button"
          onClick={loadProfile}
          className="mt-4 w-full bg-neutral-100 text-neutral-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-200 transition-colors"
        >
          Refresh
        </button>
      </div>
    </div>
  );
}
