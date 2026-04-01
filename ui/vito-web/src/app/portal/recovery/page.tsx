"use client";

import React, { useState } from "react";
import {
  Box,
  Button,
  Container,
  Paper,
  Typography,
  Stack,
  Stepper,
  Step,
  StepLabel,
  TextField,
  MenuItem,
  CircularProgress,
  Alert,
} from "@mui/material";
import { useStartRecovery, useVerifyRecovery } from "@/hooks/queries/usePortal";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import Link from "next/link";

const steps = ["Start Recovery", "Verify Identity"];

export default function IdentityRecoveryPage() {
  const [activeStep, setActiveStep] = useState(0);
  const [identifier, setIdentifier] = useState("");
  const [channel, setChannel] = useState<"SMS" | "EMAIL">("SMS");
  const [otp, setOtp] = useState("");
  const [recoveredHealthId, setRecoveredHealthId] = useState<string | null>(null);

  const startRecovery = useStartRecovery();
  const verifyRecovery = useVerifyRecovery();

  const handleStart = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await startRecovery.mutateAsync({ identifier, channel });
      setActiveStep(1);
    } catch (err) {
      // Error handled by startRecovery.error
    }
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const result = await verifyRecovery.mutateAsync({ identifier, otp });
      if (result.healthId) {
        setRecoveredHealthId(result.healthId);
        setActiveStep(2);
      }
    } catch (err) {
      // Error handled by verifyRecovery.error
    }
  };

  const handleBack = () => {
    setActiveStep((prev) => prev - 1);
  };

  if (activeStep === 2 && recoveredHealthId) {
    return (
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <PageHeader title="Identity Recovered" />
        <Paper variant="outlined" sx={{ p: 4, borderRadius: 2, textAlign: "center" }}>
          <Typography variant="h5" color="success.main" gutterBottom fontWeight="bold">
            Success!
          </Typography>
          <Typography variant="body1" sx={{ mb: 4 }}>
            Your identity has been successfully recovered.
          </Typography>
          <Box sx={{ p: 3, bgcolor: "grey.50", borderRadius: 2, mb: 4 }}>
            <Typography variant="overline" color="text.secondary">
              Your Health ID
            </Typography>
            <Typography variant="h4" fontWeight="bold" color="primary.main">
              {recoveredHealthId}
            </Typography>
          </Box>
          <Button component={Link} href="/portal" variant="contained" size="large" fullWidth>
            Return to Portal
          </Button>
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="sm" sx={{ py: 4 }}>
      <PageHeader title="Identity Recovery" />
      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Paper variant="outlined" sx={{ p: 4, borderRadius: 2 }}>
        {activeStep === 0 && (
          <form onSubmit={handleStart}>
            <Stack spacing={3}>
              <Typography variant="body2" color="text.secondary">
                Enter your National ID or Passport number to start the recovery process.
              </Typography>
              <TextField
                fullWidth
                label="Identifier (ID / Passport)"
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                required
                disabled={startRecovery.isPending}
              />
              <TextField
                fullWidth
                select
                label="Recovery Channel"
                value={channel}
                onChange={(e) => setChannel(e.target.value as "SMS" | "EMAIL")}
                required
                disabled={startRecovery.isPending}
              >
                <MenuItem value="SMS">SMS</MenuItem>
                <MenuItem value="EMAIL">Email</MenuItem>
              </TextField>

              {startRecovery.error && <ErrorAlert error={startRecovery.error} />}

              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={startRecovery.isPending}
                startIcon={startRecovery.isPending && <CircularProgress size={20} />}
              >
                {startRecovery.isPending ? "Sending..." : "Start Recovery"}
              </Button>
            </Stack>
          </form>
        )}

        {activeStep === 1 && (
          <form onSubmit={handleVerify}>
            <Stack spacing={3}>
              <Typography variant="body2" color="text.secondary">
                We&apos;ve sent a verification code to your registered {channel.toLowerCase()}. Please
                enter it below.
              </Typography>
              <TextField
                fullWidth
                label="Verification Code (OTP)"
                value={otp}
                onChange={(e) => setOtp(e.target.value)}
                required
                disabled={verifyRecovery.isPending}
                autoFocus
              />

              {verifyRecovery.error && <ErrorAlert error={verifyRecovery.error} />}

              <Stack direction="row" spacing={2}>
                <Button
                  variant="outlined"
                  size="large"
                  fullWidth
                  onClick={handleBack}
                  disabled={verifyRecovery.isPending}
                >
                  Back
                </Button>
                <Button
                  type="submit"
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={verifyRecovery.isPending}
                  startIcon={verifyRecovery.isPending && <CircularProgress size={20} />}
                >
                  {verifyRecovery.isPending ? "Verifying..." : "Verify Identity"}
                </Button>
              </Stack>
            </Stack>
          </form>
        )}
      </Paper>
    </Container>
  );
}
