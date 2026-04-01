"use client";

import React, { useState } from "react";
import {
  Box,
  Button,
  Container,
  Paper,
  Typography,
  Stack,
  Tabs,
  Tab,
  TextField,
  CircularProgress,
  Alert,
  Divider,
} from "@mui/material";
import { useCreatePickup, useRedeemPickup } from "@/hooks/queries/usePortal";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { VitoApiError } from "@/lib/apiClient";

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function CustomTabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`pickup-tabpanel-${index}`}
      aria-labelledby={`pickup-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

export default function DelegatedPickupPage() {
  const [tabValue, setTabValue] = useState(0);

  // Create Form State
  const [delegateName, setDelegateName] = useState("");
  const [delegateIdNumber, setDelegateIdNumber] = useState("");
  const [delegatePhone, setDelegatePhone] = useState("");
  const [expiryHours, setExpiryHours] = useState<number>(24);
  const [createdPickup, setCreatedPickup] = useState<any>(null);

  // Redeem Form State
  const [pickupId, setPickupId] = useState("");
  const [pin, setPin] = useState("");
  const [redeemDelegateId, setRedeemDelegateId] = useState("");
  const [redeemSuccess, setRedeemSuccess] = useState(false);
  const [isStepUpRequired, setIsStepUpRequired] = useState(false);

  const createPickup = useCreatePickup();
  const redeemPickup = useRedeemPickup();

  const handleTabChange = (_event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
    setRedeemSuccess(false);
    setCreatedPickup(null);
    setIsStepUpRequired(false);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const result = await createPickup.mutateAsync({
        delegateName,
        delegateIdNumber,
        delegatePhone,
        expiryHours,
      });
      setCreatedPickup(result);
    } catch (err) {
      // Handled by createPickup.error
    }
  };

  const handleRedeem = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsStepUpRequired(false);
    try {
      await redeemPickup.mutateAsync({
        pickupId,
        pin,
        delegateIdNumber: redeemDelegateId,
      });
      setRedeemSuccess(true);
    } catch (err) {
      if (err instanceof VitoApiError && err.status === 401) {
        // The apiClient emits the step-up event automatically if decision is STEP_UP_REQUIRED
        // We just need to show the UI state
        setIsStepUpRequired(true);
      }
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <PageHeader title="Delegated Pickup" />

      <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden" }}>
        <Box sx={{ borderBottom: 1, borderColor: "divider", bgcolor: "grey.50" }}>
          <Tabs value={tabValue} onChange={handleTabChange} variant="fullWidth">
            <Tab label="Create Pickup Token" id="pickup-tab-0" />
            <Tab label="Redeem Token" id="pickup-tab-1" />
          </Tabs>
        </Box>

        <Box sx={{ p: 4 }}>
          <CustomTabPanel value={tabValue} index={0}>
            {!createdPickup ? (
              <form onSubmit={handleCreate}>
                <Stack spacing={3}>
                  <Typography variant="body2" color="text.secondary">
                    Authorise someone else to collect your digital health card.
                  </Typography>
                  <TextField
                    fullWidth
                    label="Delegate Full Name"
                    value={delegateName}
                    onChange={(e) => setDelegateName(e.target.value)}
                    required
                    disabled={createPickup.isPending}
                  />
                  <TextField
                    fullWidth
                    label="Delegate ID Number"
                    value={delegateIdNumber}
                    onChange={(e) => setDelegateIdNumber(e.target.value)}
                    required
                    disabled={createPickup.isPending}
                  />
                  <TextField
                    fullWidth
                    label="Delegate Phone Number"
                    value={delegatePhone}
                    onChange={(e) => setDelegatePhone(e.target.value)}
                    required
                    disabled={createPickup.isPending}
                  />
                  <TextField
                    fullWidth
                    type="number"
                    label="Expiry (Hours)"
                    value={expiryHours}
                    onChange={(e) => setExpiryHours(parseInt(e.target.value) || 0)}
                    required
                    disabled={createPickup.isPending}
                  />

                  {createPickup.error && <ErrorAlert error={createPickup.error} />}

                  <Button
                    type="submit"
                    variant="contained"
                    size="large"
                    disabled={createPickup.isPending}
                    startIcon={createPickup.isPending && <CircularProgress size={20} />}
                  >
                    {createPickup.isPending ? "Creating..." : "Generate Pickup Token"}
                  </Button>
                </Stack>
              </form>
            ) : (
              <Stack spacing={3} sx={{ textAlign: "center" }}>
                <Alert severity="success">Pickup authorisation created successfully!</Alert>
                <Box sx={{ p: 4, bgcolor: "primary.50", borderRadius: 2, border: "2px dashed", borderColor: "primary.main" }}>
                  <Typography variant="overline" color="text.secondary">
                    Your Pickup Token
                  </Typography>
                  <Typography variant="h3" fontWeight="bold" color="primary.main" sx={{ letterSpacing: 4, my: 1 }}>
                    {createdPickup.pickupId?.substring(0, 8).toUpperCase() || "TOKEN"}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Share this code and the PIN with {delegateName}
                  </Typography>
                </Box>
                <Divider />
                <Box sx={{ textAlign: "left" }}>
                  <Typography variant="subtitle2" gutterBottom>
                    Details for Delegate:
                  </Typography>
                  <Typography variant="body2"><strong>Name:</strong> {createdPickup.delegateName}</Typography>
                  <Typography variant="body2"><strong>ID Number:</strong> {createdPickup.delegateIdNumber}</Typography>
                  <Typography variant="body2"><strong>PIN:</strong> {createdPickup.pin || "****"}</Typography>
                  <Typography variant="body2"><strong>Expires:</strong> {new Date(createdPickup.expiresAt).toLocaleString()}</Typography>
                </Box>
                <Button variant="outlined" onClick={() => setCreatedPickup(null)}>
                  Create Another
                </Button>
              </Stack>
            )}
          </CustomTabPanel>

          <CustomTabPanel value={tabValue} index={1}>
            {redeemSuccess ? (
              <Stack spacing={3} sx={{ textAlign: "center" }}>
                <Alert severity="success" sx={{ py: 2 }}>
                  <Typography variant="h6">Pickup Successfully Redeemed!</Typography>
                  <Typography variant="body2">The health card has been handed over.</Typography>
                </Alert>
                <Button variant="contained" component={Link} href="/portal">
                  Back to Portal
                </Button>
              </Stack>
            ) : (
              <form onSubmit={handleRedeem}>
                <Stack spacing={3}>
                  <Typography variant="body2" color="text.secondary">
                    Redeem a pickup token to collect a health card.
                  </Typography>
                  <TextField
                    fullWidth
                    label="Pickup Token / ID"
                    value={pickupId}
                    onChange={(e) => setPickupId(e.target.value)}
                    required
                    disabled={redeemPickup.isPending}
                  />
                  <TextField
                    fullWidth
                    label="PIN"
                    type="password"
                    value={pin}
                    onChange={(e) => setPin(e.target.value)}
                    required
                    disabled={redeemPickup.isPending}
                  />
                  <TextField
                    fullWidth
                    label="Your ID Number (Delegate)"
                    value={redeemDelegateId}
                    onChange={(e) => setRedeemDelegateId(e.target.value)}
                    required
                    disabled={redeemPickup.isPending}
                  />

                  {isStepUpRequired && (
                    <Alert severity="warning">
                      Additional verification required. Please check your device for a step-up prompt.
                    </Alert>
                  )}

                  {redeemPickup.error && !isStepUpRequired && (
                    <ErrorAlert error={redeemPickup.error} />
                  )}

                  <Button
                    type="submit"
                    variant="contained"
                    size="large"
                    disabled={redeemPickup.isPending}
                    startIcon={redeemPickup.isPending && <CircularProgress size={20} />}
                  >
                    {redeemPickup.isPending ? "Redeeming..." : "Redeem Token"}
                  </Button>
                </Stack>
              </form>
            )}
          </CustomTabPanel>
        </Box>
      </Paper>
    </Container>
  );
}
