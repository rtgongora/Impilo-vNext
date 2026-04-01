"use client";

import { useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import {
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  TextField,
  MenuItem,
  InputAdornment,
  Typography,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import { useListClients } from "@/hooks/queries/useClients";
import { PageHeader } from "@/components/common/PageHeader";
import { StatusChip } from "@/components/common/StatusChip";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { ErrorAlert } from "@/components/common/ErrorAlert";
import type { ClientStatus } from "@/types/vito";

export default function ClientsPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState<ClientStatus | "ALL">("ALL");

  const { data, isLoading, error } = useListClients({
    page,
    size: rowsPerPage,
    q: search || undefined,
    status: status === "ALL" ? undefined : status,
  });

  const handleChangePage = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: React.ChangeEvent<HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  const handleRowClick = (healthId: string) => {
    router.push(`/admin/clients/${healthId}`);
  };

  const clientStatuses: (ClientStatus | "ALL")[] = ["ALL", "ACTIVE", "INACTIVE", "DECEASED"];

  return (
    <Box>
      <PageHeader
        title="Client Registry"
        breadcrumbs={[{ label: "Admin", href: "/admin" }, { label: "Clients" }]}
      />

      <Paper sx={{ mb: 3, p: 2 }}>
        <Box sx={{ display: "flex", gap: 2, mb: 3 }}>
          <TextField
            label="Search"
            variant="outlined"
            size="small"
            fullWidth
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
            placeholder="Search by Name, Health ID, Phone..."
          />
          <TextField
            select
            label="Status"
            variant="outlined"
            size="small"
            sx={{ minWidth: 200 }}
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as ClientStatus | "ALL");
              setPage(0);
            }}
          >
            {clientStatuses.map((s) => (
              <MenuItem key={s} value={s}>
                {s}
              </MenuItem>
            ))}
          </TextField>
        </Box>

        <ErrorAlert error={error} sx={{ mb: 2 }} />

        {isLoading ? (
          <LoadingSkeleton rows={rowsPerPage} columns={5} />
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Health ID</TableCell>
                  <TableCell>Full Name</TableCell>
                  <TableCell>Gender</TableCell>
                  <TableCell>DOB</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data?.items.map((client) => (
                  <TableRow
                    key={client.healthId}
                    hover
                    onClick={() => handleRowClick(client.healthId!)}
                    sx={{ cursor: "pointer" }}
                  >
                    <TableCell>{client.healthId}</TableCell>
                    <TableCell>
                      {client.givenName} {client.familyName}
                    </TableCell>
                    <TableCell>{client.gender}</TableCell>
                    <TableCell>{client.dateOfBirth}</TableCell>
                    <TableCell>
                      <StatusChip status={client.status!} />
                    </TableCell>
                  </TableRow>
                ))}
                {!data?.items.length && (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      <Typography variant="body2" sx={{ py: 4 }}>
                        No clients found
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            <TablePagination
              rowsPerPageOptions={[5, 10, 25, 50]}
              component="div"
              count={data?.totalElements || 0}
              rowsPerPage={rowsPerPage}
              page={page}
              onPageChange={handleChangePage}
              onRowsPerPageChange={handleChangeRowsPerPage}
            />
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
}
