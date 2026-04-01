"use client";

import React, { useState } from "react";
import {
  Container,
  Paper,
  Typography,
  Stack,
  Box,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormControlLabel,
  Switch,
  Button,
  Card,
  CardContent,
  Alert,
  Skeleton,
} from "@mui/material";
import Link from "next/link";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useRequestId, usePortalMe } from "@/hooks/queries/usePortal";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { DeliveryChannel } from "@/types/vito";

export default function RequestIdPage() {
  const { data: profile, isLoading: isProfileLoading, error: profileError } = usePortalMe();
  const { mutate: requestId, isPending, isSuccess, data: response, error: requestError } = useRequestId();

  const [channel, setChannel] = useState<DeliveryChannel>("SMS");
  const [isReplacement, setIsReplacement] = useState(false);
  const [notes, setNotes] = useState("");

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    requestId({
      healthId: profile?.healthId,
      type: isReplacement ? "REPLACEMENT" : "NEW",
      channel,
      notes: notes.trim() || undefined,
    });
  };

  if (isProfileLoading) {
    return (
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <PageHeader title="Request Health ID" />
        <Paper variant="outlined" sx={{ p: 4, borderRadius: 2 }}>
          <Stack spacing={3}>
            <Skeleton variant="rectangular" height={56} />
            <Skeleton variant="rectangular" height={56} />
            <Skeleton variant="rectangular" height={56} />
            <Skeleton variant="rectangular" height={42} width={120} />
          </Stack>
        </Paper>
      </Container>
    );
  }

  if (profileError) {
    return (
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <PageHeader title="Request Health ID" />
        <ErrorAlert error={profileError} />
      </Container>
    );
  }

  if (isSuccess && response) {
    return (
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <PageHeader title="Request Submitted" />
        <Card variant="outlined" sx={{ borderRadius: 2, textAlign: "center", p: 2 }}>
          <CardContent>
            <CheckCircleOutlineIcon color="success" sx={{ fontSize: 64, mb: 2 }} />
            <Typography variant="h5" gutterBottom fontWeight="bold">
              Your request has been submitted
            </Typography>
            <Typography color="text.secondary" paragraph>
              {response.message}
            </Typography>
            <Box sx={{ bgcolor: "grey.50", p: 2, borderRadius: 1, mb: 3, textAlign: "left" }}>
              <Typography variant="caption" color="text.secondary" display="block">
                Reference ID
              </Typography>
              <Typography variant="body1" sx={{ fontFamily: "monospace", fontWeight: "bold" }}>
                {response.referenceId || "N/A"}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                Status
              </Typography>
              <Typography variant="body1" sx={{ fontWeight: "bold" }}>
                {response.status}
              </Typography>
            </Box>
            <Button component={Link} href="/portal" variant="contained" fullWidth size="large">
              Back to Portal
            </Button>
          </CardContent>
        </Card>
      </Container>
    );
  }

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
      <PageHeader title="Request Health ID" />

      <Paper variant="outlined" sx={{ p: 4, borderRadius: 2 }}>
        <form onSubmit={handleSubmit}>
          <Stack spacing={4}>
            <Typography variant="body1" color="text.secondary">
              Request a physical smart card or a new digital Health ID.
            </Typography>

            <FormControl fullWidth>
              <InputLabel id="channel-label">Preferred Contact Channel</InputLabel>
              <Select
                labelId="channel-label"
                value={channel}
                label="Preferred Contact Channel"
                onChange={(e) => setChannel(e.target.value as DeliveryChannel)}
                required
              >
                <MenuItem value="SMS">SMS</MenuItem>
                <MenuItem value="EMAIL">Email</MenuItem>
                <MenuItem value="IN_PERSON">In Person</MenuItem>
              </Select>
            </FormControl>

            <FormControlLabel
              control={
                <Switch
                  checked={isReplacement}
                  onChange={(e) => setIsReplacement(e.target.checked)}
                />
              }
              label="This is a replacement request"
            />

            {(isReplacement || true) && (
              <TextField
                label={isReplacement ? "Reason for replacement" : "Notes (optional)"}
                multiline
                rows={3}
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder={isReplacement ? "e.g. Lost, Stolen, Damaged" : "Any additional information"}
                fullWidth
              />
            )}

            {requestError && <ErrorAlert error={requestError} />}

            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              disabled={isPending}
              sx={{ py: 1.5, fontWeight: "bold" }}
            >
              {isPending ? "Submitting..." : "Submit Request"}
            </Button>
          </Stack>
        </form>
      </Paper>
    </Container>
  );
}
