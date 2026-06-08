/**
 * Madi — React Query hooks
 *
 * Centralises data access for the Madi frontend. Mutations invalidate related
 * queries so facility staff and donors see fresh state after each action.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  madiApi,
  unwrapMadiData,
  type CloseCasePayload,
  type DonorProfile,
  type CollectSamplePayload,
  type CompleteTransfusionPayload,
  type CreateBloodOrderPayload,
  type CreateDrivePayload,
  type CrossmatchPayload,
  type DonorDeferralPayload,
  type DonorFeedbackPayload,
  type DonorPreScreeningPayload,
  type DonorPreferencesPayload,
  type DonorScreeningPayload,
  type DriveRegisterPayload,
  type DriveScreenPayload,
  type InvestigateCasePayload,
  type IssueUnitPayload,
  type OpenHaemovigilanceCasePayload,
  type PreVerifyTransfusionPayload,
  type PrepareComponentPayload,
  type ResolveSyncConflictPayload,
  type SyncConflictPayload,
  type ProcessingQuarantinePayload,
  type ProcessingReceivePayload,
  type ProcessingReleasePayload,
  type ProcessingTestPayload,
  type RecordDonationPayload,
  type RegisterBloodBankPayload,
  type RegisterDonorPayload,
  type ReportReactionPayload,
  type ReserveUnitPayload,
  type ReturnPayload,
  type StartTransfusionPayload,
  type StockMovementPayload,
  type StockReconciliationPayload,
  type TransfusionObservationPayload,
  type VerifyTransfusionPayload,
  type WastagePayload,
} from "@/lib/madi";

export const madiQueryKeys = {
  all: ["madi"] as const,
  dashboard: (params?: Record<string, unknown>) =>
    [...madiQueryKeys.all, "dashboard", params ?? {}] as const,
  centralBank: () => [...madiQueryKeys.all, "central-bank"] as const,
  forecast: (facilityId?: string) => [...madiQueryKeys.all, "forecast", facilityId ?? "all"] as const,
  emergencyRedistributions: () => [...madiQueryKeys.all, "emergency-redistributions"] as const,
  ziboPins: (jurisdiction: string) => [...madiQueryKeys.all, "zibo-pins", jurisdiction] as const,
  drives: () => [...madiQueryKeys.all, "drives"] as const,
  driveMetrics: (driveId: string) => [...madiQueryKeys.all, "drive-metrics", driveId] as const,
  donorByPerson: (cpid: string) => [...madiQueryKeys.all, "donor", "person", cpid] as const,
  donorHistory: (donorId: string) => [...madiQueryKeys.all, "donor", donorId, "history"] as const,
  donorEligibility: (donorId: string) =>
    [...madiQueryKeys.all, "donor", donorId, "eligibility"] as const,
  drivesNearMe: (siteRef: string, radiusKm?: number) =>
    [...madiQueryKeys.all, "drives-near-me", siteRef, radiusKm ?? 25] as const,
  bloodBankStock: (bloodBankId: string) =>
    [...madiQueryKeys.all, "blood-bank", bloodBankId, "stock"] as const,
  bloodFridges: (bloodBankId: string) =>
    [...madiQueryKeys.all, "blood-bank", bloodBankId, "fridges"] as const,
  fridgeReadings: (fridgeId: string) =>
    [...madiQueryKeys.all, "fridge", fridgeId, "readings"] as const,
  bloodOrderDetail: (orderId: string) =>
    [...madiQueryKeys.all, "order", orderId] as const,
  transfusionEpisode: (episodeId: string) =>
    [...madiQueryKeys.all, "transfusion", episodeId] as const,
  nationalHaemovigilance: () => [...madiQueryKeys.all, "haemovigilance", "national"] as const,
  donorPreScreening: (donorId: string) =>
    [...madiQueryKeys.all, "donor", donorId, "pre-screening"] as const,
};

function invalidateMadi(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: madiQueryKeys.all });
}

// ---- Dashboards ----------------------------------------------------------

export function useMadiDashboard(params?: {
  scope?: "local" | "facility" | "central" | "drive";
  facilityId?: string;
  driveId?: string;
}) {
  return useQuery({
    queryKey: madiQueryKeys.dashboard({
      scope: params?.scope,
      facility_id: params?.facilityId,
      drive_id: params?.driveId,
    }),
    queryFn: () =>
      madiApi.dashboard({
        scope: params?.scope,
        facility_id: params?.facilityId,
        drive_id: params?.driveId,
      }),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useMadiCentralBankMetrics() {
  return useQuery({
    queryKey: madiQueryKeys.centralBank(),
    queryFn: () => madiApi.centralBankMetrics(),
    staleTime: 15_000,
    refetchInterval: 30_000,
  });
}

export function useMadiThirtyDayForecast(facilityId?: string) {
  return useQuery({
    queryKey: madiQueryKeys.forecast(facilityId),
    queryFn: () => madiApi.dashboardForecast(facilityId),
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
}

export function useEmergencyRedistributions() {
  return useQuery({
    queryKey: madiQueryKeys.emergencyRedistributions(),
    queryFn: () => madiApi.listEmergencyRedistributions(),
    staleTime: 15_000,
  });
}

export function useRequestEmergencyRedistribution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: madiApi.requestEmergencyRedistribution,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.emergencyRedistributions() });
      qc.invalidateQueries({ queryKey: madiQueryKeys.centralBank() });
    },
  });
}

export function useApproveEmergencyRedistribution() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      redistributionId,
      ...body
    }: {
      redistributionId: string;
      approved_by: string;
      units_allocated?: number;
      status?: string;
    }) => madiApi.approveEmergencyRedistribution(redistributionId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.emergencyRedistributions() });
      qc.invalidateQueries({ queryKey: madiQueryKeys.centralBank() });
    },
  });
}

export function useInitiateEmergencyHandoff() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      redistributionId,
      ...body
    }: {
      redistributionId: string;
      initiated_by?: string;
    }) => madiApi.initiateEmergencyHandoff(redistributionId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.emergencyRedistributions() });
    },
  });
}

export function useMadiZiboComponentPins(jurisdiction = "ZW") {
  return useQuery({
    queryKey: madiQueryKeys.ziboPins(jurisdiction),
    queryFn: () => madiApi.componentTerminologyPins(jurisdiction),
    staleTime: 300_000,
  });
}

// ---- Donors --------------------------------------------------------------

export function useDonorByPerson(personCpid: string | undefined) {
  return useQuery({
    queryKey: personCpid
      ? madiQueryKeys.donorByPerson(personCpid)
      : ["madi", "donor", "_disabled"],
    queryFn: async () => {
      if (!personCpid) return null;
      const res = await madiApi.donorByPerson(personCpid);
      return unwrapMadiData<DonorProfile | null>(res);
    },
    enabled: Boolean(personCpid),
    staleTime: 30_000,
    retry: false,
  });
}

export function useDonorHistory(donorId: string | undefined) {
  return useQuery({
    queryKey: donorId
      ? madiQueryKeys.donorHistory(donorId)
      : ["madi", "donor", "_disabled", "history"],
    queryFn: () => (donorId ? madiApi.donorHistory(donorId) : Promise.resolve(null)),
    enabled: Boolean(donorId),
    staleTime: 15_000,
  });
}

export function useDonorNextEligibility(donorId: string | undefined) {
  return useQuery({
    queryKey: donorId
      ? madiQueryKeys.donorEligibility(donorId)
      : ["madi", "donor", "_disabled", "eligibility"],
    queryFn: () => (donorId ? madiApi.donorNextEligibility(donorId) : Promise.resolve(null)),
    enabled: Boolean(donorId),
    staleTime: 60_000,
  });
}

export function useDrivesNearMe(ndilaSiteRef: string | undefined, radiusKm = 25) {
  return useQuery({
    queryKey: ndilaSiteRef
      ? madiQueryKeys.drivesNearMe(ndilaSiteRef, radiusKm)
      : ["madi", "drives-near-me", "_disabled"],
    queryFn: () =>
      ndilaSiteRef ? madiApi.drivesNearMe(ndilaSiteRef, radiusKm) : Promise.resolve([]),
    enabled: Boolean(ndilaSiteRef),
    staleTime: 60_000,
  });
}

export function useRegisterDonor() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RegisterDonorPayload) => madiApi.registerDonor(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useScreenDonor(donorId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DonorScreeningPayload) => madiApi.screenDonor(donorId, body),
    onSuccess: () => {
      invalidateMadi(qc);
      qc.invalidateQueries({ queryKey: madiQueryKeys.donorHistory(donorId) });
      qc.invalidateQueries({ queryKey: madiQueryKeys.donorEligibility(donorId) });
    },
  });
}

export function useDeferDonor(donorId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DonorDeferralPayload) => madiApi.deferDonor(donorId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useUpdateDonorPreferences(donorId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DonorPreferencesPayload) => madiApi.updateDonorPreferences(donorId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useSubmitDonorFeedback(donorId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DonorFeedbackPayload) => madiApi.submitDonorFeedback(donorId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useSubmitDonorPreScreening(donorId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DonorPreScreeningPayload) => madiApi.submitDonorPreScreening(donorId, body),
    onSuccess: () => {
      invalidateMadi(qc);
      qc.invalidateQueries({ queryKey: madiQueryKeys.donorPreScreening(donorId) });
    },
  });
}

export function useLatestDonorPreScreening(donorId: string | undefined) {
  return useQuery({
    queryKey: donorId
      ? madiQueryKeys.donorPreScreening(donorId)
      : ["madi", "donor", "_disabled", "pre-screening"],
    queryFn: () => (donorId ? madiApi.latestDonorPreScreening(donorId) : Promise.resolve(null)),
    enabled: Boolean(donorId),
    staleTime: 30_000,
    retry: false,
  });
}

// ---- Drives --------------------------------------------------------------

export function useMadiDrives() {
  return useQuery({
    queryKey: madiQueryKeys.drives(),
    queryFn: () => madiApi.listDrives(),
    staleTime: 15_000,
  });
}

export function useCreateDrive() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateDrivePayload) => madiApi.createDrive(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function usePublishDrive(driveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: { published_by?: string }) => madiApi.publishDrive(driveId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useRegisterForDrive(driveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DriveRegisterPayload) => madiApi.registerForDrive(driveId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useScreenAtDrive(driveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: DriveScreenPayload) => madiApi.screenAtDrive(driveId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useRecordDonation(driveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RecordDonationPayload) => madiApi.recordDonation(driveId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useCloseDrive(driveId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => madiApi.closeDrive(driveId),
    onSuccess: () => invalidateMadi(qc),
  });
}

// ---- Blood bank ----------------------------------------------------------

export function useBloodBankStock(bloodBankId: string | undefined) {
  return useQuery({
    queryKey: bloodBankId
      ? madiQueryKeys.bloodBankStock(bloodBankId)
      : ["madi", "blood-bank", "_disabled", "stock"],
    queryFn: () => (bloodBankId ? madiApi.bloodBankStock(bloodBankId) : Promise.resolve([])),
    enabled: Boolean(bloodBankId),
    staleTime: 10_000,
  });
}

export function useRegisterBloodBank() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: RegisterBloodBankPayload) => madiApi.registerBloodBank(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useRecordStockMovement(bloodBankId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StockMovementPayload) => madiApi.recordStockMovement(bloodBankId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.bloodBankStock(bloodBankId) });
      invalidateMadi(qc);
    },
  });
}

export function useReconcileStock(bloodBankId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StockReconciliationPayload) => madiApi.reconcileStock(bloodBankId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.bloodBankStock(bloodBankId) });
      invalidateMadi(qc);
    },
  });
}

export function useRecordWastage(bloodBankId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: WastagePayload) => madiApi.recordWastage(bloodBankId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.bloodBankStock(bloodBankId) });
      invalidateMadi(qc);
    },
  });
}

export function useRecordBloodReturn(bloodBankId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ReturnPayload) => madiApi.recordReturn(bloodBankId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.bloodBankStock(bloodBankId) });
      invalidateMadi(qc);
    },
  });
}

export function useBloodFridges(bloodBankId: string | undefined) {
  return useQuery({
    queryKey: bloodBankId
      ? madiQueryKeys.bloodFridges(bloodBankId)
      : ["madi", "blood-bank", "_disabled", "fridges"],
    queryFn: () => (bloodBankId ? madiApi.listBloodFridges(bloodBankId) : Promise.resolve([])),
    enabled: Boolean(bloodBankId),
    staleTime: 10_000,
    refetchInterval: 30_000,
  });
}

export function useFridgeReadings(fridgeId: string | undefined) {
  return useQuery({
    queryKey: fridgeId
      ? madiQueryKeys.fridgeReadings(fridgeId)
      : ["madi", "fridge", "_disabled", "readings"],
    queryFn: () => (fridgeId ? madiApi.fridgeReadings(fridgeId) : Promise.resolve([])),
    enabled: Boolean(fridgeId),
    staleTime: 10_000,
    refetchInterval: 30_000,
  });
}

export function useSyncFridgeIot(fridgeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => madiApi.syncFridgeIot(fridgeId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.fridgeReadings(fridgeId) });
      invalidateMadi(qc);
    },
  });
}

// ---- Orders --------------------------------------------------------------

export function useCreateBloodOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateBloodOrderPayload) => madiApi.createBloodOrder(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useSubmitBloodOrder(orderId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => madiApi.submitBloodOrder(orderId),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useCollectOrderSample(orderId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CollectSamplePayload) => madiApi.collectOrderSample(orderId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useCrossmatchOrder(orderId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CrossmatchPayload) => madiApi.crossmatchOrder(orderId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useReserveOrderUnit(orderId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ReserveUnitPayload) => madiApi.reserveOrderUnit(orderId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useIssueOrderUnit(orderId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: IssueUnitPayload) => madiApi.issueOrderUnit(orderId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useBloodOrderDetail(orderId: string | undefined) {
  return useQuery({
    queryKey: orderId ? madiQueryKeys.bloodOrderDetail(orderId) : ["madi", "order", "_disabled"],
    queryFn: () => (orderId ? madiApi.bloodOrderDetail(orderId) : Promise.resolve(null)),
    enabled: Boolean(orderId),
    staleTime: 15_000,
  });
}

// ---- Transfusions --------------------------------------------------------

export function useStartTransfusion() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: StartTransfusionPayload) => madiApi.startTransfusion(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useTransfusionEpisode(episodeId: string | undefined) {
  return useQuery({
    queryKey: episodeId
      ? madiQueryKeys.transfusionEpisode(episodeId)
      : ["madi", "transfusion", "_disabled"],
    queryFn: () => (episodeId ? madiApi.transfusionEpisode(episodeId) : Promise.resolve(null)),
    enabled: Boolean(episodeId),
    staleTime: 10_000,
  });
}

export function usePreVerifyTransfusion(episodeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: PreVerifyTransfusionPayload) => madiApi.preVerifyTransfusion(episodeId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: madiQueryKeys.transfusionEpisode(episodeId) });
      invalidateMadi(qc);
    },
  });
}

export function useRecordTransfusionObservation(episodeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: TransfusionObservationPayload) =>
      madiApi.recordTransfusionObservation(episodeId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useCompleteTransfusion(episodeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CompleteTransfusionPayload) => madiApi.completeTransfusion(episodeId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useVerifyTransfusion(episodeId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: VerifyTransfusionPayload) => madiApi.verifyTransfusion(episodeId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

// ---- Processing ----------------------------------------------------------

export function useReceiveProcessingBatch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProcessingReceivePayload) => madiApi.receiveProcessingBatch(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useRecordProcessingTest(batchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProcessingTestPayload) => madiApi.recordProcessingTest(batchId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useQuarantineProcessingBatch(batchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ProcessingQuarantinePayload) =>
      madiApi.quarantineProcessingBatch(batchId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useReleaseProcessingBatch(batchId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: ProcessingReleasePayload) => madiApi.releaseProcessingBatch(batchId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function usePrepareComponent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: PrepareComponentPayload) => madiApi.prepareComponent(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

// ---- Haemovigilance ------------------------------------------------------

export function useReportReaction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ReportReactionPayload) => madiApi.reportReaction(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useOpenHaemovigilanceCase() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: OpenHaemovigilanceCasePayload) => madiApi.openHaemovigilanceCase(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useInvestigateHaemovigilanceCase(caseId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: InvestigateCasePayload) => madiApi.investigateCase(caseId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useCloseHaemovigilanceCase(caseId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: CloseCasePayload) => madiApi.closeHaemovigilanceCase(caseId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useNationalHaemovigilanceDashboard() {
  return useQuery({
    queryKey: madiQueryKeys.nationalHaemovigilance(),
    queryFn: () => madiApi.nationalHaemovigilanceDashboard(),
    staleTime: 30_000,
    refetchInterval: 60_000,
  });
}

export function useRecordDriveSyncConflict() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SyncConflictPayload) => madiApi.recordDriveSyncConflict(body),
    onSuccess: () => invalidateMadi(qc),
  });
}

export function useResolveDriveSyncConflict(conflictId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ResolveSyncConflictPayload) => madiApi.resolveDriveSyncConflict(conflictId, body),
    onSuccess: () => invalidateMadi(qc),
  });
}
