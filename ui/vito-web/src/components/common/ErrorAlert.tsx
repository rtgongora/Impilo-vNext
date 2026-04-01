"use client";

import React from "react";
import { Alert, AlertTitle, Box, Typography } from "@mui/material";
import { VitoApiError } from "@/lib/apiClient";

interface ErrorAlertProps {
  error: Error | VitoApiError | null | unknown;
}

export const ErrorAlert: React.FC<ErrorAlertProps> = ({ error }) => {
  if (!error) return null;

  let message = "An unexpected error occurred.";
  let code: string | undefined;
  let correlationId: string | undefined;

  if (error instanceof VitoApiError) {
    message = error.message;
    code = error.code;
    correlationId = error.correlationId;
  } else if (error instanceof Error) {
    message = error.message;
  } else if (typeof error === "string") {
    message = error;
  }

  return (
    <Alert severity="error" sx={{ mb: 3 }}>
      <AlertTitle>Error</AlertTitle>
      <Typography variant="body2">{message}</Typography>
      {(code || correlationId) && (
        <Box sx={{ mt: 1, opacity: 0.8, fontSize: "0.75rem" }}>
          {code && (
            <Box component="span" sx={{ mr: 2 }}>
              Code: <strong>{code}</strong>
            </Box>
          )}
          {correlationId && (
            <Box component="span">
              Correlation ID: <strong>{correlationId}</strong>
            </Box>
          )}
        </Box>
      )}
    </Alert>
  );
};
