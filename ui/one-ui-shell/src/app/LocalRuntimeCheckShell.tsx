"use client";

import { useCallback, useMemo, useState } from "react";
import {
  Badge,
  Button,
  Card,
  CardHeader,
  CardTitle,
  StatusIndicator,
} from "shared-ui";
import type { ActorType, AuthzResponse, PurposeOfUse } from "@/lib/contracts";
import { apiClient } from "@/lib/apiClient";
import { useSession } from "@/hooks/useSession";
import { useWorkContext } from "@/hooks/useContext";

const DEFAULT_TENANT = "00000000-0000-4000-8000-000000000001";

const ACTOR_TYPES: ActorType[] = ["PROVIDER", "OPERATOR", "CITIZEN", "SYSTEM"];

const PURPOSES: PurposeOfUse[] = [
  "TREATMENT",
  "PAYMENT",
  "OPERATIONS",
  "RESEARCH",
  "PUBLIC_HEALTH",
  "EMERGENCY",
  "BREAK_GLASS",
  "SYSTEM",
];

/**
 * Local dev only: push form values into Zustand so apiClient injects the same
 * trust headers as production-shaped flows. This is not OIDC login.
 */
function applyLocalTrustVectors(params: {
  tenantId: string;
  actorId: string;
  actorType: ActorType;
  purposeOfUse: PurposeOfUse;
  facilityId: string;
  workspaceId: string;
}) {
  const setSession = useSession.getState().setSession;
  const setWorkContext = useWorkContext.getState().setWorkContext;
  const setPurposeOfUse = useWorkContext.getState().setPurposeOfUse;

  setSession({
    actorId: params.actorId.trim(),
    actorType: params.actorType,
    displayName: "Local trust-vector (not OIDC)",
    roles: [],
    accessToken: "local-dev-header-placeholder-not-from-keycloak",
    expiresAt: Date.now() + 3_600_000,
  });

  setWorkContext({
    tenantId: params.tenantId.trim(),
    facilityId: params.facilityId.trim() || undefined,
    workspaceId: params.workspaceId.trim() || undefined,
  });

  setPurposeOfUse(params.purposeOfUse);
}

