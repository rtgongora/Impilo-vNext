"use client";

import React from "react";
import { Box, Typography, Paper } from "@mui/material";

interface JsonViewerProps {
  data: unknown;
  title?: string;
}

export const JsonViewer: React.FC<JsonViewerProps> = ({ data, title }) => {
  if (!data) return null;

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        backgroundColor: "grey.50",
        borderRadius: 1,
        overflow: "auto",
      }}
    >
      {title && (
        <Typography
          variant="caption"
          display="block"
          color="text.secondary"
          sx={{ mb: 1, fontWeight: "bold", textTransform: "uppercase" }}
        >
          {title}
        </Typography>
      )}
      <Box
        component="pre"
        sx={{
          margin: 0,
          fontFamily: "monospace",
          fontSize: "0.8125rem",
          whiteSpace: "pre-wrap",
          wordBreak: "break-all",
        }}
      >
        {JSON.stringify(data, null, 2)}
      </Box>
    </Paper>
  );
};
