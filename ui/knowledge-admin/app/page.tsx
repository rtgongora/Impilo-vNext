"use client";

import { useCallback, useEffect, useState } from "react";

type ReviewItem = {
  id: string;
  review_status: string;
  proposed_payload?: Record<string, unknown>;
};

async function fetchQueue(token: string): Promise<ReviewItem[]> {
  const r = await fetch("/api/bff/internal/v1/clinical/curation/review-items?status=PROPOSED", {
    headers: {
      Authorization: `Bearer ${token}`,
      "X-Tenant-ID": "tenant-moh-zw",
      "X-Pod-ID": "national-spine",
      "X-Request-ID": crypto.randomUUID(),
      "X-Correlation-ID": crypto.randomUUID(),
    },
  });
  if (!r.ok) throw new Error(await r.text());
  const j = await r.json();
  return (j.data ?? []) as ReviewItem[];
}

async function postDecision(token: string, id: string, decision: "APPROVED" | "REJECTED", reviewer: string) {
  const r = await fetch(`/api/bff/internal/v1/clinical/curation/review-items/${id}/decision`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      "X-Tenant-ID": "tenant-moh-zw",
      "X-Pod-ID": "national-spine",
      "X-Request-ID": crypto.randomUUID(),
      "X-Correlation-ID": crypto.randomUUID(),
    },
    body: JSON.stringify({ decision, reviewer, notes: "" }),
  });
  if (!r.ok) throw new Error(await r.text());
  return r.json();
}

export default function KnowledgeAdminPage() {
  const [token, setToken] = useState("");
  const [items, setItems] = useState<ReviewItem[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!token.trim()) return;
    setLoading(true);
    setErr(null);
    try {
      setItems(await fetchQueue(token.trim()));
    } catch (e) {
      setErr(String(e));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    const t = typeof window !== "undefined" ? sessionStorage.getItem("exp:auth_token") : null;
    if (t) setToken(t);
  }, []);

  return (
    <main style={{ maxWidth: 960, margin: "0 auto", padding: 24 }}>
      <h1 style={{ fontSize: "1.35rem", fontWeight: 600 }}>National knowledge curation</h1>
      <p style={{ fontSize: 13, opacity: 0.85, marginTop: 8 }}>
        Requires admin-equivalent JWT (SYSTEM_ADMIN / FACILITY_ADMIN / DEVELOPER). Uses BFF rewrites to{" "}
        <code>/api/bff/…</code>.
      </p>
      <div style={{ marginTop: 20, display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input
          value={token}
          onChange={(e) => setToken(e.target.value)}
          placeholder="Paste Bearer token (session exp:auth_token)"
          style={{ flex: 1, minWidth: 240, padding: 10, borderRadius: 8, border: "1px solid #334155", background: "#1e293b", color: "#f8fafc" }}
        />
        <button
          type="button"
          onClick={load}
          disabled={loading}
          style={{ padding: "10px 16px", borderRadius: 8, border: "none", background: "#22c55e", color: "#052e16", fontWeight: 600, cursor: "pointer" }}
        >
          {loading ? "Loading…" : "Load queue"}
        </button>
      </div>
      {err && <p style={{ color: "#f87171", marginTop: 12, fontSize: 13 }}>{err}</p>}
      <ul style={{ listStyle: "none", padding: 0, marginTop: 24 }}>
        {items.map((it) => (
          <li
            key={it.id}
            style={{
              marginBottom: 12,
              padding: 14,
              borderRadius: 10,
              border: "1px solid #334155",
              background: "#1e293b",
            }}
          >
            <div style={{ fontSize: 12, opacity: 0.7 }}>{it.id}</div>
            <pre style={{ whiteSpace: "pre-wrap", fontSize: 11, marginTop: 8, maxHeight: 160, overflow: "auto" }}>
              {JSON.stringify(it.proposed_payload ?? {}, null, 2)}
            </pre>
            <div style={{ marginTop: 10, display: "flex", gap: 8 }}>
              <button
                type="button"
                style={{ padding: "6px 12px", borderRadius: 6, border: "none", background: "#22c55e", cursor: "pointer" }}
                onClick={async () => {
                  try {
                    await postDecision(token.trim(), it.id, "APPROVED", "knowledge-admin-ui");
                    await load();
                  } catch (e) {
                    setErr(String(e));
                  }
                }}
              >
                Approve
              </button>
              <button
                type="button"
                style={{ padding: "6px 12px", borderRadius: 6, border: "none", background: "#64748b", cursor: "pointer" }}
                onClick={async () => {
                  try {
                    await postDecision(token.trim(), it.id, "REJECTED", "knowledge-admin-ui");
                    await load();
                  } catch (e) {
                    setErr(String(e));
                  }
                }}
              >
                Reject
              </button>
            </div>
          </li>
        ))}
      </ul>
      {items.length === 0 && !loading && token && <p style={{ marginTop: 16, fontSize: 13 }}>No PROPOSED items.</p>}
    </main>
  );
}
