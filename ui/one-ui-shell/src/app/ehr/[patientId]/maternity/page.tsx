"use client";

/**
 * Maternity Monitoring — partograph, CTG and near-miss identification.
 *
 * Deliberately mobile-primary for CHW community postnatal contacts (RMNP W12,
 * `/internal/v1/confidential/community/postnatal/contacts`): that surface is a CHW's offline
 * home-visit capture, not a facility clinician's chart read, and this page has no clinician-facing
 * read affordance for it. See `apps/mobile/provider-app/src/screens/outreach/PostnatalContactScreen.tsx`
 * and docs/clinical/rmnp/chw-community-postnatal-mobile-contract.md. If a facility-side read of a
 * mother's community postnatal history is later needed here, it composes
 * `GET .../contacts/{motherCpid}` directly — it does not duplicate pct's record or invent a second
 * store, and it must preserve the same "empty is not proof of absence" rule the mobile screen does.
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { Baby, HeartPulse } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { VitalsCtgSection } from "@/features/maternity/ctg/VitalsCtgSection";
import { VitalsPartographSection } from "@/features/maternity/partograph/VitalsPartographSection";
import { NearMissAssessmentPanel } from "@/features/maternity/nearmiss/NearMissAssessmentPanel";
import { NearMissIndicatorsPanel } from "@/features/maternity/nearmiss/NearMissIndicatorsPanel";
import { EmergencyBundlePanel } from "@/features/maternity/emergency-bundles/EmergencyBundlePanel";
import { BishopScorePanel } from "@/features/maternity/bishop/BishopScorePanel";
import { MaternitySummaryPanel } from "@/features/maternity/MaternitySummaryPanel";
import { BirthDestinationPanel } from "@/features/maternity/BirthDestinationPanel";
import { PregnancyEpisodesPanel } from "@/features/maternity/reproductive/PregnancyEpisodesPanel";
import { ReproductiveConfidentialPanel } from "@/features/maternity/reproductive/ReproductiveConfidentialPanel";
import {
  DeliveryRecordPanel,
  FertilityPanel,
  PreconceptionPanel,
  ReproductiveIntentionPanel,
} from "@/features/maternity/reproductive/W14ReproductivePanels";
import { MaternityClinicalJourneysSection } from "@/features/maternity/journeys/MaternityClinicalJourneysSection";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useEncounters } from "@/hooks/queries/useEncounters";

export default function MaternityMonitoringPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) => encounter.attributes.isOpen,
  );
  const encounterId = activeEncounter?.id ?? "";

  return (
    <EHRLayout>
      <PageShell
        title="Maternity Monitoring"
        subtitle="Canonical maternity surface for partograph and CTG monitoring. Legacy labour rows stay on Vitals as compatibility-only."
      >
        <div className="mb-4 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
          <Link href={`/ehr/${encodeURIComponent(patientId)}/vitals`} className="inline-flex items-center gap-1 hover:text-foreground">
            <HeartPulse className="h-4 w-4" /> Back to vitals
          </Link>
          <span className="inline-flex items-center gap-1">
            <Baby className="h-4 w-4 text-pink-600" />
            {activeEncounter ? `Active encounter: ${encounterId}` : "No active encounter in scope"}
          </span>
        </div>

        <MaternitySummaryPanel patientId={patientId} encounterId={encounterId || undefined} />

        {isClinical && (
          <>
            <PregnancyEpisodesPanel patientCpid={patientId} />
            <ReproductiveConfidentialPanel patientCpid={patientId} />
            <section
              className="mb-4 rounded-lg border border-violet-200/90 bg-card p-5"
              data-testid="w14-reproductive-panel"
            >
              <h2 className="text-lg font-semibold text-foreground">
                Intention, preconception, fertility & delivery
              </h2>
              <p className="mt-1 text-xs text-muted-foreground">
                W14-B confidential lane — empty sections may mean withheld, not absent.
              </p>
              <div className="mt-4 space-y-5">
                <div>
                  <h3 className="text-sm font-medium text-foreground">Reproductive intention</h3>
                  <ReproductiveIntentionPanel
                    patientCpid={patientId}
                    recordedBy={user?.id ?? user?.displayName ?? "unknown"}
                  />
                </div>
                <div>
                  <h3 className="text-sm font-medium text-foreground">Preconception</h3>
                  <PreconceptionPanel patientCpid={patientId} />
                </div>
                <div>
                  <h3 className="text-sm font-medium text-foreground">Fertility</h3>
                  <FertilityPanel patientCpid={patientId} />
                </div>
                <div>
                  <h3 className="text-sm font-medium text-foreground">Delivery records</h3>
                  <DeliveryRecordPanel
                    patientCpid={patientId}
                    recordedBy={user?.id ?? user?.displayName ?? "unknown"}
                  />
                </div>
              </div>
            </section>
          </>
        )}

        <section className="rounded-lg border border-pink-200/90 bg-card p-5">
          <h2 className="text-lg font-semibold text-foreground">Partograph & CTG</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            This route reuses the same maternity feature modules mounted on Vitals, so promotion to a first-class
            maternity workflow keeps the BFF contract and UI behavior aligned.
          </p>

          <VitalsPartographSection
            patientId={patientId}
            encounterId={encounterId}
            hasActiveEncounter={Boolean(activeEncounter && encounterId)}
            isClinical={isClinical}
            recordedBy={user?.id ?? user?.displayName ?? "unknown"}
          />

          <VitalsCtgSection
            patientId={patientId}
            encounterId={encounterId}
            hasActiveEncounter={Boolean(activeEncounter && encounterId)}
            isClinical={isClinical}
            recordedBy={user?.id ?? user?.displayName ?? "unknown"}
          />
        </section>

        {isClinical && (
          <section className="mt-4 rounded-lg border border-pink-200/90 bg-card p-5">
            <h2 className="text-lg font-semibold text-foreground">Birth destination</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Checks whether a facility is a confirmed maternity destination for the level of
              emergency obstetric care a woman needs — composed from tuso&apos;s facility status
              and EmONC readiness.
            </p>
            <BirthDestinationPanel />
          </section>
        )}

        {isClinical && (
          <section className="mt-4 rounded-lg border border-red-200/90 bg-card p-5">
            <h2 className="text-lg font-semibold text-foreground">Near-miss identification</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              WHO organ-dysfunction criteria (near-miss approach, 2011) — identification only. Not on the
              confidential lane; the confidential MPDSR review is a separate, later instrument.
            </p>
            <NearMissAssessmentPanel patientId={patientId} />
            <NearMissIndicatorsPanel />
          </section>
        )}

        <MaternityClinicalJourneysSection
          patientId={patientId}
          encounterId={encounterId}
          hasActiveEncounter={Boolean(activeEncounter && encounterId)}
        />

        {isClinical && (
          <section className="mt-4 rounded-lg border border-pink-200/90 bg-card p-5">
            <h2 className="text-lg font-semibold text-foreground">Bishop score</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Cervical favourability for induction readiness — assessment only. A blank component is
              not zero; incomplete assessments say so explicitly.
            </p>
            <BishopScorePanel />
          </section>
        )}

        {isClinical && (
          <section className="mt-4 rounded-lg border border-red-300/90 bg-card p-5" id="emergency-bundles">
            <h2 className="text-lg font-semibold text-foreground">Emergency bundles</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              PPH and eclampsia first-response checklists — assessment only, nothing persisted here.
              A freshly-triggered bundle shows every mandatory step outstanding; silence on an open
              episode is escalation, not closure.
            </p>
            <EmergencyBundlePanel
              kind="pph"
              title="PPH first-response protocol"
              controlLabel="Bleeding controlled and confirmed"
              testIdPrefix="pph-protocol"
            />
            <EmergencyBundlePanel
              kind="eclampsia"
              title="Eclampsia / severe pre-eclampsia protocol"
              controlLabel="Seizure/control stable and confirmed"
              testIdPrefix="eclampsia-protocol"
            />
          </section>
        )}
      </PageShell>
    </EHRLayout>
  );
}
