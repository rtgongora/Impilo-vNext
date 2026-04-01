"use client";

import React, { useRef } from "react";
import {
  Container,
  Paper,
  Typography,
  Stack,
  Box,
  Button,
  Skeleton,
} from "@mui/material";
import Link from "next/link";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import DownloadIcon from "@mui/icons-material/Download";
import QRCode from "react-qr-code";
import { usePortalMe, useHealthIdQr } from "@/hooks/queries/usePortal";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";

export default function QrPage() {
  const { data: profile, isLoading: isProfileLoading, error: profileError } = usePortalMe();
  const { data: qrData, isLoading: isQrLoading, error: qrError } = useHealthIdQr();
  const qrRef = useRef<HTMLDivElement>(null);

  const downloadQr = () => {
    const svg = qrRef.current?.querySelector("svg");
    if (!svg) return;

    const canvas = document.createElement("canvas");
    const svgData = new XMLSerializer().serializeToString(svg);
    const img = new Image();
    const svgBlob = new Blob([svgData], { type: "image/svg+xml;charset=utf-8" });
    const url = URL.createObjectURL(svgBlob);

    img.onload = () => {
      canvas.width = img.width + 40; // Add padding
      canvas.height = img.height + 40;
      const ctx = canvas.getContext("2d");
      if (ctx) {
        ctx.fillStyle = "white";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 20, 20);
        const pngUrl = canvas.toDataURL("image/png");
        const downloadLink = document.createElement("a");
        downloadLink.href = pngUrl;
        downloadLink.download = `health-id-qr-${profile?.healthId || "qr"}.png`;
        document.body.appendChild(downloadLink);
        downloadLink.click();
        document.body.removeChild(downloadLink);
      }
      URL.revokeObjectURL(url);
    };
    img.src = url;
  };

  const isLoading = isProfileLoading || isQrLoading;
  const error = profileError || qrError;

  if (isLoading) {
    return (
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <PageHeader title="Health ID QR" />
        <Paper variant="outlined" sx={{ p: 4, borderRadius: 2, textAlign: "center" }}>
          <Stack spacing={3} alignItems="center">
            <Skeleton variant="rectangular" width={256} height={256} />
            <Skeleton variant="text" width="60%" />
            <Skeleton variant="rectangular" width="100%" height={48} />
          </Stack>
        </Paper>
      </Container>
    );
  }

  if (error) {
    return (
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <PageHeader title="Health ID QR" />
        <ErrorAlert error={error} />
      </Container>
    );
  }

  // Use signed QR if available, otherwise fallback to plain healthId
  const qrValue = qrData?.qr || profile?.healthId || "";

  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <Box sx={{ mb: 2 }}>
        <Button
          component={Link}
          href="/portal"
          startIcon={<ArrowBackIcon />}
          sx={{ color: "text.secondary" }}
        >
          Back
        </Button>
      </Box>
      <PageHeader title="Health ID QR" />

      <Paper
        variant="outlined"
        sx={{
          p: 4,
          borderRadius: 3,
          textAlign: "center",
          backgroundColor: "background.paper",
          boxShadow: "0 8px 32px rgba(0,0,0,0.05)",
        }}
      >
        <Stack spacing={4} alignItems="center">
          <Box
            ref={qrRef}
            sx={{
              p: 3,
              bgcolor: "white",
              borderRadius: 2,
              border: "1px solid",
              borderColor: "grey.200",
              display: "inline-block",
            }}
          >
            {qrValue ? (
              <QRCode
                value={qrValue}
                size={256}
                level="H"
                style={{ height: "auto", maxWidth: "100%", width: "100%" }}
              />
            ) : (
              <Typography color="error">QR data unavailable</Typography>
            )}
          </Box>

          <Box>
            <Typography variant="h6" fontWeight="bold" gutterBottom>
              Your Health ID
            </Typography>
            <Typography
              variant="h4"
              sx={{
                fontFamily: "monospace",
                color: "primary.main",
                fontWeight: "bold",
                letterSpacing: 1,
              }}
            >
              {profile?.healthId || "—"}
            </Typography>
          </Box>

          <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 300 }}>
            Show this QR code at any **Impilo-registered facility** to access your records.
          </Typography>

          <Button
            variant="contained"
            size="large"
            fullWidth
            startIcon={<DownloadIcon />}
            onClick={downloadQr}
            disabled={!qrValue}
            sx={{ py: 1.5, fontWeight: "bold", borderRadius: 2 }}
          >
            Download as PNG
          </Button>

          <Typography variant="caption" color="text.secondary">
            Generated on {new Date().toLocaleDateString()}
          </Typography>
        </Stack>
      </Paper>
    </Container>
  );
}
