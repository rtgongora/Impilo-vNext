"use client";

import { useState, useEffect, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import {
  Box,
  Paper,
  TextField,
  MenuItem,
  Button,
  Typography,
  Alert,
  AlertTitle,
  Grid,
} from "@mui/material";
import PrintIcon from "@mui/icons-material/Print";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { PageHeader } from "@/components/common/PageHeader";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { useSubmitPrintJob } from "@/hooks/queries/usePrint";
import type { PrintPriority } from "@/types/vito";

function PrintForm() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const cardIdParam = searchParams.get("cardId") || "";

  const [cardId, setCardId] = useState(cardIdParam);
  const [printerId, setPrinterId] = useState("PRINTER-001");
  const [templateId, setTemplateId] = useState("TEMPLATE-V1");
  const [priority, setPriority] = useState<PrintPriority>("NORMAL");
  const [success, setSuccess] = useState<boolean>(false);
  const [jobId, setJobId] = useState<string | null>(null);

  const submitPrintJob = useSubmitPrintJob();

  useEffect(() => {
    if (cardIdParam) setCardId(cardIdParam);
  }, [cardIdParam]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const response = await submitPrintJob.mutateAsync({
        cardId,
        printerId,
        templateId,
        priority,
      });
      setSuccess(true);
      setJobId(response.jobId || "N/A");
    } catch (e) {
      // Error handled by mutation
    }
  };

  if (success) {
    return (
      <Box sx={{ mt: 2 }}>
        <Alert severity="success" sx={{ mb: 3 }}>
          <AlertTitle>Print Job Submitted Successfully</AlertTitle>
          <Typography variant="body2" sx={{ mb: 1 }}>
            The print job has been enqueued and is being processed.
          </Typography>
          <Typography variant="body2" fontWeight="bold">
            Job ID: {jobId}
          </Typography>
        </Alert>
        <Box sx={{ display: "flex", gap: 2 }}>
          <Button
            variant="contained"
            onClick={() => {
              setSuccess(false);
              setJobId(null);
              setCardId("");
            }}
          >
            Submit Another Job
          </Button>
          <Button variant="outlined" onClick={() => router.push("/admin/cards")}>
            Go to Cards
          </Button>
        </Box>
      </Box>
    );
  }

  return (
    <Box component="form" onSubmit={handleSubmit}>
      <Paper sx={{ p: 4 }}>
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <Typography variant="h6" gutterBottom>
              Submit Card for Printing
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Ensure the card is in the correct status before submitting for printing.
            </Typography>
          </Grid>

          <Grid item xs={12}>
            <TextField
              label="Card ID"
              fullWidth
              value={cardId}
              onChange={(e) => setCardId(e.target.value)}
              required
              disabled={!!cardIdParam}
            />
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              select
              label="Target Printer"
              fullWidth
              value={printerId}
              onChange={(e) => setPrinterId(e.target.value)}
              required
            >
              <MenuItem value="PRINTER-001">Main Facility Printer (Alpha)</MenuItem>
              <MenuItem value="PRINTER-002">Regional Hub Printer (Beta)</MenuItem>
              <MenuItem value="PRINTER-003">Field Office Printer (Gamma)</MenuItem>
            </TextField>
          </Grid>

          <Grid item xs={12} md={6}>
            <TextField
              select
              label="Card Template"
              fullWidth
              value={templateId}
              onChange={(e) => setTemplateId(e.target.value)}
              required
            >
              <MenuItem value="TEMPLATE-V1">Standard ID (v1)</MenuItem>
              <MenuItem value="TEMPLATE-V2-SECURE">High-Security ID (v2)</MenuItem>
              <MenuItem value="TEMPLATE-PROVISIONAL">Provisional ID (temp)</MenuItem>
            </TextField>
          </Grid>

          <Grid item xs={12}>
            <TextField
              select
              label="Priority"
              fullWidth
              value={priority}
              onChange={(e) => setPriority(e.target.value as PrintPriority)}
            >
              <MenuItem value="NORMAL">Normal</MenuItem>
              <MenuItem value="URGENT">Urgent (Express Processing)</MenuItem>
            </TextField>
          </Grid>

          <Grid item xs={12}>
            <Box sx={{ mb: 2 }}>
              <ErrorAlert error={submitPrintJob.error} />
            </Box>
            <Box sx={{ display: "flex", gap: 2 }}>
              <Button
                type="submit"
                variant="contained"
                size="large"
                startIcon={<PrintIcon />}
                disabled={submitPrintJob.isPending}
              >
                Submit Print Job
              </Button>
              <Button
                variant="outlined"
                size="large"
                onClick={() => router.back()}
                disabled={submitPrintJob.isPending}
              >
                Cancel
              </Button>
            </Box>
          </Grid>
        </Grid>
      </Paper>
    </Box>
  );
}

export default function PrintPage() {
  return (
    <Box>
      <PageHeader
        title="Card Printing"
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "Cards", href: "/admin/cards" },
          { label: "Print" },
        ]}
        action={
          <Button
            startIcon={<ArrowBackIcon />}
            onClick={() => window.history.back()}
          >
            Back
          </Button>
        }
      />

      <Suspense fallback={<Typography>Loading...</Typography>}>
        <PrintForm />
      </Suspense>
    </Box>
  );
}
