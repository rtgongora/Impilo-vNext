"use client";

import React from "react";
import {
  Box,
  Button,
  Container,
  Paper,
  Typography,
  Stack,
  Divider,
  Skeleton,
} from "@mui/material";
import PersonIcon from "@mui/icons-material/Person";
import Link from "next/link";
import { useAuthStore } from "@/hooks/useAuthStore";
import { usePortalMe } from "@/hooks/queries/usePortal";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { StatusChip } from "@/components/common/StatusChip";
import { PageHeader } from "@/components/common/PageHeader";

export default function PortalProfilePage() {
  const { user } = useAuthStore();
  const { data: profile, isLoading, error } = usePortalMe();

  const greeting = user?.displayName ? `Welcome, ${user.displayName}` : "Citizen Profile";

  if (isLoading) {
    return (
      <Container maxWidth="md" sx={{ py: 4 }}>
        <PageHeader title={greeting} />
        <Paper variant="outlined" sx={{ p: 4, borderRadius: 2 }}>
          <Stack spacing={3}>
            <Skeleton variant="text" width="30%" height={30} />
            <Skeleton variant="rectangular" width="60%" height={60} />
            <Divider />
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                gap: 3,
              }}
            >
              <Box>
                <Skeleton variant="text" width="40%" />
                <Skeleton variant="text" width="80%" height={30} />
              </Box>
              <Box>
                <Skeleton variant="text" width="40%" />
                <Skeleton variant="text" width="80%" height={30} />
              </Box>
              <Box>
                <Skeleton variant="text" width="40%" />
                <Skeleton variant="text" width="80%" height={30} />
              </Box>
              <Box>
                <Skeleton variant="text" width="40%" />
                <Skeleton variant="rectangular" width="100px" height={32} />
              </Box>
            </Box>
            <Stack direction="row" spacing={2} sx={{ pt: 2 }}>
              <Skeleton variant="rectangular" width={140} height={42} sx={{ borderRadius: 1 }} />
              <Skeleton variant="rectangular" width={160} height={42} sx={{ borderRadius: 1 }} />
            </Stack>
          </Stack>
        </Paper>
      </Container>
    );
  }

  if (error) {
    return (
      <Container maxWidth="md" sx={{ py: 4 }}>
        <PageHeader title={greeting} />
        <ErrorAlert error={error} />
      </Container>
    );
  }

  const isRegistered = !!profile?.healthId;

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <PageHeader title={greeting} />

      {isRegistered && profile ? (
        <Paper
          variant="outlined"
          sx={{
            p: 4,
            borderRadius: 2,
            position: "relative",
            overflow: "hidden",
            boxShadow: "0 4px 20px rgba(0,0,0,0.05)",
          }}
        >
          <Box
            sx={{
              position: "absolute",
              top: 0,
              left: 0,
              width: "100%",
              height: 6,
              bgcolor: "primary.main",
            }}
          />
          <Stack spacing={4}>
            <Box>
              <Typography
                variant="overline"
                color="text.secondary"
                sx={{ letterSpacing: 2, fontWeight: "bold" }}
              >
                Your Digital Health ID
              </Typography>
              <Typography
                variant="h2"
                sx={{
                  fontWeight: 800,
                  color: "primary.main",
                  letterSpacing: -1,
                  fontFamily: "monospace",
                  mt: -1,
                }}
              >
                {profile.healthId}
              </Typography>
            </Box>

            <Divider />

            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
                gap: 4,
              }}
            >
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight="bold"
                  sx={{ textTransform: "uppercase", letterSpacing: 1 }}
                >
                  Full Name
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {profile.givenName} {profile.familyName}
                </Typography>
              </Box>
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight="bold"
                  sx={{ textTransform: "uppercase", letterSpacing: 1 }}
                >
                  Date of Birth
                </Typography>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  {profile.dateOfBirth
                    ? new Date(profile.dateOfBirth).toLocaleDateString(undefined, {
                        dateStyle: "long",
                      })
                    : "—"}
                </Typography>
              </Box>
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight="bold"
                  sx={{ textTransform: "uppercase", letterSpacing: 1 }}
                >
                  National ID / Passport
                </Typography>
                <Typography variant="body1" sx={{ fontWeight: 500 }}>
                  {profile.nationalId || "Not provided"}
                </Typography>
              </Box>
              <Box>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  fontWeight="bold"
                  sx={{ textTransform: "uppercase", letterSpacing: 1 }}
                >
                  Identity Status
                </Typography>
                <Box sx={{ mt: 0.5 }}>
                  <StatusChip status={profile.status || "ACTIVE"} type="client" />
                </Box>
              </Box>
            </Box>

            <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ pt: 2 }}>
              <Button
                component={Link}
                href="/portal/qr"
                variant="contained"
                size="large"
                disableElevation
                sx={{ borderRadius: 2, px: 4, py: 1.2, fontWeight: "bold" }}
              >
                View My QR
              </Button>
              <Button
                component={Link}
                href="/portal/request-id"
                variant="outlined"
                size="large"
                sx={{ borderRadius: 2, px: 4, py: 1.2, fontWeight: "bold" }}
              >
                Request ID Card
              </Button>
            </Stack>
          </Stack>
        </Paper>
      ) : (
        <Paper
          variant="outlined"
          sx={{
            p: { xs: 4, md: 8 },
            textAlign: "center",
            borderRadius: 3,
            backgroundColor: "grey.50",
            borderStyle: "dashed",
            borderWidth: 2,
          }}
        >
          <Box
            sx={{
              display: "flex",
              justifyContent: "center",
              mb: 4,
              color: "primary.light",
              opacity: 0.6,
            }}
          >
            <PersonIcon sx={{ fontSize: 120 }} />
          </Box>
          <Typography variant="h4" fontWeight="800" gutterBottom sx={{ letterSpacing: -0.5 }}>
            You haven&apos;t registered for a Health ID yet
          </Typography>
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ mb: 5, maxWidth: 520, mx: "auto", fontSize: "1.1rem", lineHeight: 1.6 }}
          >
            A Health ID is your secure, digital identity that connects you to healthcare services
            across the country. It helps doctors access your records safely and quickly.
          </Typography>
          <Button
            component={Link}
            href="/portal/request-id"
            variant="contained"
            size="large"
            disableElevation
            sx={{ borderRadius: 2, px: 6, py: 2, fontSize: "1.1rem", fontWeight: "bold" }}
          >
            Get Started
          </Button>
        </Paper>
      )}
    </Container>
  );
}
