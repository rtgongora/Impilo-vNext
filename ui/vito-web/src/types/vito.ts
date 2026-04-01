export type ClientStatus = "ACTIVE" | "INACTIVE" | "DECEASED";

export type Gender = "MALE" | "FEMALE" | "OTHER" | "UNKNOWN";

export type CardStatus = "REQUESTED" | "PRINTING" | "PRINTED" | "ACTIVE" | "INACTIVE" | "REVOKED";

export type CardType = "PHYSICAL" | "VIRTUAL";

export type IssuanceStatus = "SUBMITTED" | "PROOFING" | "APPROVED" | "ISSUED" | "DELIVERED" | "REJECTED" | "EXPIRED";

export type ProofingMethod = "DOCUMENT_SCAN" | "BIOMETRIC" | "IN_PERSON" | "REMOTE_VIDEO" | "DHA_LOOKUP";

export type ProofingOutcome = "PASSED" | "FAILED" | "INCONCLUSIVE";

export type WalletStatus = "ACTIVE" | "FROZEN" | "CLOSED";

export type WalletJournalType = "TOPUP" | "PAYMENT" | "REVERSAL" | "OFFLINE_VOUCHER" | "OFFLINE_REDEEM";

export type MatchStatus = "PENDING" | "MERGED" | "SPLIT" | "DISMISSED" | "DEFERRED";

export type MatchResolution = "MERGE" | "SPLIT" | "DISMISS" | "DEFER";

export type MatchDisposition = "PENDING" | "AUTO_LINKED" | "MANUAL_LINKED" | "REJECTED" | "DEFERRED";

export type DedupStatus = "PENDING" | "MERGED" | "NOT_DUPLICATE" | "DEFERRED";

export type DedupAction = "MERGE" | "NOT_DUPLICATE";

export type DelegatedPickupStatus = "ACTIVE" | "REDEEMED" | "EXPIRED" | "CANCELLED";

export type RegistryMode = "NORMAL" | "PROVISIONAL" | "OFFLINE";

export type BiometricModality = "FINGERPRINT" | "IRIS" | "FACE";

export interface BiometricTemplate {
  id: number;
  healthId: string;
  modality: BiometricModality;
  position?: string;
  qualityScore?: number;
  captureDevice?: string;
  capturedBy?: string;
  status: "ACTIVE" | "SUPERSEDED" | "REVOKED";
  capturedAt: string;
}

export type BiometricTemplateFormat = "ISO_19794_2" | "ISO_19794_6" | "PROPRIETARY";

export type DeliveryChannel = "IN_PERSON" | "SMS" | "EMAIL" | "DELEGATED_PICKUP";

export type InactivationReason = "LOST" | "STOLEN" | "DAMAGED" | "OTHER";

export type RegistrationSource = "FACILITY" | "PORTAL" | "FIELD" | "MIGRATION";

export type VerifyMethod = "BIOMETRIC" | "OTP" | "DOCUMENT" | "PIN";

export type RecoveryChannel = "SMS" | "EMAIL";

export type PrintPriority = "NORMAL" | "URGENT";

export type PrintJobStatus = "QUEUED" | "PRINTING" | "COMPLETED" | "FAILED";

export type HandoverChannel = "CALL_CENTRE" | "FACILITY" | "FIELD_WORKER";

export interface Address {
  line1?: string;
  line2?: string;
  city?: string;
  province?: string;
  postalCode?: string;
  country?: string;
}

export interface Client {
  cpid?: string;
  healthId?: string;
  givenName?: string;
  familyName?: string;
  dateOfBirth?: string;
  gender?: Gender;
  nationalId?: string;
  phoneNumber?: string;
  email?: string;
  address?: Address;
  status?: ClientStatus;
  registeredAt?: string;
  lastVerifiedAt?: string;
  tenantId?: string;
}

