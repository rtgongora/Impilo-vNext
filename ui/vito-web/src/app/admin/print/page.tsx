"use client";

import React, { useState, useEffect } from "react";
import {
  Box,
  Button,
  Paper,
  TextField,
  MenuItem,
  Typography,
  Alert,
  CircularProgress,
} from "@mui/material";
import { Print as PrintIcon } from "@mui/icons-material";
import { useSearchParams, useRouter } from "next/navigation";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { useSubmitPrintJob } from "@/hooks/queries/usePrint";
import type { PrintPriority, PrintJobResponse } from "@/types/vito";

export default function PrintPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const cardIdFromQuery = searchParams.get("cardId") || "";
  
  const [cardId, setCardId] = useState(cardIdFromQuery);
  const [templateId, setTemplateId] = useState("standard-v1");
  const [priority, setPriority] = useState<PrintPriority>("NORMAL");
  const [successResult, setSuccessResult] = useState<PrintJobResponse | null>(null);

  const submitPrintJobMutation = useSubmitPrintJob();

  useEffect(() => {
    if (cardIdFromQuery) {
      setCardId(cardIdFromQuery);
    }
  }, [cardIdFromQuery]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const result = await submitPrintJobMutation.mutateAsync({
        cardId,
        templateId,
        priority,
      });
      setSuccessResult(result);
    } catch (err) {
      // Error handled by the hook and ErrorAlert
    }
  };

  return (
    <Box sx={{ maxWidth: 800 }}>
      <PageHeader
        title="Submit Print Job"
        breadcrumbs={[
          { label: "Admin", href: "/admin/dashboard" },
          { label: "Cards", href: "/admin/cards" },
          { label: "Print" },
        ]}
      />

      {successResult ? (
        <Paper sx={{ p: 3, textAlign: "center" }}>
          <Alert severity="success" sx={{ mb: 3 }}>
            Print job submitted successfully!
          </Alert>
          <Typography variant="h6" gutterBottom>
            Job ID: {successResult.jobId}
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
            Status: {successResult.status}
          </Typography>
          <Box sx={{ display: "flex", justifyContent: "center", gap: 2 }}>
            <Button
              variant="contained"
              onClick={() => {
                setSuccessResult(null);
                setCardId("");
              }}
            >
              Print Another Card
            </Button>
            <Button
              variant="outlined"
              onClick={() => router.push("/admin/cards")}
            >
              Back to Cards
            </Button>
          </Box>
        </Paper>
      ) : (
        <Paper sx={{ p: 3 }}>
          <Typography variant="body1" sx={{ mb: 3 }}>
            Configure and submit a print job for a requested SMART card.
          </Typography>

          <ErrorAlert error={submitPrintJobMutation.error as any} />

          <form onSubmit={handleSubmit}>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
              <TextField
                label="Card ID"
                value={cardId}
                onChange={(e) => setCardId(e.target.value)}
                required
                fullWidth
                helperText="Enter the unique identifier of the card to print"
              />

              <TextField
                select
                label="Card Template"
                value={templateId}
                onChange={(e) => setTemplateId(e.target.value)}
                required
                fullWidth
              >
                <MenuItem value="standard-v1">Standard Health ID Card v1</MenuItem>
                <MenuItem value="premium-v1">Premium Security Card v1</MenuItem>
                <MenuItem value="emergency-v1">Emergency/Temporary Slip v1</MenuItem>
              </TextField>

              <TextField
                select
                label="Priority"
                value={priority}
                onChange={(e) => setPriority(e.target.value as PrintPriority)}
                required
                fullWidth
              >
                <MenuItem value="NORMAL">Normal</MenuItem>
                <MenuItem value="URGENT">Urgent</MenuItem>
              </TextField>

              <Box sx={{ mt: 2 }}>
                <Button
                  type="submit"
                  variant="contained"
                  startIcon={submitPrintJobMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <PrintIcon />}
                  disabled={submitPrintJobMutation.isPending || !cardId}
                  fullWidth
                  size="large"
                >
                  {submitPrintJobMutation.isPending ? "Submitting..." : "Submit to Print Queue"}
                </Button>
              </Box>
            </Box>
          </form>
        </Paper>
      )}
    </Box>
  );
}
