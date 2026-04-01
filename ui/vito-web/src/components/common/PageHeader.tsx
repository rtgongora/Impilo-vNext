"use client";

import React from "react";
import { Box, Typography, Breadcrumbs, Link } from "@mui/material";
import NextLink from "next/link";
import NavigateNextIcon from "@mui/icons-material/NavigateNext";

interface Breadcrumb {
  label: string;
  href?: string;
}

interface PageHeaderProps {
  title: string;
  breadcrumbs?: Breadcrumb[];
  action?: React.ReactNode;
}

export const PageHeader: React.FC<PageHeaderProps> = ({
  title,
  breadcrumbs,
  action,
}) => {
  return (
    <Box sx={{ mb: 4 }}>
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Breadcrumbs
          separator={<NavigateNextIcon fontSize="small" />}
          aria-label="breadcrumb"
          sx={{ mb: 1 }}
        >
          {breadcrumbs.map((b, i) =>
            b.href ? (
              <Link
                key={i}
                component={NextLink}
                underline="hover"
                color="inherit"
                href={b.href}
                sx={{ fontSize: "0.875rem" }}
              >
                {b.label}
              </Link>
            ) : (
              <Typography
                key={i}
                color="text.primary"
                sx={{ fontSize: "0.875rem" }}
              >
                {b.label}
              </Typography>
            )
          )}
        </Breadcrumbs>
      )}
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <Typography variant="h5" component="h1" fontWeight="bold">
          {title}
        </Typography>
        {action && <Box>{action}</Box>}
      </Box>
    </Box>
  );
};
