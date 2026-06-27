"use client";

import { Users, UserCheck } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDelegationStore } from "@/hooks/useDelegationStore";
import {
  useDelegationsAsDelegate,
  useDelegationsAsSubject,
  useRevokeDelegation,
  type DelegationRelationship,
} from "@/hooks/queries/useDelegations";

const REL_LABEL: Record<string, string> = {
  GUARDIAN: "Guardian",
  CAREGIVER: "Caregiver",
  CHW: "Community health worker",
  FACILITY_STAFF: "Facility staff",
};

function relLabel(t: string) {
  return REL_LABEL[t?.toUpperCase?.()] ?? t;
}

/**
 * Delegated-access management (L5, G-CZO-03): the subject sees and can revoke who may act for them;
 * the delegate sees who they may act for and can enter that "acting for" context. Backed by Mvumo
 * via the BFF proxy; revocation takes effect at the trust plane on the next request.
 */
export function DelegationManagementSection() {
  const user = useAuthStore((s) => s.user);
  const anchor = user?.healthId;
  const enterContext = useDelegationStore((s) => s.enterContext);
  const actingFor = useDelegationStore((s) => s.actingFor);

  const asSubject = useDelegationsAsSubject(anchor);
  const asDelegate = useDelegationsAsDelegate(anchor);
  const revoke = useRevokeDelegation();

  const active = (rows: DelegationRelationship[] | undefined) =>
    (rows ?? []).filter((d) => d.status === "GRANTED");

  const whoCanActForMe = active(asSubject.data?.data);
  const iCanActFor = active(asDelegate.data?.data);

  if (!anchor) return null;

  return (
    <section className="bg-card rounded-lg border border-border p-6">
      <h3 className="text-sm font-semibold text-foreground mb-4 flex items-center gap-2">
        <Users className="w-4 h-4 text-primary" />
        Acting on Behalf
      </h3>

      {/* Who can act for me — subject view, always revocable */}
      <div className="mb-5">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground mb-2">Who can act for me</p>
        {whoCanActForMe.length === 0 ? (
          <p className="text-sm text-muted-foreground">No one is currently authorised to act for you.</p>
        ) : (
          <ul className="space-y-2">
            {whoCanActForMe.map((d) => (
              <li key={d.id} className="flex items-center justify-between gap-3 text-sm">
                <span className="text-foreground">
                  {d.delegateActorId} <span className="text-muted-foreground">· {relLabel(d.relationshipType)}</span>
                </span>
                <button
                  type="button"
                  onClick={() => revoke.mutate({ id: d.id, reason: "Revoked by subject" })}
                  disabled={revoke.isPending}
                  className="shrink-0 rounded-md border border-border px-2.5 py-1 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-50"
                >
                  Revoke
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* I am acting for — delegate view, enter context */}
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground mb-2">I can act for</p>
        {iCanActFor.length === 0 ? (
          <p className="text-sm text-muted-foreground">You are not currently a delegate for anyone.</p>
        ) : (
          <ul className="space-y-2">
            {iCanActFor.map((d) => {
              const isActive = actingFor?.subjectRef === d.delegatorSubjectRef;
              return (
                <li key={d.id} className="flex items-center justify-between gap-3 text-sm">
                  <span className="text-foreground">
                    {d.delegatorSubjectRef} <span className="text-muted-foreground">· {relLabel(d.relationshipType)}</span>
                  </span>
                  <button
                    type="button"
                    disabled={isActive}
                    onClick={() =>
                      enterContext({
                        subjectRef: d.delegatorSubjectRef,
                        subjectLabel: d.delegatorSubjectRef,
                        relationshipType: d.relationshipType,
                      })
                    }
                    className="inline-flex shrink-0 items-center gap-1 rounded-md border border-primary/30 bg-primary-soft px-2.5 py-1 text-xs font-medium text-primary-hover hover:bg-primary/10 disabled:opacity-50"
                  >
                    <UserCheck className="w-3.5 h-3.5" />
                    {isActive ? "Active" : "Act as"}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}
