"use client";

import React from "react";
import { Chip, ChipProps } from "@mui/material";
import {
  CLIENT_STATUS_LABEL,
  CARD_STATUS_LABEL,
  ISSUANCE_STATE_LABEL,
  WALLET_STATUS_LABEL,
  MATCH_STATUS_LABEL,
  DEDUP_STATUS_LABEL,
} from "@/lib/constants";

export type StatusType =
  | "client"
  | "card"
  | "issuance"
  | "wallet"
  | "match"
  | "dedup";

interface StatusChipProps {
  status: string;
  type: StatusType;
  size?: ChipProps["size"];
  variant?: ChipProps["variant"];
}

const getLabel = (status: string, type: StatusType): string => {
  switch (type) {
    case "client":
      return CLIENT_STATUS_LABEL[status] || status;
    case "card":
      return CARD_STATUS_LABEL[status] || status;
    case "issuance":
      return ISSUANCE_STATE_LABEL[status] || status;
    case "wallet":
      return WALLET_STATUS_LABEL[status] || status;
    case "match":
      return MATCH_STATUS_LABEL[status] || status;
    case "dedup":
      return DEDUP_STATUS_LABEL[status] || status;
    default:
      return status;
  }
};

const getColor = (status: string): ChipProps["color"] => {
  const s = status.toUpperCase();

  // Success states
  if (
    [
      "ACTIVE",
      "APPROVED",
      "ISSUED",
      "DELIVERED",
      "PASSED",
      "MERGED",
      "COMPLETED",
      "SUCCESS",
    ].includes(s)
  ) {
    return "success";
  }

  // Warning/Info states
  if (
    ["REQUESTED", "PRINTING", "SUBMITTED", "PENDING", "QUEUED", "PRINTED"].includes(
      s
    )
  ) {
    return "info";
  }

  // Error states
  if (
    [
      "INACTIVE",
      "FROZEN",
      "REJECTED",
      "FAILED",
      "REVOKED",
      "CANCELLED",
    ].includes(s)
  ) {
    return "error";
  }

  // Default/Neutral states
  if (
    [
      "DECEASED",
      "CLOSED",
      "DISMISSED",
      "EXPIRED",
      "STOLEN",
      "LOST",
      "DAMAGED",
      "NOT_DUPLICATE",
    ].includes(s)
  ) {
    return "default";
  }

  return "default";
};

export const StatusChip: React.FC<StatusChipProps> = ({
  status,
  type,
  size = "small",
  variant = "outlined",
}) => {
  if (!status) return null;

  return (
    <Chip
      label={getLabel(status, type)}
      color={getColor(status)}
      size={size}
      variant={variant}
    />
  );
};