export function LocalRuntimeCheckShell() {
  const [tenantId, setTenantId] = useState(DEFAULT_TENANT);
  const [actorId, setActorId] = useState("local-test-actor");
  const [actorType, setActorType] = useState<ActorType>("PROVIDER");
  const [purposeOfUse, setPurpose] = useState<PurposeOfUse>("TREATMENT");
  const [facilityId, setFacilityId] = useState("");
  const [workspaceId, setWorkspaceId] = useState("");

  const [policyAction, setPolicyAction] = useState("POST:/api/v1/authorize");
  const [policyResourceType, setPolicyResourceType] = useState("authorize");
  const [policyResourceId, setPolicyResourceId] = useState("");

  const [policyLoading, setPolicyLoading] = useState(false);
  const [policyJson, setPolicyJson] = useState<string | null>(null);
  const [policyError, setPolicyError] = useState<string | null>(null);

  const applyVectors = useCallback(() => {
    applyLocalTrustVectors({
      tenantId,
      actorId,
      actorType,
      purposeOfUse,
      facilityId,
      workspaceId,
    });
  }, [
    tenantId,
    actorId,
    actorType,
    purposeOfUse,
    facilityId,
    workspaceId,
  ]);

  const runPolicyCheck = useCallback(async () => {
    setPolicyLoading(true);
    setPolicyJson(null);
    setPolicyError(null);
    applyVectors();
    try {
      const response = await apiClient.post<AuthzResponse>(
        "/api/v1/authorize",
        {
          action: policyAction,
          resourceType: policyResourceType,
          resourceId: policyResourceId || undefined,
        },
        { purposeOfUse }
      );
      setPolicyJson(JSON.stringify(response.data, null, 2));
    } catch (err: unknown) {
      const message =
        err && typeof err === "object"
          ? JSON.stringify(err, null, 2)
          : String(err);
      setPolicyError(message);
    } finally {
      setPolicyLoading(false);
    }
  }, [
    applyVectors,
    policyAction,
    policyResourceType,
    policyResourceId,
    purposeOfUse,
  ]);

  const routesToday = useMemo(
    () => ["/ (this page — local runtime check)", "/_not-found (Next.js built-in)"],
    []
  );

  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900">
      <header className="border-b border-neutral-100 bg-white">
        <div className="mx-auto max-w-5xl px-4 py-6">
          <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">
            Impilo One UI Shell — Local Runtime Check
          </h1>
          <p className="mt-1 text-sm font-medium text-brand-primary">
            Thin local development shell
          </p>
          <p className="mt-2 max-w-3xl text-sm text-neutral-500">
            This page is for honest wiring checks against TSHEPO and infra. It
            does not perform OIDC login and must not be mistaken for production
            authentication.
          </p>
        </div>
      </header>

      <main className="mx-auto max-w-5xl space-y-6 px-4 py-8">
        <section className="grid gap-4 sm:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Runtime status</CardTitle>
            </CardHeader>
            <ul className="space-y-3 text-sm">
              <li className="flex items-center justify-between gap-2">
                <span>One UI Shell</span>
                <StatusIndicator status="active" label="running (dev)" />
              </li>
              <li className="flex items-center justify-between gap-2">
                <span>TSHEPO via /api/v1/authorize</span>
                <StatusIndicator status="pending" label="use Policy Check" />
              </li>
              <li className="text-neutral-500">
                Keycloak:{" "}
                <code className="text-xs text-neutral-900">
                  http://localhost:8080
                </code>
              </li>
              <li className="text-neutral-500">
                HAPI FHIR:{" "}
                <code className="text-xs text-neutral-900">
                  http://localhost:8090/fhir
                </code>
              </li>
              <li className="flex flex-wrap items-center gap-2">
                <span>Gateway mode</span>
                <Badge variant="default">README local bridge</Badge>
              </li>
            </ul>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Identity and context (local only)</CardTitle>
            </CardHeader>
            <p className="mb-4 text-sm text-neutral-500">
              No OpenID Connect flow is mounted in this package. Values below
              populate Zustand stores so{" "}
              <code className="text-xs">apiClient</code> can attach the same
              trust headers used in production-shaped calls. This is{" "}
              <strong>not</strong> a logged-in session.
            </p>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block text-xs font-medium text-neutral-500">
                x-tenant-id (UUID)
                <input
                  className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                  value={tenantId}
                  onChange={(e) => setTenantId(e.target.value)}
                  spellCheck={false}
                />
              </label>
              <label className="block text-xs font-medium text-neutral-500">
                x-actor-id
                <input
                  className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                  value={actorId}
                  onChange={(e) => setActorId(e.target.value)}
                  spellCheck={false}
                />
              </label>
              <label className="block text-xs font-medium text-neutral-500">
                x-actor-type
                <select
                  className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                  value={actorType}
                  onChange={(e) =>
                    setActorType(e.target.value as ActorType)
                  }
                >
                  {ACTOR_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-xs font-medium text-neutral-500">
                x-purpose-of-use
                <select
                  className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                  value={purposeOfUse}
                  onChange={(e) =>
                    setPurpose(e.target.value as PurposeOfUse)
                  }
                >
                  {PURPOSES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-xs font-medium text-neutral-500">
                x-facility-id (optional UUID)
                <input
                  className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                  value={facilityId}
                  onChange={(e) => setFacilityId(e.target.value)}
                  placeholder="empty = omit header"
                  spellCheck={false}
                />
              </label>
              <label className="block text-xs font-medium text-neutral-500">
                x-workspace-id (optional UUID)
                <input
                  className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                  value={workspaceId}
                  onChange={(e) => setWorkspaceId(e.target.value)}
                  placeholder="empty = omit header"
                  spellCheck={false}
                />
              </label>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              <Button type="button" variant="secondary" size="sm" onClick={applyVectors}>
                Apply to trust stores (local)
              </Button>
            </div>
          </Card>
        </section>

        <Card>
          <CardHeader>
            <CardTitle>Policy check</CardTitle>
          </CardHeader>
          <p className="mb-4 text-sm text-neutral-500">
            Uses existing <code className="text-xs">apiClient.post</code> to{" "}
            <code className="text-xs">/api/v1/authorize</code> (Next rewrite →
            TSHEPO). Applies your local context first, then shows the real JSON
            or error payload.
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            <label className="block text-xs font-medium text-neutral-500 sm:col-span-1">
              action (request hint)
              <input
                className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                value={policyAction}
                onChange={(e) => setPolicyAction(e.target.value)}
                spellCheck={false}
              />
            </label>
            <label className="block text-xs font-medium text-neutral-500 sm:col-span-1">
              resourceType (body)
              <input
                className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                value={policyResourceType}
                onChange={(e) => setPolicyResourceType(e.target.value)}
                spellCheck={false}
              />
            </label>
            <label className="block text-xs font-medium text-neutral-500 sm:col-span-1">
              resourceId (optional)
              <input
                className="mt-1 w-full rounded-lg border border-neutral-100 px-3 py-2 text-sm"
                value={policyResourceId}
                onChange={(e) => setPolicyResourceId(e.target.value)}
                spellCheck={false}
              />
            </label>
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button
              type="button"
              onClick={runPolicyCheck}
              disabled={policyLoading}
            >
              {policyLoading ? "Calling TSHEPO…" : "Run policy check"}
            </Button>
          </div>
          {policyJson && (
            <pre className="mt-4 max-h-64 overflow-auto rounded-lg bg-neutral-900 p-4 text-xs text-neutral-50">
              {policyJson}
            </pre>
          )}
          {policyError && (
            <pre className="mt-4 max-h-64 overflow-auto rounded-lg border border-danger/40 bg-red-50 p-4 text-xs text-danger">
              {policyError}
            </pre>
          )}
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Route / navigation readiness</CardTitle>
          </CardHeader>
          <p className="mb-2 text-sm text-neutral-500">
            Routes mounted in <code className="text-xs">src/app</code> today:
          </p>
          <ul className="list-inside list-disc text-sm text-neutral-900">
            {routesToday.map((r) => (
              <li key={r}>{r}</li>
            ))}
          </ul>
          <p className="mt-4 text-sm text-neutral-500">
            Next actions for real product flows: add OIDC/OAuth routes (e.g.
            login + callback), protect segments with session + TSHEPO-backed
            checks, mount workspace/facility switchers backed by registry
            services, and wire dashboards to gateway paths beyond this dev
            bridge.
          </p>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Nompilo readiness placeholder</CardTitle>
          </CardHeader>
          <p className="text-sm text-neutral-600">
            Nompilo is intended to surface contextual assistance alongside
            clinical and operational work. No Nompilo component is packaged in{" "}
            <code className="text-xs">one-ui-shell</code> or{" "}
            <code className="text-xs">shared-ui</code> yet, so nothing here
            calls an assistant or helpdesk backend. When the real surface lands,
            mount it from this shell without faking responses.
          </p>
        </Card>
      </main>
    </div>
  );
}
