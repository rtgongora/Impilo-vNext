"use client";

/**
 * Full emergency episode spine — FSM, location, identity, order sets, meds, alerts, handover.
 */

import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useEmergencyRealtime } from "@/hooks/useEmergencyRealtime";
import { EpisodeCareActionsPanel } from "@/features/emergency/spine/EpisodeCareActionsPanel";
import { IdentityLinkPanel } from "@/features/emergency/spine/IdentityLinkPanel";
import { EpisodeAlertsPanel } from "@/features/emergency/spine/EpisodeAlertsPanel";
import {
  MedicationAdminPanel,
  OrderSetsPanel,
} from "@/features/emergency/spine/OrderSetsAndMedsPanels";
import {
  useEmergencyAlertActions,
  useEmergencyEpisode,
  useEmergencyEpisodeActions,
  useEmergencyEpisodeAlerts,
  useEmergencyHandoverActions,
  useEmergencyHandoverHistory,
  useEmergencyIdentityLinks,
  useEmergencyMedications,
  useEmergencyOrderSetActions,
  useEmergencyOrderSets,
  useLinkEmergencyIdentity,
  useRecordEmergencyMedication,
} from "@/hooks/queries/useEmergencyEpisode";
import { useIntakeMentalHealthReferral } from "@/hooks/queries/useMentalHealth";

const HANDOVER_TARGET_TYPES = [
  "ADMISSION",
  "THEATRE",
  "MENTAL_HEALTH",
  "INTERFACILITY_TRANSFER",
  "OTHER_SERVICE",
] as const;

const STATE_LABEL: Record<string, string> = {
  PRE_ARRIVAL: "Pre-arrival",
  OPEN_UNTRIAGED: "Untriaged",
  OPEN_IN_CARE: "In care",
  OPEN_AWAITING_ACCEPTANCE: "Awaiting acceptance",
  CLOSED_HANDED_OVER: "Closed — handed over",
  CLOSED_DISCHARGED: "Closed — discharged",
  CLOSED_DIED: "Closed — died",
};

