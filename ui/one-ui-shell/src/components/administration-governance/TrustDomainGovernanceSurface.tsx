"use client";

import { useCallback, useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";

/**
 * Trust-domain governance workspace (Wave 1C).
 *
 * The surface has one job it must never get wrong: distinguishing "there is nothing here"
 * from "we could not ask". Right now the governance endpoints are closed by a server-side
 * gate until Envoy's ext_authz enforces on their route, so the honest rendering of this
 * page today is an explanation of that — not an empty table, which would read as "no trust
 * domains exist" and be false.
 *
 * Every governed state renders as itself. `UNDETERMINED` and `UNMAPPED` are real answers
 * with real consequences, not blanks: an organisation with no evidenced membership cannot
 * perform a trust-domain-dependent operation, and saying so is the point.
 */

type LoadState =
  | { kind: "LOADING" }
  | { kind: "READY"; domains: TrustDomain[] }
  | { kind: "DISABLED"; message: string; action: string }
  | { kind: "FORBIDDEN" }
  | { kind: "UNAVAILABLE"; detail: string };

interface TrustDomain {
  trustDomainId: string;
  code: string;
  displayName: string;
  controllerType: string | null;
  controllerDeclarationState: "UNDETERMINED" | "PENDING_GOVERNANCE_REVIEW" | "DECLARED";
  accreditationStatus: "PROVISIONAL" | "ACCREDITED" | "SUSPENDED" | "WITHDRAWN";
  dataControllerLegalName: string | null;
}

/** Every governed state gets a visible treatment. None of them is blank. */
const STATE_STYLES: Record<string, string> = {
  PROVISIONAL: "bg-amber-50 text-amber-900 ring-amber-200",
  ACCREDITED: "bg-emerald-50 text-emerald-900 ring-emerald-200",
  SUSPENDED: "bg-orange-50 text-orange-900 ring-orange-200",
  WITHDRAWN: "bg-slate-100 text-slate-700 ring-slate-300",
  UNDETERMINED: "bg-slate-100 text-slate-700 ring-slate-300",
  PENDING_GOVERNANCE_REVIEW: "bg-sky-50 text-sky-900 ring-sky-200",
  DECLARED: "bg-emerald-50 text-emerald-900 ring-emerald-200",
};

function StateChip({ value }: { value: string }) {
  const style = STATE_STYLES[value] ?? "bg-slate-100 text-slate-700 ring-slate-300";
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${style}`}
    >
      {value.replace(/_/g, " ").toLowerCase()}
    </span>
  );
}

export function TrustDomainGovernanceSurface() {
  const [state, setState] = useState<LoadState>({ kind: "LOADING" });

  const load = useCallback(async () => {
    setState({ kind: "LOADING" });
    try {
      const page = await apiClient.get<{ content?: TrustDomain[] }>(
        "/api/v1/governance/trust-domains?page=0&size=25",
      );
      setState({ kind: "READY", domains: page?.content ?? [] });
    } catch (error) {
      // api-client's apiRejection SPREADS the response body at the top level alongside
      // `status` — it does not nest it under `body`. Reading error.body here would always
      // be undefined and quietly fall back to generic text, which is precisely the
      // dishonest-state failure this surface exists to avoid.
      const rejection = (error ?? {}) as {
        status?: number;
        message?: string;
        requiredGovernanceAction?: string;
      };
      const status = rejection.status;
      if (status === 503) {
        // The gate is shut. Say so, and say what would open it — never an empty list.
        setState({
          kind: "DISABLED",
          message: rejection.message ?? "Trust-domain governance is not enabled on this deployment.",
          action:
            rejection.requiredGovernanceAction ??
            "Enable ext_authz for this route, then enable governance.",
        });
      } else if (status === 401 || status === 403) {
        setState({ kind: "FORBIDDEN" });
      } else {
        setState({
          kind: "UNAVAILABLE",
          detail: status ? `The registry answered ${status}.` : "The registry could not be reached.",
        });
      }
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section className="space-y-6" aria-labelledby="td-heading">
      <header className="space-y-2">
        <h1 id="td-heading" className="text-2xl font-semibold tracking-tight">
          Trust domains and responsibility
        </h1>
        <p className="max-w-3xl text-sm text-slate-600 dark:text-slate-300">
          A trust domain is a governed data-control and disclosure boundary. It is not a
          controller, and governance authority here grants <strong>no clinical access</strong>.
          Who is legally responsible for a record is resolved separately, per data domain and
          purpose, and may legitimately be undetermined.
        </p>
      </header>

      {state.kind === "LOADING" && (
        <p className="text-sm text-slate-500" role="status">
          Loading trust domains…
        </p>
      )}

      {state.kind === "DISABLED" && (
        <div role="status" className="rounded-lg border border-amber-300 bg-amber-50 p-4">
          <h2 className="text-sm font-semibold text-amber-900">Governance is switched off here</h2>
          <p className="mt-1 text-sm text-amber-900">{state.message}</p>
          <p className="mt-2 text-sm text-amber-800">
            <span className="font-medium">To enable it: </span>
            {state.action}
          </p>
          <p className="mt-2 text-xs text-amber-800">
            This is not an empty registry. Nothing was read, so nothing can be shown.
          </p>
        </div>
      )}

      {state.kind === "FORBIDDEN" && (
        <div role="status" className="rounded-lg border border-slate-300 bg-slate-50 p-4">
          <h2 className="text-sm font-semibold">You do not hold governance authority here</h2>
          <p className="mt-1 text-sm text-slate-700">
            Trust-domain governance is held by platform and institutional governance roles. This
            is a role boundary, not an error, and no step-up will change it.
          </p>
        </div>
      )}

      {state.kind === "UNAVAILABLE" && (
        <div role="alert" className="rounded-lg border border-red-300 bg-red-50 p-4">
          <h2 className="text-sm font-semibold text-red-900">The registry is unavailable</h2>
          <p className="mt-1 text-sm text-red-900">
            {state.detail} This is an outage, not an empty result — there may well be trust
            domains recorded.
          </p>
          <button
            type="button"
            onClick={() => void load()}
            className="mt-3 rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700"
          >
            Try again
          </button>
        </div>
      )}

      {state.kind === "READY" && state.domains.length === 0 && (
        <div role="status" className="rounded-lg border border-slate-300 bg-white p-4">
          <h2 className="text-sm font-semibold">No trust domains have been created yet</h2>
          <p className="mt-1 text-sm text-slate-700">
            The registry answered, and it holds none. Creating one records a governed boundary;
            it does not decide who controls any record within it.
          </p>
        </div>
      )}

      {state.kind === "READY" && state.domains.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <caption className="sr-only">Trust domains, their accreditation and controller declaration state</caption>
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-slate-500">
                <th scope="col" className="px-3 py-2">Code</th>
                <th scope="col" className="px-3 py-2">Name</th>
                <th scope="col" className="px-3 py-2">Accreditation</th>
                <th scope="col" className="px-3 py-2">Controller</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {state.domains.map((d) => (
                <tr key={d.trustDomainId}>
                  <td className="px-3 py-2 font-mono text-xs">{d.code}</td>
                  <td className="px-3 py-2">{d.displayName}</td>
                  <td className="px-3 py-2">
                    <StateChip value={d.accreditationStatus} />
                  </td>
                  <td className="px-3 py-2">
                    <StateChip value={d.controllerDeclarationState} />
                    {d.controllerDeclarationState === "DECLARED" && d.dataControllerLegalName ? (
                      <span className="ml-2 text-slate-700">{d.dataControllerLegalName}</span>
                    ) : (
                      <span className="ml-2 text-slate-500">
                        not determined — controller-dependent operations refuse
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export default TrustDomainGovernanceSurface;
