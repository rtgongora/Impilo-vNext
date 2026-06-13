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
      <div className="text-center text-sm text-muted-foreground py-8">
        Loading your Health ID…
      </div>
    );
  }

  if (status === "not-registered") {
    return (
      <div className="text-center">
        <p className="text-sm text-muted-foreground">
          You do not have a registered Health ID yet. Submit a request first on{" "}
          <a href="/citizen/health-id/request" className="text-primary hover:underline">
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
          className="bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary-hover"
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div>
      {profile && (
        <div className="bg-background border border-border rounded-lg p-4 mb-4 space-y-1 text-sm">
          {profile.impiloId && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Impilo ID</span>
              <span className="font-mono text-foreground">{profile.impiloId}</span>
            </div>
          )}
          {profile.healthId && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Health ID</span>
              <span className="font-mono text-foreground">{profile.healthId}</span>
            </div>
          )}
          {profile.status && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Status</span>
              <span className="font-medium text-green-700">{profile.status}</span>
            </div>
          )}
        </div>
      )}
      {qrToken && (
        <div className="bg-background border border-border rounded-lg p-4">
          <p className="text-xs text-muted-foreground mb-2 text-center">Signed QR token</p>
          <div className="bg-card border border-border rounded-lg p-4 break-all font-mono text-xs text-foreground max-h-48 overflow-y-auto">
            {qrToken}
          </div>
        </div>
      )}
      <button
        type="button"
        onClick={loadProfile}
        className="mt-4 w-full bg-neutral-100 text-foreground px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-100"
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
          className="flex-1 px-3 py-2 border border-border rounded-lg text-sm font-mono"
        />
        <button
          type="submit"
          disabled={!inputToken.trim() || isFetching}
          className="bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary-hover disabled:opacity-50"
        >
          {isFetching ? "Resolving…" : "Resolve"}
        </button>
        {activeToken && (
          <button
            type="button"
            onClick={handleClear}
            className="bg-neutral-100 text-foreground px-4 py-2 rounded-lg text-sm font-medium hover:bg-neutral-100"
          >
            Clear
          </button>
        )}
      </form>

      {isError && (
        <div className="bg-danger-soft border border-danger/28 rounded-lg p-3">
          <p className="text-sm text-danger">
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
              <span className="text-muted-foreground">Health ID</span>
              <span className="font-mono font-semibold text-foreground">{resolution.healthId}</span>
            </div>
          )}
          {resolution.cpid && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">CPID</span>
              <span className="font-mono text-foreground">{resolution.cpid}</span>
            </div>
          )}
          {(resolution.givenName || resolution.familyName) && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Name</span>
              <span className="text-foreground">
                {[resolution.givenName, resolution.familyName].filter(Boolean).join(" ")}
              </span>
            </div>
          )}
          {resolution.validUntil && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Valid until</span>
              <span className="text-foreground">{resolution.validUntil}</span>
            </div>
          )}
        </div>
      )}

      {publicKey && (
        <div className="bg-background border border-border rounded-lg p-4">
          <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
            QR Verification Public Key
          </p>
          <div className="space-y-1 text-xs text-foreground">
            {publicKey.kty && (
              <div className="flex gap-2">
                <span className="text-muted-foreground w-8">kty</span>
                <span className="font-mono">{publicKey.kty}</span>
              </div>
            )}
            {publicKey.kid && (
              <div className="flex gap-2">
                <span className="text-muted-foreground w-8">kid</span>
                <span className="font-mono break-all">{publicKey.kid}</span>
              </div>
            )}
            {publicKey.alg && (
              <div className="flex gap-2">
                <span className="text-muted-foreground w-8">alg</span>
                <span className="font-mono">{publicKey.alg}</span>
              </div>
            )}
            {publicKey.use && (
              <div className="flex gap-2">
                <span className="text-muted-foreground w-8">use</span>
                <span className="font-mono">{publicKey.use}</span>
              </div>
            )}
            {publicKey.n && (
              <div className="flex gap-2">
                <span className="text-muted-foreground w-8">n</span>
                <span className="font-mono break-all text-muted-foreground max-h-16 overflow-y-auto">
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
      <div className="bg-card rounded-xl border border-border p-6">
        <h1 className="text-xl font-semibold text-foreground mb-1 text-center">My Health ID QR</h1>
        <p className="text-sm text-muted-foreground text-center mb-6">
          Present this token at participating facilities (render as QR in production clients).
        </p>
        <MyHealthIdQr />
      </div>

      <div className="bg-card rounded-xl border border-border p-6">
        <h2 className="text-base font-semibold text-foreground mb-1">Verify a QR Token</h2>
        <p className="text-sm text-muted-foreground mb-4">
          Paste any Health ID QR token to resolve the associated identity and verify its authenticity.
        </p>
        <QrResolver />
      </div>
    </div>
  );
}
