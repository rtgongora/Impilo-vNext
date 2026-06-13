"use client";

import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

const SESSION_HEADER = "X-Patient-Share-Session";

type Step = "otp" | "stepUp" | "identity" | "workspace";

type CouncilRow = { id: number; name: string; councilCode?: string; registrationNumberPattern?: string | null };

/** External provider entry: token + OTP + council-linked identity (public BFF → VITO). */
export default function CollaborationAccessPage() {
  const searchParams = useSearchParams();
  const initialToken = useMemo(() => searchParams.get("token")?.trim() ?? "", [searchParams]);

  const [shareToken, setShareToken] = useState(initialToken);
  const [otp, setOtp] = useState("");
  const [session, setSession] = useState("");
  const [step, setStep] = useState<Step>(initialToken ? "otp" : "otp");
  const [tenantId, setTenantId] = useState<string>("");
  const [councils, setCouncils] = useState<CouncilRow[]>([]);
  const [councilId, setCouncilId] = useState("");
  const [reg, setReg] = useState("");
  const [given, setGiven] = useState("");
  const [family, setFamily] = useState("");
  const [profession, setProfession] = useState("");
  const [stepUpCode, setStepUpCode] = useState("");
  const [workspace, setWorkspace] = useState<Record<string, unknown> | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function postJson(path: string, body: unknown, extraHeaders?: Record<string, string>) {
    const headers: Record<string, string> = { "Content-Type": "application/json", ...extraHeaders };
    const res = await fetch(path, { method: "POST", headers, body: JSON.stringify(body) });
    const text = await res.text();
    let json: unknown = null;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      /* ignore */
    }
    if (!res.ok) {
      const msg =
        json && typeof json === "object" && json !== null && "error" in json
          ? String((json as { error?: { message?: string } }).error?.message ?? res.statusText)
          : res.statusText;
      throw new Error(msg || "Request failed");
    }
    return json;
  }

  const getJson = useCallback(async (path: string, extraHeaders?: Record<string, string>) => {
    const res = await fetch(path, { headers: { ...extraHeaders } });
    const text = await res.text();
    const json = text ? JSON.parse(text) : null;
    if (!res.ok) {
      throw new Error(String((json as { error?: { message?: string } })?.error?.message ?? res.statusText));
    }
    return json;
  }, []);

  const loadCouncils = useCallback(async (tid: string) => {
    if (!tid) return;
    setError("");
    try {
      const json = (await getJson(`/internal/v1/public/patient-shares/councils?tenantId=${encodeURIComponent(tid)}`)) as {
        data?: CouncilRow[];
      };
      const rows = Array.isArray(json?.data) ? json.data : [];
      setCouncils(rows);
      if (rows.length > 0 && !councilId) {
        setCouncilId(String(rows[0].id));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load councils");
    }
  }, [councilId, getJson]);

  useEffect(() => {
    async function validateToken() {
      const t = shareToken.trim();
      if (!t) return;
      setLoading(true);
      try {
        const json = (await postJson("/internal/v1/public/patient-shares/validate", { shareToken: t })) as {
          data?: { tenantId?: string };
        };
        const tid = json?.data?.tenantId;
        if (tid) {
          setTenantId(tid);
          await loadCouncils(tid);
        }
      } catch {
        /* optional validate — ignore */
      } finally {
        setLoading(false);
      }
    }
    void validateToken();
  }, [loadCouncils, shareToken]);

  async function handleOtp(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const json = (await postJson("/internal/v1/public/patient-shares/verify-otp", {
        shareToken: shareToken.trim(),
        otp: otp.trim(),
      })) as { data?: { sessionToken?: string; sessionStatus?: string } };
      const tok = json?.data?.sessionToken;
      if (!tok) throw new Error("No session returned");
      setSession(tok);
      const st = json?.data?.sessionStatus;
      if (st === "AWAITING_STEP_UP") {
        setStep("stepUp");
      } else {
        setStep("identity");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "OTP failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleStepUp(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await postJson(
        "/internal/v1/public/patient-shares/verify-step-up",
        { stepUpCode: stepUpCode.trim() },
        { [SESSION_HEADER]: session },
      );
      setStep("identity");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Step-up failed");
    } finally {
      setLoading(false);
    }
  }

  async function handleIdentity(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const json = (await postJson(
        "/internal/v1/public/patient-shares/complete-identity",
        {
          councilId: Number(councilId),
          councilRegistrationNumber: reg.trim(),
          givenName: given.trim(),
          familyName: family.trim(),
          professionOrCadre: profession.trim() || undefined,
        },
        { [SESSION_HEADER]: session },
      )) as { data?: Record<string, unknown> };
      setWorkspace(json?.data ?? null);
      setStep("workspace");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Identity step failed");
    } finally {
      setLoading(false);
    }
  }

  async function refreshWorkspace() {
    setError("");
    setLoading(true);
    try {
      const json = (await getJson("/internal/v1/public/patient-shares/workspace", {
        [SESSION_HEADER]: session,
      })) as { data?: Record<string, unknown> };
      setWorkspace(json?.data ?? null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Load failed");
    } finally {
      setLoading(false);
    }
  }

  async function submitNote(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await postJson(
        "/internal/v1/public/patient-shares/contributions",
        {
          targetRecordType: "LIMITED_CLINICAL_SUMMARY",
          targetRecordId: String(workspace?.grantId ?? "1"),
          actionType: "ADD_NOTE",
          summary: note.trim(),
          payloadJson: JSON.stringify({ note: note.trim(), source: "EXTERNAL_COLLABORATION" }),
        },
        { [SESSION_HEADER]: session },
      );
      setNote("");
      await refreshWorkspace();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Contribution failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-lg space-y-6 p-6">
      <div>
        <h1 className="text-xl font-semibold text-foreground">Collaboration access</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          You are entering through a patient-mediated share. You must verify the one-time code and provide
          professional council registration details. Access is limited by policy and expires automatically.
        </p>
        {tenantId ? (
          <p className="mt-2 text-xs text-muted-foreground">
            Tenant context: <span className="font-mono">{tenantId}</span> (from share validation).
          </p>
        ) : null}
      </div>

      {step === "otp" ? (
        <form onSubmit={handleOtp} className="space-y-3 rounded border border-border bg-card p-4">
          <label className="block text-sm font-medium">
            Share token
            <input
              className="mt-1 w-full rounded border px-2 py-1 text-sm"
              value={shareToken}
              onChange={(e) => setShareToken(e.target.value)}
            />
          </label>
          <label className="block text-sm font-medium">
            One-time code (OTP)
            <input
              className="mt-1 w-full rounded border px-2 py-1 text-sm"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
            />
          </label>
          <button
            type="submit"
            disabled={loading}
            className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            Continue
          </button>
        </form>
      ) : null}

      {step === "stepUp" ? (
        <form onSubmit={handleStepUp} className="space-y-3 rounded border border-warning/35 bg-warning-soft p-4">
          <p className="text-sm text-warning-foreground">
            This share scope requires an additional step-up code (MVP: use the code configured on the server, default{" "}
            <span className="font-mono">000000</span> via <span className="font-mono">VITO_PATIENT_SHARE_STEP_UP_CODE</span>
            ).
          </p>
          <label className="block text-sm font-medium">
            Step-up code
            <input
              className="mt-1 w-full rounded border px-2 py-1 text-sm"
              value={stepUpCode}
              onChange={(e) => setStepUpCode(e.target.value)}
            />
          </label>
          <button
            type="submit"
            disabled={loading}
            className="rounded bg-amber-800 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            Verify step-up
          </button>
        </form>
      ) : null}

      {step === "identity" ? (
        <form onSubmit={handleIdentity} className="space-y-3 rounded border border-border bg-card p-4">
          <p className="text-sm text-foreground">Session established. Provide your professional identity.</p>
          <label className="block text-sm font-medium">
            Council
            <select
              className="mt-1 w-full rounded border px-2 py-1 text-sm"
              value={councilId}
              onChange={(e) => setCouncilId(e.target.value)}
              disabled={councils.length === 0}
            >
              {councils.length === 0 ? (
                <option value="">No councils loaded — check tenant / Varapi data</option>
              ) : (
                councils.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.councilCode ?? c.id})
                  </option>
                ))
              )}
            </select>
          </label>
          {councils.find((c) => String(c.id) === councilId)?.registrationNumberPattern ? (
            <p className="text-xs text-muted-foreground">
              Expected format (regex):{" "}
              <span className="font-mono break-all">
                {councils.find((c) => String(c.id) === councilId)?.registrationNumberPattern}
              </span>
            </p>
          ) : null}
          <label className="block text-sm font-medium">
            Council registration number
            <input className="mt-1 w-full rounded border px-2 py-1 text-sm" value={reg} onChange={(e) => setReg(e.target.value)} />
          </label>
          <label className="block text-sm font-medium">
            Given name
            <input className="mt-1 w-full rounded border px-2 py-1 text-sm" value={given} onChange={(e) => setGiven(e.target.value)} />
          </label>
          <label className="block text-sm font-medium">
            Family name
            <input
              className="mt-1 w-full rounded border px-2 py-1 text-sm"
              value={family}
              onChange={(e) => setFamily(e.target.value)}
            />
          </label>
          <label className="block text-sm font-medium">
            Profession / cadre (optional)
            <input
              className="mt-1 w-full rounded border px-2 py-1 text-sm"
              value={profession}
              onChange={(e) => setProfession(e.target.value)}
            />
          </label>
          <button
            type="button"
            className="text-xs text-blue-800 underline"
            onClick={() => tenantId && void loadCouncils(tenantId)}
            disabled={!tenantId}
          >
            Reload councils
          </button>
          <button
            type="submit"
            disabled={loading}
            className="block rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            Activate workspace
          </button>
        </form>
      ) : null}

      {step === "workspace" && workspace ? (
        <div className="space-y-4 rounded border border-success/25 bg-success-soft p-4 text-sm text-emerald-950">
          <p className="font-medium">Workspace active (provisional / governed)</p>
          <p>Grant: {String(workspace.grantId)} — scope: {String(workspace.scopeType)}</p>
          <p>Trust: {String(workspace.trustLevel)} — temp provider ID: {String(workspace.temporaryProviderPublicId ?? "—")}</p>
          <p className="text-xs">Write-capable flag from server: {String(workspace.writeCapable)}</p>
          <p className="text-xs text-primary-hover">
            ADD_NOTE contributions are mirrored to PCT clinical notes when PCT accepts the service call (configure{" "}
            <span className="font-mono">VITO_TO_PCT_BEARER_TOKEN</span> if PCT returns 401).
          </p>
          <button type="button" onClick={refreshWorkspace} className="text-xs text-blue-800 underline">
            Refresh context
          </button>
          <form onSubmit={submitNote} className="space-y-2 border-t border-emerald-300 pt-3">
            <label className="block text-sm font-medium">
              Add note (bounded contribution)
              <textarea className="mt-1 w-full rounded border px-2 py-1 text-sm" rows={3} value={note} onChange={(e) => setNote(e.target.value)} />
            </label>
            <button
              type="submit"
              disabled={loading || !note.trim()}
              className="rounded bg-emerald-800 px-3 py-1 text-xs font-medium text-white disabled:opacity-50"
            >
              Submit contribution
            </button>
          </form>
        </div>
      ) : null}

      {error ? <p className="text-sm text-danger">{error}</p> : null}
    </div>
  );
}
