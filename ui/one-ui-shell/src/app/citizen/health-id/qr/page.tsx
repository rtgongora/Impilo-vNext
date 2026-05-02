"use client";

import { useCallback, useEffect, useState } from "react";
import { citizenPortalApi } from "@/lib/citizenPortalClient";
import { useResolveQr, useQrPublicKey } from "@/hooks/queries/useVitoQr";

type Status = "loading" | "not-registered" | "registered" | "error";

interface ProfileData {
  impiloId?: string;
  healthId?: string;
  status?: string;
}

/** My Health ID QR section — citizen's own QR token */
function MyHealthIdQr() {
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
      <div className="text-center text-sm text-gray-500 py-8">
        Loading your Health ID…
      </div>
    );
  }

  if (status === "not-registered") {
    return (
      <div className="text-center">
        <p className="text-sm text-gray-600">
          You do not have a registered Health ID yet. Submit a request first on{" "}
          <a href="/citizen/health-id/request" className="text-impilo-500 hover:underline">
            Request Health ID
          </a>
          .
        </p>
      </div>
    );
  }

  if (status === "error") {
    return (
      <div className="text-center">
        <p className="text-sm text-red-600 mb-4">{error}</p>
        <button
          type="button"
          onClick={loadProfile}
          className="bg-impilo-500 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-impilo-600"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div>
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

/** QR Resolver section — paste a token to look up the associated Health ID */
function QrResolver() {
  const [inputToken, setInputToken] = useState("");
  const [activeToken, setActiveToken] = useState<string | undefined>(undefined);

  const { data: resolveData, isFetching, isError, error } = useResolveQr(activeToken);
  const { data: publicKeyData } = useQrPublicKey();

  function handleResolve(e: React.FormEvent) {
    e.preventDefault();
    setActiveToken(inputToken.trim() || undefined);
  }

  function handleClear() {
    setInputToken("");
    setActiveToken(undefined);
  }

  const resolution = resolveData?.data;
  const publicKey = publicKeyData?.data;

  return (
    <div className="space-y-4">
      <form onSubmit={handleResolve} className="flex gap-2">
        <input
          type="text"
          value={inputToken}
          onChange={(e) => setInputToken(e.target.value)}
          placeholder="Paste QR token here…"
          className="flex-1 px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono"
        />
        <button
          type="submit"
          disabled={!inputToken.trim() || isFetching}
          className="bg-impilo-500 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-impilo-600 disabled:opacity-50"
        >
          {isFetching ? "Resolving…" : "Resolve"}
        </button>
        {activeToken && (
          <button
            type="button"
            onClick={handleClear}
            className="bg-gray-100 text-gray-700 px-4 py-2 rounded-lg text-sm font-medium hover:bg-gray-200"
          >
            Clear
          </button>
        )}
      </form>

      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3">
          <p className="text-sm text-red-700">
            {error instanceof Error ? error.message : "Failed to resolve QR token"}
          </p>
        </div>
      )}

      {resolution && (
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 space-y-2 text-sm">
          <p className="text-xs font-semibold text-green-800 uppercase tracking-wide mb-1">
            Resolved Identity
          </p>
          {resolution.healthId && (
            <div className="flex justify-between">
              <span className="text-gray-600">Health ID</span>
              <span className="font-mono font-semibold text-gray-900">{resolution.healthId}</span>
            </div>
          )}
          {resolution.cpid && (
            <div className="flex justify-between">
              <span className="text-gray-600">CPID</span>
              <span className="font-mono text-gray-900">{resolution.cpid}</span>
            </div>
          )}
          {(resolution.givenName || resolution.familyName) && (
            <div className="flex justify-between">
              <span className="text-gray-600">Name</span>
              <span className="text-gray-900">
                {[resolution.givenName, resolution.familyName].filter(Boolean).join(" ")}
              </span>
            </div>
          )}
          {resolution.validUntil && (
            <div className="flex justify-between">
              <span className="text-gray-600">Valid until</span>
              <span className="text-gray-900">{resolution.validUntil}</span>
            </div>
          )}
        </div>
      )}

      {publicKey && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4">
          <p className="text-xs font-semibold text-gray-500 uppercase tracking-wide mb-2">
            QR Verification Public Key
          </p>
          <div className="space-y-1 text-xs text-gray-700">
            {publicKey.kty && (
              <div className="flex gap-2">
                <span className="text-gray-500 w-8">kty</span>
                <span className="font-mono">{publicKey.kty}</span>
              </div>
            )}
            {publicKey.kid && (
              <div className="flex gap-2">
                <span className="text-gray-500 w-8">kid</span>
                <span className="font-mono break-all">{publicKey.kid}</span>
              </div>
            )}
            {publicKey.alg && (
              <div className="flex gap-2">
                <span className="text-gray-500 w-8">alg</span>
                <span className="font-mono">{publicKey.alg}</span>
              </div>
            )}
            {publicKey.use && (
              <div className="flex gap-2">
                <span className="text-gray-500 w-8">use</span>
                <span className="font-mono">{publicKey.use}</span>
              </div>
            )}
            {publicKey.n && (
              <div className="flex gap-2">
                <span className="text-gray-500 w-8">n</span>
                <span className="font-mono break-all text-gray-600 max-h-16 overflow-y-auto">
                  {publicKey.n}
                </span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default function CitizenHealthIdQrPage() {
  return (
    <div className="max-w-lg mx-auto space-y-6">
      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h1 className="text-xl font-semibold text-gray-900 mb-1 text-center">My Health ID QR</h1>
        <p className="text-sm text-gray-500 text-center mb-6">
          Present this token at participating facilities (render as QR in production clients).
        </p>
        <MyHealthIdQr />
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h2 className="text-base font-semibold text-gray-900 mb-1">Verify a QR Token</h2>
        <p className="text-sm text-gray-500 mb-4">
          Paste any Health ID QR token to resolve the associated identity and verify its authenticity.
        </p>
        <QrResolver />
      </div>
    </div>
  );
}
