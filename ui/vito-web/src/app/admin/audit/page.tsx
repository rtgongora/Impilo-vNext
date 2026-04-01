"use client";

import React, { useState } from "react";
import {
  Box,
  Card,
  CardContent,
  Grid,
  TextField,
  MenuItem,
  FormControl,
  InputLabel,
  Select,
  Switch,
  FormControlLabel,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  IconButton,
  Collapse,
  Typography,
  TablePagination,
  Chip,
} from "@mui/material";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import KeyboardArrowUpIcon from "@mui/icons-material/KeyboardArrowUp";
import { PageHeader } from "@/components/common/PageHeader";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import { JsonViewer } from "@/components/common/JsonViewer";
import { useAuditLog } from "@/hooks/queries/useAudit";
import type { AuditEvent } from "@/types/vito";

const AGGREGATE_TYPES = [
  { label: "All Types", value: "" },
  { label: "Client", value: "client" },
  { label: "Issuance", value: "issuance" },
  { label: "Card", value: "card" },
  { label: "Wallet", value: "wallet" },
  { label: "Match", value: "match" },
  { label: "Dedup", value: "dedup" },
];

const EVENT_TYPES = [
  { label: "All Events", value: "" },
  { label: "Registered", value: "client.registered" },
  { label: "Updated", value: "client.updated" },
  { label: "Submitted", value: "issuance.submitted" },
  { label: "Approved", value: "issuance.approved" },
  { label: "Rejected", value: "issuance.rejected" },
  { label: "Requested", value: "card.requested" },
  { label: "Printed", value: "card.printed" },
  { label: "Activated", value: "card.activated" },
  { label: "Revoked", value: "card.revoked" },
  { label: "Created", value: "wallet.created" },
  { label: "Top-up", value: "wallet.topup" },
  { label: "Payment", value: "wallet.payment" },
  { label: "Resolved", value: "match.resolved" },
  { label: "Merged", value: "dedup.merged" },
];

function AuditRow({ event }: { event: AuditEvent }) {
  const [open, setOpen] = useState(false);

  const formatTimestamp = (ts: string | undefined) => {
    if (!ts) return "—";
    return new Date(ts).toLocaleString();
  };

  return (
    <>
      <TableRow sx={{ "& > *": { borderBottom: "unset" } }} hover onClick={() => setOpen(!open)} style={{ cursor: 'pointer' }}>
        <TableCell>
          <IconButton size="small" onClick={(e) => { e.stopPropagation(); setOpen(!open); }}>
            {open ? <KeyboardArrowUpIcon /> : <KeyboardArrowDownIcon />}
          </IconButton>
        </TableCell>
        <TableCell sx={{ whiteSpace: "nowrap" }}>
          {formatTimestamp(event.publishedAt ?? event.createdAt)}
        </TableCell>
        <TableCell>
          <Chip
            label={event.eventType ?? "—"}
            size="small"
            variant="outlined"
            sx={{ fontFamily: "monospace", fontSize: "0.75rem" }}
          />
        </TableCell>
        <TableCell>{event.aggregateType ?? "—"}</TableCell>
        <TableCell sx={{ fontFamily: "monospace", fontSize: "0.875rem" }}>
          {event.aggregateId ?? "—"}
        </TableCell>
        <TableCell sx={{ fontFamily: "monospace", fontSize: "0.875rem" }}>
          {event.actorId ?? "—"}
        </TableCell>
        <TableCell>{event.tenantId ?? "—"}</TableCell>
      </TableRow>
      <TableRow>
        <TableCell style={{ paddingBottom: 0, paddingTop: 0 }} colSpan={7}>
          <Collapse in={open} timeout="auto" unmountOnExit>
            <Box sx={{ margin: 2 }}>
              <Typography variant="subtitle2" gutterBottom component="div">
                Payload Details
              </Typography>
              <Paper variant="outlined" sx={{ p: 2, bgcolor: "grey.50" }}>
                <JsonViewer data={event.payload || {}} />
              </Paper>
              {event.correlationId && (
                <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                  Correlation ID: {event.correlationId}
                </Typography>
              )}
            </Box>
          </Collapse>
        </TableCell>
      </TableRow>
    </>
  );
}