export default function EmergencyEpisodeSpinePage({ params }: { params: { episodeId: string } }) {
  const { user } = useAuthStore();
  const facility = useFacilityStore((s) => s.facility);
  const episode = useEmergencyEpisode(params.episodeId);
  const handovers = useEmergencyHandoverHistory(params.episodeId);
  useEmergencyRealtime(Boolean(params.episodeId));
  const actions = useEmergencyEpisodeActions(params.episodeId);
  const identityLinks = useEmergencyIdentityLinks(params.episodeId);
  const linkIdentity = useLinkEmergencyIdentity(params.episodeId);
  const orderSets = useEmergencyOrderSets(params.episodeId);
  const orderActions = useEmergencyOrderSetActions(params.episodeId);
  const medications = useEmergencyMedications(params.episodeId);
  const recordMed = useRecordEmergencyMedication(params.episodeId);
  const episodeAlerts = useEmergencyEpisodeAlerts(params.episodeId);
  const alertActions = useEmergencyAlertActions(facility?.id, params.episodeId);

  const actor = user?.providerId ?? user?.staffId ?? user?.id ?? "";
  const [targetType, setTargetType] = useState<string>("ADMISSION");
  const [mhIntakeNote, setMhIntakeNote] = useState<{
    status: string;
    referralId?: string;
    error?: string;
    handoverId?: string;
  } | null>(null);
  const intakeRetry = useIntakeMentalHealthReferral();
  const pendingHandover = handovers.data?.find((h) => h.status === "PENDING");

  const mutating =
    actions.arrive.isPending ||
    actions.transition.isPending ||
    actions.confirmLocation.isPending ||
    actions.requestHandover.isPending ||
    linkIdentity.isPending ||
    orderActions.invoke.isPending ||
    orderActions.orderItem.isPending ||
    orderActions.declineItem.isPending ||
    recordMed.isPending ||
    alertActions.acknowledge.isPending ||
    alertActions.respond.isPending ||
    alertActions.close.isPending;

  return (
    <AppLayout>
      <PageShell
        title={episode.data ? String(episode.data.episode_reference ?? params.episodeId) : "Emergency episode"}
        subtitle="pct.emergency_episode — continuum-side spine"
      >
        <div className="mb-4 flex flex-wrap justify-end gap-x-4 gap-y-1 text-sm">
          <Link
            href={`/clinical/emergency/spine/${params.episodeId}/observation`}
            className="text-primary underline"
          >
            Observation stay
          </Link>
          <Link
            href={`/clinical/emergency/spine/${params.episodeId}/disposition`}
            className="text-primary underline"
          >
            Disposition
          </Link>
          <Link href="/clinical/emergency/board" className="text-primary underline">
            ← Emergency board
          </Link>
        </div>

        {episode.isLoading && <p className="text-sm text-muted-foreground">Loading episode…</p>}
        {episode.isError && <p className="text-sm text-destructive">Could not load this episode.</p>}

        {episode.data && (
          <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
            <div className="space-y-4">
              <div className="rounded border p-4">
                <div className="flex items-center justify-between">
                  <span className="rounded bg-muted px-2 py-1 text-sm font-medium">
                    {STATE_LABEL[String(episode.data.state)] ?? String(episode.data.state)}
                  </span>
                  {episode.data.sensitive && (
                    <span className="rounded bg-destructive/10 px-2 py-1 text-xs text-destructive">
                      Sensitive
                    </span>
                  )}
                </div>
                <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
                  <dt className="text-muted-foreground">Entry route</dt>
                  <dd>{String(episode.data.entry_route ?? "—").replaceAll("_", " ")}</dd>
                  <dt className="text-muted-foreground">Identity</dt>
                  <dd>
                    {episode.data.subject_cpid
                      ? "Identified"
                      : String(episode.data.identity_mode ?? "Unknown")}
                  </dd>
                  <dt className="text-muted-foreground">Location</dt>
                  <dd>{String(episode.data.current_location ?? "—")}</dd>
                  <dt className="text-muted-foreground">Journey</dt>
                  <dd>{episode.data.journey_id ? String(episode.data.journey_id) : "Not yet anchored"}</dd>
                </dl>
              </div>

              <EpisodeCareActionsPanel
                episode={episode.data}
                isMutating={mutating}
                onArrive={(body) => actions.arrive.mutateAsync(body)}
                onTransition={(body) => actions.transition.mutateAsync(body)}
                onConfirmLocation={(body) => actions.confirmLocation.mutateAsync(body)}
              />

              <IdentityLinkPanel
                links={identityLinks.data}
                isLoading={identityLinks.isLoading}
                isError={identityLinks.isError}
                actor={actor}
                isMutating={linkIdentity.isPending}
                onLink={(body) => linkIdentity.mutateAsync(body)}
              />

              <OrderSetsPanel
                sets={orderSets.data}
                isLoading={orderSets.isLoading}
                isError={orderSets.isError}
                actor={actor}
                isMutating={
                  orderActions.invoke.isPending ||
                  orderActions.orderItem.isPending ||
                  orderActions.declineItem.isPending
                }
                onInvoke={(body) => orderActions.invoke.mutateAsync(body)}
                onOrderItem={(body) => orderActions.orderItem.mutateAsync(body)}
                onDeclineItem={(body) => orderActions.declineItem.mutateAsync(body)}
              />

              <MedicationAdminPanel
                medications={medications.data}
                isLoading={medications.isLoading}
                isError={medications.isError}
                actor={actor}
                isMutating={recordMed.isPending}
                onRecord={(body) => recordMed.mutateAsync(body)}
              />

              <div className="rounded border p-4">
                <h3 className="mb-2 text-sm font-semibold">Acceptance handshake</h3>
                {pendingHandover ? (
                  <div className="space-y-2">
                    <p className="text-sm">
                      Handover requested to <strong>{String(pendingHandover.target_type)}</strong> —
                      awaiting acceptance. Requesting does not close this episode.
                    </p>
                    <AcceptDeclineForm
                      handoverId={String(pendingHandover.handover_id)}
                      episodeId={params.episodeId}
                      actor={actor || user?.displayName || "unknown"}
                    />
                  </div>
                ) : (
                  <div className="flex flex-wrap items-center gap-2">
                    <select
                      className="rounded border px-2 py-1 text-sm"
                      value={targetType}
                      onChange={(e) => setTargetType(e.target.value)}
                      aria-label="Handover target type"
                    >
                      {HANDOVER_TARGET_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {t.replaceAll("_", " ")}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
                      disabled={
                        episode.data.state !== "OPEN_IN_CARE" ||
                        actions.requestHandover.isPending ||
                        !actor
                      }
                      onClick={() => {
                        void actions.requestHandover
                          .mutateAsync({
                            targetType,
                            requestedBy: actor,
                            facilityId: facility?.id,
                            subjectCpid: episode.data.subject_cpid
                              ? String(episode.data.subject_cpid)
                              : undefined,
                          })
                          .then((res) => {
                            const data = (res as { data?: Record<string, unknown> })?.data ?? {};
                            if (targetType === "MENTAL_HEALTH") {
                              setMhIntakeNote({
                                status: String(data.mh_intake_status ?? "UNKNOWN"),
                                referralId: data.referral_id ? String(data.referral_id) : undefined,
                                error: data.mh_intake_error
                                  ? String(data.mh_intake_error)
                                  : undefined,
                                handoverId: data.handover_id
                                  ? String(data.handover_id)
                                  : undefined,
                              });
                            } else {
                              setMhIntakeNote(null);
                            }
                          });
                      }}
                    >
                      {actions.requestHandover.isPending ? "Requesting…" : "Request handover"}
                    </button>
                  </div>
                )}
                {mhIntakeNote && targetType === "MENTAL_HEALTH" ? (
                  <div
                    className="mt-3 rounded border border-border p-3 text-sm"
                    data-testid="mh-intake-status"
                  >
                    {mhIntakeNote.status === "OK" ? (
                      <p>
                        Mental-health referral queued.{" "}
                        <Link href="/work/mental-health" className="text-primary underline">
                          Open MH queue
                        </Link>
                        {mhIntakeNote.referralId
                          ? ` · referral ${mhIntakeNote.referralId}`
                          : null}
                      </p>
                    ) : (
                      <div className="space-y-2">
                        <p className="text-destructive">
                          Handover recorded, but MH intake failed
                          {mhIntakeNote.error ? `: ${mhIntakeNote.error}` : "."}
                        </p>
                        <button
                          type="button"
                          className="rounded border px-2 py-1 text-xs disabled:opacity-50"
                          disabled={
                            !mhIntakeNote.handoverId ||
                            intakeRetry.isPending ||
                            !facility?.id
                          }
                          onClick={() => {
                            if (!mhIntakeNote.handoverId || !facility?.id) return;
                            void intakeRetry
                              .mutateAsync({
                                episodeId: params.episodeId,
                                handoverId: mhIntakeNote.handoverId,
                                facilityId: facility.id,
                                subjectCpid: episode.data?.subject_cpid
                                  ? String(episode.data.subject_cpid)
                                  : undefined,
                                requestedBy: actor,
                              })
                              .then((res) => {
                                const data = (res as { data?: Record<string, unknown> })?.data;
                                setMhIntakeNote({
                                  status: "OK",
                                  referralId: data?.id ? String(data.id) : undefined,
                                  handoverId: mhIntakeNote.handoverId,
                                });
                              });
                          }}
                        >
                          {intakeRetry.isPending ? "Retrying…" : "Retry MH intake"}
                        </button>
                      </div>
                    )}
                  </div>
                ) : null}
              </div>

              <div className="rounded border p-4">
                <h3 className="mb-2 text-sm font-semibold">Handover history</h3>
                {(handovers.data ?? []).length === 0 && (
                  <p className="text-sm text-muted-foreground">No handovers requested yet.</p>
                )}
                <ul className="space-y-1 text-sm">
                  {(handovers.data ?? []).map((h) => (
                    <li key={String(h.handover_id)} className="flex justify-between">
                      <span>{String(h.target_type)}</span>
                      <span className="text-muted-foreground">{String(h.status)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>

            <div className="space-y-4">
              <EpisodeAlertsPanel
                alerts={episodeAlerts.data}
                isLoading={episodeAlerts.isLoading}
                isError={episodeAlerts.isError}
                actor={actor}
                isMutating={
                  alertActions.acknowledge.isPending ||
                  alertActions.respond.isPending ||
                  alertActions.close.isPending
                }
                onAcknowledge={(body) => alertActions.acknowledge.mutateAsync(body)}
                onRespond={(body) => alertActions.respond.mutateAsync(body)}
                onClose={(body) => alertActions.close.mutateAsync(body)}
              />
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}

function AcceptDeclineForm({
  handoverId,
  episodeId,
  actor,
}: {
  handoverId: string;
  episodeId: string;
  actor: string;
}) {
  const handoverActions = useEmergencyHandoverActions(handoverId, episodeId);
  const [acceptingRef, setAcceptingRef] = useState("");
  const [declineReason, setDeclineReason] = useState("");

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <input
          className="flex-1 rounded border px-2 py-1 text-sm"
          placeholder="Your own record id (e.g. admission id)"
          value={acceptingRef}
          onChange={(e) => setAcceptingRef(e.target.value)}
        />
        <button
          type="button"
          className="rounded bg-primary px-3 py-1 text-sm text-primary-foreground disabled:opacity-50"
          disabled={!acceptingRef.trim() || handoverActions.accept.isPending}
          onClick={() =>
            handoverActions.accept.mutate({ acceptedBy: actor, acceptingRef })
          }
        >
          {handoverActions.accept.isPending ? "Accepting…" : "Accept"}
        </button>
      </div>
      <div className="flex gap-2">
        <input
          className="flex-1 rounded border px-2 py-1 text-sm"
          placeholder="Decline reason"
          value={declineReason}
          onChange={(e) => setDeclineReason(e.target.value)}
        />
        <button
          type="button"
          className="rounded border px-3 py-1 text-sm disabled:opacity-50"
          disabled={!declineReason.trim() || handoverActions.decline.isPending}
          onClick={() =>
            handoverActions.decline.mutate({ declinedBy: actor, reason: declineReason })
          }
        >
          {handoverActions.decline.isPending ? "Declining…" : "Decline"}
        </button>
        <button
          type="button"
          className="rounded border px-3 py-1 text-sm disabled:opacity-50"
          disabled={handoverActions.expire.isPending}
          data-testid="handover-expire"
          onClick={() => handoverActions.expire.mutate({ expiredBy: actor })}
        >
          {handoverActions.expire.isPending ? "Expiring…" : "Expire"}
        </button>
      </div>
    </div>
  );
}
