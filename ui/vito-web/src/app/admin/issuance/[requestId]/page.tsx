"use client";

import React from "react";
import {
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  Grid,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { ArrowBack as BackIcon } from "@mui/icons-material";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useIssuanceDetail } from "@/hooks/queries/useIssuance";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { PROOFING_METHOD_LABEL, PROOFING_OUTCOME_LABEL } from "@/lib/constants";

export default function IssuanceDetailPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const { data, isLoading, error } = useIssuanceDetail(requestId);

  if (isLoading) {
    return (
      <Box>
        <LoadingSkeleton rows={10} columns={1} />
      </Box>
    );
  }

  if (error || !data) {
    return <ErrorAlert error={error as any} />;
  }

  const detailItems = [
    { label: "Request ID", value: data.requestId, monospace: true },
    { label: "Health ID", value: data.healthId || "Not assigned yet", monospace: !!data.healthId },
    { label: "Applicant Name", value: data.applicantName },
    { label: "Date of Birth", value: data.dateOfBirth },
    { label: "Facility ID", value: data.facilityId },
    { label: "Submitted At", value: data.submittedAt ? new Date(data.submittedAt).toLocaleString() : "-" },
    { label: "Approved At", value: data.approvedAt ? new Date(data.approvedAt).toLocaleString() : "-" },
    { label: "Issued At", value: data.issuedAt ? new Date(data.issuedAt).toLocaleString() : "-" },
    { label: "Delivered At", value: data.deliveredAt ? new Date(data.deliveredAt).toLocaleString() : "-" },
    { label: "Rejected At", value: data.rejectedAt ? new Date(data.rejectedAt).toLocaleString() : "-" },
    { label: "Rejection Reason", value: data.rejectionReason || "-", color: data.rejectionReason ? "error.main" : "text.primary" },
  ];

  return (
    <Box>
      <PageHeader
        title={`Issuance Request Details`}
        breadcrumbs={[
          { label: "Admin", href: "/admin" },
          { label: "Issuance Queue", href: "/admin/issuance" },
          { label: "Details" },
        ]}
        action={
          <Button
            component={Link}
            href="/admin/issuance"
            startIcon={<BackIcon />}
            variant="outlined"
          >
            Back to Queue
          </Button>
        }
      />

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: "100%" }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
              <Typography variant="h6">General Information</Typography>
              <StatusChip status={data.status || ""} type="issuance" />
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Grid container spacing={2}>
              {detailItems.map((item, index) => (
                <React.Fragment key={index}>
                  <Grid item xs={5}>
                    <Typography variant="body2" color="text.secondary" fontWeight="medium">
                      {item.label}
                    </Typography>
                  </Grid>
                  <Grid item xs={7}>
                    <Typography
                      variant="body2"
                      sx={{
                        fontFamily: item.monospace ? "monospace" : "inherit",
                        color: item.color || "text.primary",
                        wordBreak: "break-all",
                      }}
                    >
                      {item.value}
                    </Typography>
                  </Grid>
                </React.Fragment>
              ))}
            </Grid>
          </Paper>
        </Grid>

        <Grid item xs={12} md={6}>
          <Paper sx={{ p: 3, height: "100%" }}>
            <Typography variant="h6" sx={{ mb: 3 }}>Proofing History</Typography>
            <Divider sx={{ mb: 2 }} />
            {data.proofingEvents && data.proofingEvents.length > 0 ? (
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Method</TableCell>
                      <TableCell>Outcome</TableCell>
                      <TableCell>Confidence</TableCell>
                      <TableCell>Date</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {data.proofingEvents.map((event, index) => (
                      <TableRow key={index}>
                        <TableCell>{PROOFING_METHOD_LABEL[event.method || ""] || event.method}</TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            color={event.outcome === "PASSED" ? "success.main" : "error.main"}
                            fontWeight="bold"
                          >
                            {PROOFING_OUTCOME_LABEL[event.outcome || ""] || event.outcome}
                          </Typography>
                        </TableCell>
                        <TableCell>{event.confidence ? `${(event.confidence * 100).toFixed(1)}%` : "-"}</TableCell>
                        <TableCell>
                          {event.performedAt ? new Date(event.performedAt).toLocaleDateString() : "-"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: 200 }}>
                <Typography variant="body2" color="text.secondary italic">
                  No proofing events recorded.
                </Typography>
              </Box>
            )}
          </Paper>
        </Grid>
      </Grid>
    </Box>
  );
}