export default function AuditLogPage() {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [aggregateType, setAggregateType] = useState("");
  const [eventType, setEventType] = useState("");
  const [actorId, setActorId] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [autoRefresh, setAutoRefresh] = useState(false);

  const { data, isLoading, error } = useAuditLog({
    page,
    size,
    aggregateType,
    eventType,
    actorId,
    from: fromDate ? new Date(fromDate).toISOString() : undefined,
    to: toDate ? new Date(toDate).toISOString() : undefined,
    refetchInterval: autoRefresh ? 10000 : undefined,
  });

  const handlePageChange = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleSizeChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSize(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <Box>
      <PageHeader
        title="Audit Log"
        breadcrumbs={[
          { label: "Admin", href: "/admin/dashboard" },
          { label: "Audit Log" },
        ]}
        action={
          <FormControlLabel
            control={
              <Switch
                checked={autoRefresh}
                onChange={(e) => setAutoRefresh(e.target.checked)}
                color="primary"
              />
            }
            label="Auto-refresh"
          />
        }
      />

      <Card sx={{ mb: 4 }}>
        <CardContent>
          <Grid container spacing={2} alignItems="center">
            <Grid item xs={12} sm={6} md={2}>
              <FormControl fullWidth size="small">
                <InputLabel>Aggregate Type</InputLabel>
                <Select
                  value={aggregateType}
                  label="Aggregate Type"
                  onChange={(e) => {
                    setAggregateType(e.target.value);
                    setPage(0);
                  }}
                >
                  {AGGREGATE_TYPES.map((t) => (
                    <MenuItem key={t.value} value={t.value}>
                      {t.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6} md={2}>
              <FormControl fullWidth size="small">
                <InputLabel>Event Type</InputLabel>
                <Select
                  value={eventType}
                  label="Event Type"
                  onChange={(e) => {
                    setEventType(e.target.value);
                    setPage(0);
                  }}
                >
                  {EVENT_TYPES.map((t) => (
                    <MenuItem key={t.value} value={t.value}>
                      {t.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6} md={2}>
              <TextField
                fullWidth
                size="small"
                label="Actor ID"
                value={actorId}
                onChange={(e) => {
                  setActorId(e.target.value);
                  setPage(0);
                }}
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <TextField
                fullWidth
                size="small"
                label="From Date"
                type="datetime-local"
                InputLabelProps={{ shrink: true }}
                value={fromDate}
                onChange={(e) => {
                  setFromDate(e.target.value);
                  setPage(0);
                }}
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <TextField
                fullWidth
                size="small"
                label="To Date"
                type="datetime-local"
                InputLabelProps={{ shrink: true }}
                value={toDate}
                onChange={(e) => {
                  setToDate(e.target.value);
                  setPage(0);
                }}
              />
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {error && <ErrorAlert error={error} sx={{ mb: 2 }} />}

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell width={50} />
              <TableCell>Timestamp</TableCell>
              <TableCell>Event Type</TableCell>
              <TableCell>Aggregate Type</TableCell>
              <TableCell>Aggregate ID</TableCell>
              <TableCell>Actor</TableCell>
              <TableCell>Tenant</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading ? (
              <LoadingSkeleton rows={10} columns={7} />
            ) : (
              data?.items.map((event) => (
                <AuditRow key={event.id} event={event} />
              ))
            )}
            {!isLoading && !data?.items.length && (
              <TableRow>
                <TableCell colSpan={7} align="center" sx={{ py: 8 }}>
                  <Typography variant="body1" color="text.secondary">
                    No audit events found matching the filters.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={data?.totalElements ?? 0}
          page={page}
          onPageChange={handlePageChange}
          rowsPerPage={size}
          onRowsPerPageChange={handleSizeChange}
          rowsPerPageOptions={[10, 25, 50, 100]}
        />
      </TableContainer>
    </Box>
  );
}
