export const VITO_STORAGE_KEYS = {
  AUTH_TOKEN: "vito:auth_token",
  AUTH_USER: "vito:auth_user",
  TENANT_ID: "vito:tenant_id",
  STEP_UP_TOKEN: "vito:step_up_token",
} as const;

export const CLIENT_STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  DECEASED: "Deceased",
};

export const CARD_STATUS_LABEL: Record<string, string> = {
  REQUESTED: "Requested",
  PRINTING: "Printing",
  PRINTED: "Printed",
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  REVOKED: "Revoked",
};

export const ISSUANCE_STATE_LABEL: Record<string, string> = {
  SUBMITTED: "Submitted",
  PROOFED: "Proofed",
  APPROVED: "Approved",
  ISSUED: "Issued",
  DELIVERED: "Delivered",
  REJECTED: "Rejected",
};

export const MODALITY_TYPE_LABEL: Record<string, string> = {
  FINGERPRINT_LEFT_INDEX: "Left Index Finger",
  FINGERPRINT_RIGHT_INDEX: "Right Index Finger",
  FINGERPRINT_LEFT_THUMB: "Left Thumb",
  FINGERPRINT_RIGHT_THUMB: "Right Thumb",
  IRIS_LEFT: "Left Iris",
  IRIS_RIGHT: "Right Iris",
};

export const WALLET_STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  FROZEN: "Frozen",
  CLOSED: "Closed",
};

export const WALLET_JOURNAL_TYPE_LABEL: Record<string, string> = {
  TOPUP: "Top-up",
  PAYMENT: "Payment",
  REVERSAL: "Reversal",
  OFFLINE_VOUCHER: "Offline Voucher",
  OFFLINE_REDEEM: "Offline Redeem",
};

export const MATCH_STATUS_LABEL: Record<string, string> = {
  PENDING: "Pending",
  MERGED: "Merged",
  SPLIT: "Split",
  DISMISSED: "Dismissed",
};

export const DEDUP_STATUS_LABEL: Record<string, string> = {
  PENDING: "Pending",
  MERGED: "Merged",
  NOT_DUPLICATE: "Not a Duplicate",
};

export const DELEGATED_PICKUP_STATUS_LABEL: Record<string, string> = {
  ACTIVE: "Active",
  REDEEMED: "Redeemed",
  EXPIRED: "Expired",
  CANCELLED: "Cancelled",
};

export const REGISTRY_MODE_LABEL: Record<string, string> = {
  NORMAL: "Normal",
  PROVISIONAL: "Provisional",
  OFFLINE: "Offline",
};

export const PROOFING_METHOD_LABEL: Record<string, string> = {
  DOCUMENT_SCAN: "Document Scan",
  BIOMETRIC: "Biometric",
  IN_PERSON: "In-Person",
  REMOTE_VIDEO: "Remote Video",
  DHA_LOOKUP: "DHA Lookup",
};

export const PROOFING_OUTCOME_LABEL: Record<string, string> = {
  PASSED: "Passed",
  FAILED: "Failed",
  INCONCLUSIVE: "Inconclusive",
};

export const GENDER_LABEL: Record<string, string> = {
  MALE: "Male",
  FEMALE: "Female",
  OTHER: "Other",
  UNKNOWN: "Unknown",
};

export const DELIVERY_CHANNEL_LABEL: Record<string, string> = {
  IN_PERSON: "In Person",
  SMS: "SMS",
  EMAIL: "Email",
  DELEGATED_PICKUP: "Delegated Pickup",
};

export const INACTIVATION_REASON_LABEL: Record<string, string> = {
  LOST: "Lost",
  STOLEN: "Stolen",
  DAMAGED: "Damaged",
  OTHER: "Other",
};

export const TEMPLATE_FORMAT_LABEL: Record<string, string> = {
  ISO_19794_2: "ISO 19794-2",
  ISO_19794_6: "ISO 19794-6",
  PROPRIETARY: "Proprietary",
};

export const PRINT_JOB_STATUS_LABEL: Record<string, string> = {
  QUEUED: "Queued",
  PRINTING: "Printing",
  COMPLETED: "Completed",
  FAILED: "Failed",
};

export const CARD_TYPE_LABEL: Record<string, string> = {
  PHYSICAL: "Physical",
  VIRTUAL: "Virtual",
};

export const REGISTRATION_SOURCE_LABEL: Record<string, string> = {
  FACILITY: "Facility",
  PORTAL: "Portal",
  FIELD: "Field",
  MIGRATION: "Migration",
};

export const AUDIT_AGGREGATE_TYPE_LABEL: Record<string, string> = {
  CLIENT: "Client",
  ISSUANCE_REQUEST: "Issuance Request",
  SMART_CARD: "Smart Card",
  WALLET: "Wallet",
  BIOMETRIC: "Biometric",
  DEDUP_CASE: "Dedup Case",
  MATCH_RESULT: "Match Result",
  DELEGATED_PICKUP: "Delegated Pickup",
  REGISTRY: "Registry",
};
