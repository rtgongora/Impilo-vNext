"use client";

import { useCallback, useEffect, useState } from "react";
import { citizenPortalApi } from "@/lib/citizenPortalClient";

type Status = "loading" | "not-registered" | "registered" | "error";

interface ProfileData {
  impiloId?: string;
  healthId?: string;
  status?: string;
}

/** Migrated from ui/portal/(citizen)/my-qr — same VITO contract via /api/v1/portal (gateway rewrite). */
export default function CitizenHealthIdQrPage() {
  const [status, setStatus] = useState<Status>("loading");
  const [profile, setProfile] = useState<ProfileData | null>(null);
  const [qrToken, setQrToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadProfile = useCallback(async () => {
    setStatus("loading");
    setError(null);
    try {
      const me = await citizenPortalApi.getMe();
      if (!me.registered) {
        setStatus("not-registered");
        return;
      }
      setProfile(me);
      const qr = await citizenPortalApi.getHealthIdQr();
      setQrToken(qr.qr);
      setStatus("registered");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load profile");
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  if (status === "loading") {
    return (
      <div className="max-w-lg mx-auto text-center text-sm text-gray-500 py-12">
        Loading your Health ID…
      </div>
    );
  }

  if (status === "not-registered") {
    return (
      <div className="max-w-lg mx-auto bg-white rounded-xl border border-gray-200 p-6 text-center">
        <h1 className="text-xl font-semibold text-gray-900 mb-2">My Health ID QR</h1>
        <p className="text-sm text-gray-600">
          You do not have a registered Health ID yet. Submit a request first on{" "}
          <a href="/citizen/health-id/request" className="text-blue-600 hover:underline">
            Request Health ID
          </a>
          .
        </p>
      </div>
    );
  }

  if (status === "error") {
    return (
      <div className="max-w-lg mx-auto bg-white rounded-xl border border-gray-200 p-6 text-center">
        <h1 className="text-xl font-semibold text-gray-900 mb-2">My Health ID QR</h1>
        <p className="text-sm text-red-600 mb-4">{error}</p>
        <button
          type="button"
          onClick={loadProfile}
          className="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-700"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto bg-white rounded-xl border border-gray-200 p-6">
      <h1 className="text-xl font-semibold text-gray-900 mb-1 text-center">My Health ID QR</h1>
      <p className="text-sm text-gray-500 text-center mb-6">
        Present this token at participating facilities (render as QR in production clients).
      </p>
      {profile && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 mb-4 space-y-1 text-sm">
          {profile.impiloId && (
            <div className="flex justify-between">
              <span className="text-gray-500">Impilo ID</span>
              <span className="font-mono text-gray-900">{profile.impiloId}</span>
            </div>
          )}
          {profile.healthId && (
            <div className="flex justify-between">
              <span className="text-gray-500">Health ID</span>
              <span className="font-mono text-gray-900">{profile.healthId}</span>
            </div>
          )}
          {profile.status && (
            <div className="flex justify-between">
              <span className="text-gray-500">Status</span>
              <span className="font-medium text-green-700">{profile.status}</span>
            </div>
          )}
        </div>
      )}
      {qrToken && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500 mb-2 text-center">Signed QR token</p>
          <div className="bg-white border border-gray-300 rounded-lg p-4 break-all font-mono text-xs text-gray-800 max-h-48 overflow-y-auto">
            {qrToken}
          </div>
        </div>
      )}
      <button
        type="button"
        onClick={loadProfile}
        className="mt-4 w-full bg-gray-100 text-gray-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-gray-200"
      >
        Refresh
      </button>
    </div>
  );
}
