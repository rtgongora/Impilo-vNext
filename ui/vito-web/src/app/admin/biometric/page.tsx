"use client";

import { useState } from "react";
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  InputAdornment,
  Stack,
  Alert,
  MenuItem,
  CircularProgress,
  Grid,
  Chip,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import SecurityIcon from "@mui/icons-material/Security";
import { PageHeader } from "@/components/common/PageHeader";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { StatusChip } from "@/components/common/StatusChip";
import { useBiometricTemplates, useEnrollBiometric } from "@/hooks/queries/useBiometric";
import type { BiometricModality, BiometricEnrollPayload } from "@/types/vito";

export default function BiometricPage() {
  const [searchHealthId, setSearchHealthId] = useState("");
  const [activeHealthId, setActiveHealthId] = useState("");
  const [enrollModalOpen, setEnrollModalOpen] = useState(false);

  // Form state
  const [modality, setModality] = useState<BiometricModality>("FINGERPRINT");
  const [position, setPosition] = useState("");
  const [captureDevice, setCaptureDevice] = useState("");
  const [quality, setQuality] = useState<string>("85");
  const [templateData, setTemplateData] = useState("");

  const {
    data: templates,
    isLoading: templatesLoading,
    error: templatesError,
  } = useBiometricTemplates(activeHealthId);

  const enrollBiometric = useEnrollBiometric();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setActiveHealthId(searchHealthId);
  };

  const handleEnroll = async () => {
    try {
      const payload: BiometricEnrollPayload = {
        healthId: activeHealthId,
        modality,
        position,
        captureDevice,
        qualityScore: parseInt(quality, 10),
        templateDataBase64: templateData,
      };
      await enrollBiometric.mutateAsync(payload);
      setEnrollModalOpen(false);
      resetForm();
    } catch (e) {
      // handled by mutation
    }
  };

  const resetForm = () => {
    setModality("FINGERPRINT");
    setPosition("");
    setCaptureDevice("");
    setQuality("85");
    setTemplateData("");
  };

  const modalities: BiometricModality[] = ["FINGERPRINT", "IRIS", "FACE"];

  const getModalityColor = (m: BiometricModality) => {
    switch (m) {
      case "FINGERPRINT": return "primary";
      case "IRIS": return "secondary";
      case "FACE": return "info";
      default: return "default";
    }
  };

  return (
    <Box>
      <PageHeader
        title="Biometric Management"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Biometric" }]}
      />

      <Alert severity="warning" icon={<SecurityIcon />} sx={{ mb: 3 }}>
        <Typography variant="body2" sx={{ fontWeight: "bold" }}>Privacy Notice:</Typography>
        <Typography variant="body2">
          Biometric templates contain sensitive personal data. 
          Access is strictly logged and restricted to authorized personnel. 
          Templates are stored as encrypted blobs and are never exposed in plaintext over the API.
        </Typography>
      </Alert>

      <Paper sx={{ p: 2, mb: 3 }}>
        <form onSubmit={handleSearch}>
          <Box sx={{ display: "flex", gap: 2 }}>
            <TextField
              fullWidth
              label="Search Health ID"
              variant="outlined"
              size="small"
              value={searchHealthId}
              onChange={(e) => setSearchHealthId(e.target.value)}
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchIcon />
                  </InputAdornment>
                ),
              }}
              placeholder="Enter Health ID to view enrolled biometrics"
            />
            <Button type="submit" variant="contained">
              Search
            </Button>
          </Box>
        </form>
      </Paper>

      {activeHealthId && (
        <Paper sx={{ p: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
            <Typography variant="h6">Enrolled Templates</Typography>
            <Button
              variant="contained"
              startIcon={<FingerprintIcon />}
              onClick={() => setEnrollModalOpen(true)}
            >
              Enroll New
            </Button>
          </Stack>

          <ErrorAlert error={templatesError} />

          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Modality</TableCell>
                  <TableCell>Position</TableCell>
                  <TableCell align="right">Quality Score</TableCell>
                  <TableCell>Capture Device</TableCell>
                  <TableCell>Enrolled At</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {templatesLoading ? (
                  <LoadingSkeleton rows={3} columns={6} />
                ) : (
                  templates?.map((t) => (
                    <TableRow key={t.id}>
                      <TableCell>
                        <Chip 
                          label={t.modality} 
                          size="small" 
                          color={getModalityColor(t.modality)}
                          variant="outlined"
                          icon={<FingerprintIcon />}
                        />
                      </TableCell>
                      <TableCell>{t.position || "-"}</TableCell>
                      <TableCell align="right">
                        <Box sx={{ color: (t.qualityScore || 0) > 70 ? 'success.main' : 'warning.main', fontWeight: 'bold' }}>
                          {t.qualityScore}%
                        </Box>
                      </TableCell>
                      <TableCell>{t.captureDevice || "-"}</TableCell>
                      <TableCell>
                        {t.capturedAt ? new Date(t.capturedAt).toLocaleString() : "-"}
                      </TableCell>
                      <TableCell>
                        <StatusChip status={t.status!} type="client" />
                      </TableCell>
                    </TableRow>
                  ))
                )}
                {templates && templates.length === 0 && !templatesLoading && (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                      <Typography variant="body2" color="text.secondary">
                        No biometric templates enrolled for this client
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}

      {/* Enroll Modal */}
      <Dialog open={enrollModalOpen} onClose={() => !enrollBiometric.isPending && setEnrollModalOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Enroll Biometric Template</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Capturing biometric data for Health ID: <strong>{activeHealthId}</strong>
            </Typography>
            
            <Grid container spacing={2}>
              <Grid item xs={6}>
                <TextField
                  select
                  fullWidth
                  label="Modality"
                  value={modality}
                  onChange={(e) => setModality(e.target.value as BiometricModality)}
                >
                  {modalities.map((m) => (
                    <MenuItem key={m} value={m}>{m}</MenuItem>
                  ))}
                </TextField>
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  label="Position"
                  placeholder="e.g. LEFT_INDEX, RIGHT_THUMB"
                  value={position}
                  onChange={(e) => setPosition(e.target.value)}
                />
              </Grid>
            </Grid>

            <TextField
              fullWidth
              label="Capture Device"
              placeholder="e.g. Futronic FS80H, IriShield MK2120U"
              value={captureDevice}
              onChange={(e) => setCaptureDevice(e.target.value)}
            />

            <TextField
              fullWidth
              label="Quality Score (0-100)"
              type="number"
              value={quality}
              onChange={(e) => setQuality(e.target.value)}
            />

            <TextField
              fullWidth
              label="Base64 Template Data"
              multiline
              rows={6}
              placeholder="Paste base64 encoded template data here..."
              value={templateData}
              onChange={(e) => setTemplateData(e.target.value)}
              sx={{ fontFamily: 'monospace' }}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setEnrollModalOpen(false)} disabled={enrollBiometric.isPending}>
            Cancel
          </Button>
          <Button 
            onClick={handleEnroll} 
            variant="contained" 
            startIcon={enrollBiometric.isPending ? <CircularProgress size={20} color="inherit" /> : <FingerprintIcon />}
            disabled={enrollBiometric.isPending || !templateData}
          >
            Enroll Template
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
