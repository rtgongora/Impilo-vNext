"use client";

import { useState } from "react";
import {
  Box,
  Paper,
  Tabs,
  Tab,
  TextField,
  Button,
  Grid,
  Typography,
  MenuItem,
  Divider,
  Alert,
  Snackbar,
} from "@mui/material";
import { useRegisterIdentity, useResolveIdentity } from "@/hooks/queries/useIdentity";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import type { Gender, RegistrationSource, Client, IdentityResolveResponse } from "@/types/vito";

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
      id={`simple-tabpanel-${index}`}
      aria-labelledby={`simple-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

export default function RegisterPage() {
  const [tabValue, setTabValue] = useState(0);
  const [snackbar, setSnackbar] = useState<{
    open: boolean;
    message: string;
    severity: "success" | "error";
  }>({ open: false, message: "", severity: "success" });

  const registerMutation = useRegisterIdentity();
  const resolveMutation = useResolveIdentity();

  const [registeredClient, setRegisteredClient] = useState<Client | null>(null);
  const [resolvedIdentity, setResolvedIdentity] = useState<IdentityResolveResponse | null>(null);

  const handleTabChange = (_: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
    setRegisteredClient(null);
    setResolvedIdentity(null);
  };

  const handleRegisterSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload = {
      givenName: formData.get("givenName") as string,
      familyName: formData.get("familyName") as string,
      dateOfBirth: formData.get("dateOfBirth") as string,
      gender: formData.get("gender") as Gender,
      nationalId: formData.get("nationalId") as string || undefined,
      phoneNumber: formData.get("phoneNumber") as string || undefined,
      email: formData.get("email") as string || undefined,
      facilityId: "FAC-001", // Placeholder
      source: "FACILITY" as RegistrationSource,
    };

    registerMutation.mutate(payload, {
      onSuccess: (data) => {
        setRegisteredClient(data);
        setSnackbar({ open: true, message: "Client registered successfully", severity: "success" });
      },
      onError: (err: any) => {
        setSnackbar({ open: true, message: err?.message || "Registration failed", severity: "error" });
      },
    });
  };

  const handleResolveSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const payload = {
      givenName: formData.get("givenName") as string || undefined,
      familyName: formData.get("familyName") as string || undefined,
      dateOfBirth: formData.get("dateOfBirth") as string || undefined,
      nationalId: formData.get("nationalId") as string || undefined,
      phoneNumber: formData.get("phoneNumber") as string || undefined,
    };

    resolveMutation.mutate(payload, {
      onSuccess: (data) => {
        setResolvedIdentity(data);
        setSnackbar({ open: true, message: "Identity resolved", severity: "success" });
      },
      onError: (err: any) => {
        setSnackbar({ open: true, message: err?.message || "Resolution failed", severity: "error" });
      },
    });
  };

  return (
    <Box>
      <PageHeader
        title="Identity Registration"
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "Clients", href: "/admin/clients" },
          { label: "Register" },
        ]}
      />

      <Paper sx={{ width: "100%" }}>
        <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
          <Tabs value={tabValue} onChange={handleTabChange} aria-label="registration tabs">
            <Tab label="Register New" />
            <Tab label="Resolve Existing" />
          </Tabs>
        </Box>

        <CustomTabPanel value={tabValue} index={0}>
          {registeredClient ? (
            <Alert severity="success" sx={{ mb: 3 }}>
              <Typography variant="subtitle1" fontWeight="bold">Registration Successful!</Typography>
              <Typography>Assigned Health ID: {registeredClient.healthId}</Typography>
              <Typography>DID: {(registeredClient as any).did}</Typography>
              <Typography>Status: {registeredClient.status}</Typography>
              <Button 
                variant="outlined" 
                size="small" 
                sx={{ mt: 2 }}
                onClick={() => setRegisteredClient(null)}
              >
                Register Another
              </Button>
            </Alert>
          ) : (
            <Box component="form" onSubmit={handleRegisterSubmit}>
              <Typography variant="h6" gutterBottom>Client Demographics</Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TextField required fullWidth label="Given Name" name="givenName" />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField required fullWidth label="Family Name" name="familyName" />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField required fullWidth type="date" label="Date of Birth" name="dateOfBirth" InputLabelProps={{ shrink: true }} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField required fullWidth select label="Gender" name="gender" defaultValue="UNKNOWN">
                    <MenuItem value="MALE">Male</MenuItem>
                    <MenuItem value="FEMALE">Female</MenuItem>
                    <MenuItem value="OTHER">Other</MenuItem>
                    <MenuItem value="UNKNOWN">Unknown</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth label="National ID" name="nationalId" />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TextField fullWidth label="Phone Number" name="phoneNumber" />
                </Grid>
                <Grid item xs={12}>
                  <TextField fullWidth label="Email" name="email" type="email" />
                </Grid>
                <Grid item xs={12}>
                  <Button
                    type="submit"
                    variant="contained"
                    size="large"
                    disabled={registerMutation.isPending}
                  >
                    {registerMutation.isPending ? "Registering..." : "Register Client"}
                  </Button>
                </Grid>
              </Grid>
              <Box sx={{ mt: 2 }}>
                <ErrorAlert error={registerMutation.error} />
              </Box>
            </Box>
          )}
        </CustomTabPanel>

        <CustomTabPanel value={tabValue} index={1}>
          <Box component="form" onSubmit={handleResolveSubmit}>
            <Typography variant="h6" gutterBottom>Search Demographics</Typography>
            <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
              Provide any combination of fields to find an existing identity.
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} sm={6}>
                <TextField fullWidth label="Given Name" name="givenName" />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField fullWidth label="Family Name" name="familyName" />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField fullWidth type="date" label="Date of Birth" name="dateOfBirth" InputLabelProps={{ shrink: true }} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField fullWidth label="National ID" name="nationalId" />
              </Grid>
              <Grid item xs={12} sm={6}>
                <TextField fullWidth label="Phone Number" name="phoneNumber" />
              </Grid>
              <Grid item xs={12}>
                <Button
                  type="submit"
                  variant="contained"
                  disabled={resolveMutation.isPending}
                >
                  {resolveMutation.isPending ? "Searching..." : "Resolve Identity"}
                </Button>
              </Grid>
            </Grid>
          </Box>

          {resolvedIdentity && (
            <Box sx={{ mt: 4 }}>
              <Divider sx={{ mb: 3 }} />
              <Typography variant="h6" gutterBottom>Resolution Results</Typography>
              <Paper variant="outlined" sx={{ p: 2, bgcolor: "action.hover" }}>
                <Grid container spacing={2}>
                  <Grid item xs={12} sm={4}>
                    <Typography variant="caption" color="textSecondary">Health ID</Typography>
                    <Typography variant="body1" fontWeight="bold">{resolvedIdentity.healthId || "Not Found"}</Typography>
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <Typography variant="caption" color="textSecondary">Confidence Score</Typography>
                    <Typography variant="body1">{((resolvedIdentity.confidence || 0) * 100).toFixed(1)}%</Typography>
                  </Grid>
                  <Grid item xs={12} sm={4}>
                    <Typography variant="caption" color="textSecondary">Matched Fields</Typography>
                    <Typography variant="body1">{resolvedIdentity.matchedOn?.join(", ") || "None"}</Typography>
                  </Grid>
                </Grid>
              </Paper>
            </Box>
          )}
          <Box sx={{ mt: 2 }}>
            <ErrorAlert error={resolveMutation.error} />
          </Box>
        </CustomTabPanel>
      </Paper>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={6000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} sx={{ width: "100%" }}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
