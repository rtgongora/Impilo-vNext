"use client";

import React, { useState } from "react";
import {
  Box,
  Button,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
  Container,
} from "@mui/material";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import {
  useResolveQr,
  downloadEmergencyCapsule,
  downloadPickupSlip,
} from "@/hooks/queries/useQr";
import type { QrResolveResponse } from "@/types/vito";

export default function QrAdminPage() {
  // Section 1: QR Token Resolver
  const [token, setToken] = useState("");
  const resolveMutation = useResolveQr();
  const [resolvedData, setResolvedData] = useState<QrResolveResponse | null>(null);

  const handleResolve = async () => {
    try {
      const result = await resolveMutation.mutateAsync(token);
      setResolvedData(result);
    } catch (err) {
      setResolvedData(null);
    }
  };

  // Section 2: Emergency Capsule Slip
  const [healthId, setHealthId] = useState("");
  const [downloadingCapsule, setDownloadingCapsule] = useState(false);
  const [capsuleError, setCapsuleError] = useState<Error | null>(null);

  const handleDownloadCapsule = async () => {
    if (!healthId) return;
    setDownloadingCapsule(true);
    setCapsuleError(null);
    try {
      await downloadEmergencyCapsule(healthId);
    } catch (err: any) {
      setCapsuleError(err);
    } finally {
      setDownloadingCapsule(false);
    }
  };

  // Section 3: Pickup Slip
  const [pickupId, setPickupId] = useState("");
  const [downloadingPickup, setDownloadingPickup] = useState(false);
  const [pickupError, setPickupError] = useState<Error | null>(null);

  const handleDownloadPickup = async () => {
    if (!pickupId) return;
    setDownloadingPickup(true);
    setPickupError(null);
    try {
      await downloadPickupSlip(pickupId);
    } catch (err: any) {
      setPickupError(err);
    } finally {
      setDownloadingPickup(false);
    }
  };

  return (
    <Container maxWidth="lg">
      <PageHeader
        title="QR Resolver & Slip Downloads"
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "QR & Slips" },
        ]}
      />

      <Stack spacing={4} sx={{ mb: 4 }}>
        {/* Section 1: QR Token Resolver */}
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom fontWeight="medium">
            QR Token Resolver
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Resolve a signed QR token to retrieve the associated Health ID and identity details.
          </Typography>
          
          <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
            <TextField
              label="QR Token"
              variant="outlined"
              fullWidth
              size="small"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              placeholder="Paste QR token here..."
            />
            <Button
              variant="contained"
              onClick={handleResolve}
              disabled={!token || resolveMutation.isPending}
              sx={{ minWidth: 120 }}
            >
              {resolveMutation.isPending ? <CircularProgress size={24} color="inherit" /> : "Resolve"}
            </Button>
          </Stack>

          <ErrorAlert error={resolveMutation.error} />

          {resolvedData && (
            <Box sx={{ mt: 2, p: 2, bgcolor: "action.hover", borderRadius: 1, border: "1px solid", borderColor: "divider" }}>
              <Typography variant="subtitle2" color="primary" gutterBottom>
                Resolved Identity
              </Typography>
              <Stack spacing={1}>
                <Typography variant="body2">
                  <strong>Health ID:</strong> {resolvedData.healthId}
                </Typography>
                <Typography variant="body2">
                  <strong>CPID:</strong> {resolvedData.cpid}
                </Typography>
                <Typography variant="body2">
                  <strong>Name:</strong> {resolvedData.givenName} {resolvedData.familyName}
                </Typography>
                <Typography variant="body2">
                  <strong>Valid Until:</strong> {resolvedData.validUntil ? new Date(resolvedData.validUntil).toLocaleString() : "N/A"}
                </Typography>
              </Stack>
            </Box>
          )}
        </Paper>

        <Divider />

        {/* Section 2: Emergency Capsule Slip */}
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom fontWeight="medium">
            Emergency Capsule Slip
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Download a PDF containing essential identity and medical info for emergency use.
          </Typography>

          <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
            <TextField
              label="Health ID"
              variant="outlined"
              fullWidth
              size="small"
              value={healthId}
              onChange={(e) => setHealthId(e.target.value)}
              placeholder="HID-XXXX-XXXX"
            />
            <Button
              variant="contained"
              onClick={handleDownloadCapsule}
              disabled={!healthId || downloadingCapsule}
              sx={{ minWidth: 120 }}
            >
              {downloadingCapsule ? <CircularProgress size={24} color="inherit" /> : "Download"}
            </Button>
          </Stack>

          <ErrorAlert error={capsuleError} />
        </Paper>

        <Divider />

        {/* Section 3: Pickup Slip */}
        <Paper sx={{ p: 3 }}>
          <Typography variant="h6" gutterBottom fontWeight="medium">
            Pickup Slip
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Download a printable slip for a client to collect their smart card.
          </Typography>

          <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
            <TextField
              label="Pickup ID"
              variant="outlined"
              fullWidth
              size="small"
              value={pickupId}
              onChange={(e) => setPickupId(e.target.value)}
              placeholder="Enter Pickup or Card ID"
            />
            <Button
              variant="contained"
              onClick={handleDownloadPickup}
              disabled={!pickupId || downloadingPickup}
              sx={{ minWidth: 120 }}
            >
              {downloadingPickup ? <CircularProgress size={24} color="inherit" /> : "Download"}
            </Button>
          </Stack>

          <ErrorAlert error={pickupError} />
        </Paper>
      </Stack>
    </Container>
  );
}
