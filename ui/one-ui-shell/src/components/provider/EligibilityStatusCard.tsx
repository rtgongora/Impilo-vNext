"use client";

import React from "react";
import {
  BadgeCheck,
  BadgeX,
  AlertCircle,
  Shield,
  Clock,
  Building,
  Users,
  Award,
  AlertTriangle,
} from "lucide-react";

interface EligibilityStatusCardProps {
  eligible: boolean;
  hasValidLicense: boolean;
  hasActivePractisingCertificate: boolean;
  hasNoOverdueCompliance: boolean;
  hasActiveAffiliation: boolean;
  canServeAsPic: boolean;
  licenseExpiryDate?: string;
  certificateExpiryDate?: string;
  blockReasons?: string[];
}

const statusConfig = {
  eligible: { icon: BadgeCheck, color: "text-primary", bgColor: "bg-success-soft", label: "Eligible" },
  ineligible: { icon: BadgeX, color: "text-red-600", bgColor: "bg-danger-soft", label: "Ineligible" },
  partial: { icon: AlertCircle, color: "text-amber-600", bgColor: "bg-warning-soft", label: "Partial" },
};

const criteriaConfig = [
  { key: "hasValidLicense", icon: Shield, label: "Valid License" },
  { key: "hasActivePractisingCertificate", icon: Award, label: "Practising Certificate" },
  { key: "hasNoOverdueCompliance", icon: AlertTriangle, label: "Compliance OK" },
  { key: "hasActiveAffiliation", icon: Building, label: "Active Affiliation" },
  { key: "canServeAsPic", icon: Users, label: "PIC Eligible" },
];

export function EligibilityStatusCard({
  eligible,
  hasValidLicense,
  hasActivePractisingCertificate,
  hasNoOverdueCompliance,
  hasActiveAffiliation,
  canServeAsPic,
  licenseExpiryDate,
  certificateExpiryDate,
  blockReasons = [],
}: EligibilityStatusCardProps) {
  const config = eligible ? statusConfig.eligible : statusConfig.ineligible;
  const Icon = config.icon;

  const statusValues = {
    hasValidLicense,
    hasActivePractisingCertificate,
    hasNoOverdueCompliance,
    hasActiveAffiliation,
    canServeAsPic,
  };

  const passedCount = Object.values(statusValues).filter(Boolean).length;
  const totalCount = Object.keys(statusValues).length;

  return (
    <div className={`rounded-lg border p-4 ${config.bgColor}`}>
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Icon className={`h-5 w-5 ${config.color}`} />
          <span className={`font-semibold ${config.color}`}>{config.label}</span>
        </div>
        <span className="text-sm text-muted-foreground">
          {passedCount}/{totalCount} criteria met
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {criteriaConfig.map(({ key, icon: IconComponent, label }) => {
          const passed = statusValues[key as keyof typeof statusValues];
          return (
            <div
              key={key}
              className={`flex items-center gap-2 rounded-md px-3 py-2 ${
                passed ? "bg-card/80" : "bg-card/40"
              }`}
            >
              <IconComponent
                className={`h-4 w-4 ${passed ? "text-primary" : "text-muted-foreground"}`}
              />
              <span className={`text-sm ${passed ? "text-foreground" : "text-muted-foreground"}`}>
                {label}
              </span>
              {passed ? (
                <BadgeCheck className="ml-auto h-3.5 w-3.5 text-primary" />
              ) : (
                <Clock className="ml-auto h-3.5 w-3.5 text-muted-foreground" />
              )}
            </div>
          );
        })}
      </div>

      {(licenseExpiryDate || certificateExpiryDate) && (
        <div className="mt-4 flex gap-4 border-t pt-3">
          {licenseExpiryDate && (
            <div className="text-xs">
              <span className="text-muted-foreground">License expires: </span>
              <span className="font-medium">{new Date(licenseExpiryDate).toLocaleDateString()}</span>
            </div>
          )}
          {certificateExpiryDate && (
            <div className="text-xs">
              <span className="text-muted-foreground">Certificate expires: </span>
              <span className="font-medium">{new Date(certificateExpiryDate).toLocaleDateString()}</span>
            </div>
          )}
        </div>
      )}

      {blockReasons.length > 0 && (
        <div className="mt-4 border-t pt-3">
          <p className="text-xs font-medium text-red-600 mb-2">Block Reasons:</p>
          <ul className="space-y-1">
            {blockReasons.map((reason) => (
              <li key={reason} className="flex items-center gap-2 text-xs text-red-600">
                <AlertCircle className="h-3 w-3" />
                {reason.replace(/_/g, " ")}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}