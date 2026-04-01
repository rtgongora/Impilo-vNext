"use client";

import {
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ListSubheader,
  Divider,
  Toolbar,
  Box,
} from "@mui/material";
import {
  People as PeopleIcon,
  PersonAdd as PersonAddIcon,
  Assignment as AssignmentIcon,
  CreditCard as CreditCardIcon,
  Compare as CompareIcon,
  MergeType as MergeTypeIcon,
  AccountBalanceWallet as AccountBalanceWalletIcon,
  Fingerprint as FingerprintIcon,
  Print as PrintIcon,
  QrCode as QrCodeIcon,
  Settings as SettingsIcon,
  History as HistoryIcon,
} from "@mui/icons-material";
import { usePathname, useRouter } from "next/navigation";

const DRAWER_WIDTH = 240;

interface NavItem {
  label: string;
  path: string;
  icon: React.ReactNode;
}

interface NavGroup {
  subheader: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    subheader: "Registry",
    items: [
      { label: "Clients", path: "/admin/clients", icon: <PeopleIcon /> },
      { label: "Register Client", path: "/admin/clients/register", icon: <PersonAddIcon /> },
      { label: "Issuance Queue", path: "/admin/issuance", icon: <AssignmentIcon /> },
      { label: "SMART Cards", path: "/admin/cards", icon: <CreditCardIcon /> },
    ],
  },
  {
    subheader: "Operations",
    items: [
      { label: "Match Queue", path: "/admin/match-queue", icon: <CompareIcon /> },
      { label: "Dedup Cases", path: "/admin/dedup", icon: <MergeTypeIcon /> },
      { label: "Wallets", path: "/admin/wallets", icon: <AccountBalanceWalletIcon /> },
      { label: "Biometric", path: "/admin/biometric", icon: <FingerprintIcon /> },
      { label: "Print Jobs", path: "/admin/print", icon: <PrintIcon /> },
      { label: "QR & Slips", path: "/admin/qr", icon: <QrCodeIcon /> },
    ],
  },
  {
    subheader: "Administration",
    items: [
      { label: "Registry Config", path: "/admin/registry", icon: <SettingsIcon /> },
      { label: "Audit Log", path: "/admin/audit", icon: <HistoryIcon /> },
    ],
  },
];

interface AdminDrawerProps {
  open: boolean;
  onClose?: () => void;
  variant?: "permanent" | "temporary";
}

export function AdminDrawer({ open, onClose, variant = "permanent" }: AdminDrawerProps) {
  const pathname = usePathname();
  const router = useRouter();

  const handleNavigate = (path: string) => {
    router.push(path);
    if (onClose && variant === "temporary") {
      onClose();
    }
  };

  return (
    <Drawer
      variant={variant}
      open={open}
      onClose={onClose}
      sx={{
        width: DRAWER_WIDTH,
        flexShrink: 0,
        "& .MuiDrawer-paper": {
          width: DRAWER_WIDTH,
          boxSizing: "border-box",
        },
      }}
    >
      <Toolbar />
      <Box sx={{ overflow: "auto" }}>
        {NAV_GROUPS.map((group, groupIndex) => (
          <List
            key={group.subheader}
            subheader={
              <ListSubheader component="div" id={`nested-list-subheader-${groupIndex}`}>
                {group.subheader}
              </ListSubheader>
            }
          >
            {group.items.map((item) => (
              <ListItemButton
                key={item.path}
                onClick={() => handleNavigate(item.path)}
                selected={pathname === item.path || pathname.startsWith(`${item.path}/`)}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
            <Divider sx={{ my: 1 }} />
          </List>
        ))}
      </Box>
    </Drawer>
  );
}