export interface ProofingEvent {
  eventId?: string;
  method?: ProofingMethod;
  outcome?: ProofingOutcome;
  confidence?: number;
  performedAt?: string;
  performedBy?: string;
  notes?: string;
}

export interface IssuanceRequest {
  requestId?: string;
  healthId?: string;
  status?: IssuanceStatus;
  applicantName?: string;
  dateOfBirth?: string;
  facilityId?: string;
  type?: string;
  channel?: string;
  rejectionReason?: string;
  impiloIdIssued?: string;
  actorId?: string;
  submittedAt?: string;
  approvedAt?: string;
  issuedAt?: string;
  deliveredAt?: string;
  rejectedAt?: string;
}

export interface SmartCard {
  cardId?: string;
  healthId?: string;
  cardNumber?: string;
  status?: CardStatus;
  cardType?: CardType;
  requestedAt?: string;
  printedAt?: string;
  activatedAt?: string;
  inactivatedAt?: string;
  revokedAt?: string;
  inactivationReason?: string;
  revocationReason?: string;
}

export interface Wallet {
  walletId?: string;
  healthId?: string;
  balance?: number;
  currency?: string;
  status?: WalletStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface WalletJournal {
  journalId?: string;
  walletId?: string;
  type?: WalletJournalType;
  amount?: number;
  balanceAfter?: number;
  reference?: string;
  description?: string;
  createdAt?: string;
  actorId?: string;
}

export interface MatchResult {
  id?: number;
  matchId?: string; // Some frontend code might use this
  sourceHealthId?: string;
  candidateHealthId?: string;
  matchScore: number;
  matchAlgorithm?: string;
  matchFields?: string;
  disposition: MatchDisposition;
  resolvedBy?: string;
  resolvedAt?: string;
  createdAt?: string;
}

export interface MergeHistory {
  mergeId?: string;
  survivorCpid?: string;
  retiredCpid?: string;
  mergedFields?: string[];
  mergedAt?: string;
  mergedBy?: string;
  reversible?: boolean;
}

export interface DedupCase {
  caseId?: string;
  cpidA?: string;
  cpidB?: string;
  matchScore?: number;
  matchedFields?: string[];
  status?: DedupStatus;
  mergeHistory?: MergeHistory;
  createdAt?: string;
  resolvedAt?: string;
  resolvedBy?: string;
}

export interface DelegatedPickup {
  pickupId?: string;
  healthId?: string;
  delegateIdNumber?: string;
  delegateName?: string;
  delegatePhone?: string;
  pin?: string;
  status?: DelegatedPickupStatus;
  createdAt?: string;
  expiresAt?: string;
  redeemedAt?: string;
}

export interface BiometricMetadata {
  healthId?: string;
  modalities?: BiometricModality[];
  enrolledAt?: string;
  quality?: number;
  templateFormat?: BiometricTemplateFormat;
}

export interface GenericResponse {
  success?: boolean;
  message?: string;
  timestamp?: string;
}

export interface ErrorResponse {
  error?: string;
  message?: string;
  details?: Array<{ field?: string; message?: string }>;
  correlationId?: string;
  timestamp?: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: {
    code: string;
    message: string;
    status: number;
  };
  correlationId: string;
  timestamp: string;
}

export interface IdentityResolveResponse {
  cpid?: string;
  healthId?: string;
  confidence?: number;
  matchedOn?: string[];
}

export interface IdentityRotateResponse {
  previousCpid?: string;
  newCpid?: string;
  healthId?: string;
  rotatedAt?: string;
}

export interface RecoveryVerifyResponse {
  healthId?: string;
  temporaryToken?: string;
}

export interface RegistryModeResponse {
  mode?: RegistryMode;
  since?: string;
  reason?: string;
}

export interface ProvisionalIdResponse {
  provisionalRef?: string;
  issuedAt?: string;
  expiresAt?: string;
}

export interface ProvisionalIdRecord {
  provisionalRef?: string;
  healthId?: string;
  reason?: string;
  givenName?: string;
  familyName?: string;
  dateOfBirth?: string;
  facilityId?: string;
  issuedAt?: string;
  expiresAt?: string;
}

export interface OpenCrMatchCandidate {
  cpid?: string;
  healthId?: string;
  score?: number;
  matchedOn?: string[];
}

export interface OpenCrMatchResponse {
  candidates?: OpenCrMatchCandidate[];
}

export interface PrintJobResponse {
  jobId?: string;
  status?: PrintJobStatus;
  createdAt?: string;
}

export interface QrResolveResponse {
  healthId?: string;
  cpid?: string;
  givenName?: string;
  familyName?: string;
  validUntil?: string;
}

export interface QrPublicKeyResponse {
  kty?: string;
  kid?: string;
  use?: string;
  alg?: string;
  n?: string;
  e?: string;
}

export interface OfflineVoucherResponse {
  voucherToken?: string;
  expiresAt?: string;
  amount?: number;
}

export interface SHSCreateResponse {
  shsToken?: string;
  expiresAt?: string;
}

export interface AuditEvent {
  id?: string;
  aggregateType?: string;
  aggregateId?: string;
  eventType?: string;
  payload?: Record<string, unknown>;
  publishedAt?: string;
  createdAt?: string;
  actorId?: string;
  correlationId?: string;
  tenantId?: string;
}

export interface PortalProfile {
  registered: boolean;
  client?: Client;
}

export interface IssuanceSubmitPayload {
  givenName: string;
  familyName: string;
  dateOfBirth: string;
  gender?: Gender;
  nationalId?: string;
  phoneNumber?: string;
  facilityId?: string;
  source?: RegistrationSource;
}

export interface IdentityRegisterPayload {
  givenName: string;
  familyName: string;
  dateOfBirth: string;
  gender?: Gender;
  nationalId?: string;
  phoneNumber?: string;
  email?: string;
  address?: Address;
  facilityId?: string;
  source?: RegistrationSource;
}

export interface IdentityResolvePayload {
  healthId?: string;
  nationalId?: string;
  phoneNumber?: string;
  givenName?: string;
  familyName?: string;
  dateOfBirth?: string;
}

export interface CardRequestPayload {
  healthId: string;
  cardType: CardType;
  deliveryFacilityId?: string;
}

export interface WalletTransactionPayload {
  healthId: string;
  currency: string;
  amount: number;
  transactionRef: string;
  description?: string;
}

export interface BiometricEnrollPayload {
  healthId: string;
  modality: BiometricModality;
  position?: string;
  templateDataBase64: string;
  qualityScore?: number;
  captureDevice?: string;
}

export interface VerifyClientPayload {
  method: VerifyMethod;
  biometricTemplate?: string;
  otp?: string;
  documentRef?: string;
}

export interface DelegatedPickupCreatePayload {
  delegateIdNumber: string;
  delegateName: string;
  delegatePhone: string;
  expiryHours?: number;
}

export interface DelegatedPickupRedeemPayload {
  pickupId: string;
  pin: string;
  delegateIdNumber: string;
}

export interface PortalIdRequestPayload {
  // Demographics (for NEW requests)
  givenName?: string;
  familyName?: string;
  dateOfBirth?: string;
  gender?: Gender;
  nationalId?: string;
  phoneNumber?: string;
  email?: string;
  address?: Address;

  // Existing ID request
  healthId?: string;
  type?: "NEW" | "REPLACEMENT";
  channel?: DeliveryChannel;
  notes?: string;
}

export interface PortalRequestIdResponse {
  status: string;
  message: string;
  referenceId?: string;
}

export interface PortalQrResponse {
  qr: string;
}

export interface RecoveryStartPayload {
  identifier: string;
  channel?: RecoveryChannel;
}

export interface RecoveryVerifyPayload {
  identifier: string;
  otp: string;
}
