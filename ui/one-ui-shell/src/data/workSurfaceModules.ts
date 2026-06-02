import type { LucideIcon } from "lucide-react";
import {
  Activity,
  ArrowRightLeft,
  BarChart3,
  Bed,
  Bell,
  BookOpen,
  Building2,
  Calendar,
  ClipboardCheck,
  ClipboardList,
  Clock,
  Database,
  DollarSign,
  DoorOpen,
  FileCheck,
  FileHeart,
  FileText,
  FlaskConical,
  Gauge,
  Heart,
  HelpCircle,
  LayoutGrid,
  Megaphone,
  MessageSquare,
  Package,
  Pill,
  Receipt,
  Scan,
  Settings,
  Shield,
  ShoppingCart,
  Stethoscope,
  Store,
  Syringe,
  TestTube2,
  TrendingUp,
  UserCog,
  UserPlus,
  Users,
  Video,
  Wallet,
} from "lucide-react";
import type { WorkCategoryModule } from "@/components/home/ExpandableWorkCategoryCard";

/** Mirrors Lovable `ModuleHome` role gates, mapped to Experience role groups. */
export type WorkSurfaceRoleKey =
  | "clinical"
  | "admin"
  | "finance"
  | "dispenser"
  | "queue"
  | "commerce"
  | "prescriber"
  | "registry";

export interface WorkSurfaceFilterFlags {
  isClinical: boolean;
  isAdmin: boolean;
  isFinance: boolean;
  isDispenser: boolean;
  isQueueManager: boolean;
  isCommerce: boolean;
  isPrescriber: boolean;
  isRegistryAdmin: boolean;
}

type CatalogModule = WorkCategoryModule & {
  requires?: WorkSurfaceRoleKey[];
};

export interface WorkSurfaceCategoryRow {
  id: string;
  title: string;
  description: string;
  icon: LucideIcon;
  color: string;
  categoryRequires?: WorkSurfaceRoleKey[];
  modules: CatalogModule[];
}

function roleOk(key: WorkSurfaceRoleKey, f: WorkSurfaceFilterFlags): boolean {
  switch (key) {
    case "clinical":
      return f.isClinical;
    case "admin":
      return f.isAdmin;
    case "finance":
      return f.isFinance;
    case "dispenser":
      return f.isDispenser;
    case "queue":
      return f.isQueueManager;
    case "commerce":
      return f.isCommerce;
    case "prescriber":
      return f.isPrescriber;
    case "registry":
      return f.isRegistryAdmin;
    default:
      return false;
  }
}

function canSeeModule(m: CatalogModule, f: WorkSurfaceFilterFlags): boolean {
  if (!m.requires?.length) return true;
  return m.requires.some((k) => roleOk(k, f));
}

function canSeeCategory(cat: WorkSurfaceCategoryRow, f: WorkSurfaceFilterFlags): boolean {
  if (!cat.categoryRequires?.length) return true;
  return cat.categoryRequires.some((k) => roleOk(k, f));
}

/** Practice Management card — first tile on Lovable Work surface. */
export const PRACTICE_MANAGEMENT_CATEGORY: WorkSurfaceCategoryRow = {
  id: "practice-management",
  title: "Practice Management",
  description: "Manage your practice or facility",
  icon: Building2,
  color: "bg-teal-500",
  modules: [
    {
      id: "schedule",
      label: "Schedule",
      description: "Appointments",
      href: "/scheduling",
      icon: Calendar,
      color: "bg-teal-500",
    },
    {
      id: "patients",
      label: "Patients",
      description: "Find a patient chart",
      href: "/queue/search",
      icon: Users,
      color: "bg-blue-500",
    },
    {
      id: "billing",
      label: "Billing",
      description: "Bills & invoices",
      href: "/finance/billing",
      icon: Wallet,
      color: "bg-green-500",
      requires: ["finance"],
    },
    {
      id: "analytics",
      label: "Analytics",
      description: "Reports & dashboards",
      href: "/reports",
      icon: TrendingUp,
      color: "bg-purple-500",
    },
    {
      id: "staff",
      label: "Staff",
      description: "Users & administration",
      href: "/admin/users",
      icon: UserCog,
      color: "bg-orange-500",
      requires: ["admin"],
    },
    {
      id: "inventory",
      label: "Inventory",
      description: "Stock & supply",
      href: "/inventory",
      icon: Package,
      color: "bg-amber-500",
    },
  ],
};

const WORK_SURFACE_CATEGORIES: WorkSurfaceCategoryRow[] = [
  {
    id: "clinical",
    title: "Clinical Care",
    description: "Patient encounters, assessments, and care delivery",
    icon: Stethoscope,
    color: "bg-blue-500",
    modules: [
      {
        id: "dashboard",
        label: "My Dashboard",
        description: "Command centre & operations",
        href: "/facility-operations",
        icon: ClipboardList,
        color: "bg-impilo-600",
      },
      {
        id: "communication",
        label: "Communication",
        description: "Messages, pages & calls",
        href: "/communication",
        icon: MessageSquare,
        color: "bg-impilo-600",
      },
      {
        id: "sorting",
        label: "Patient Sorting",
        description: "Triage & front-desk flow",
        href: "/queue/triage",
        icon: ClipboardCheck,
        color: "bg-orange-500",
      },
      {
        id: "ehr",
        label: "Patient Encounters",
        description: "Clinical hub & documentation",
        href: "/clinical",
        icon: Stethoscope,
        color: "bg-blue-500",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "queue",
        label: "Patient Queue",
        description: "Waiting patients & service",
        href: "/queue",
        icon: Users,
        color: "bg-orange-500",
      },
      {
        id: "beds",
        label: "Bed Management",
        description: "Ward status & census",
        href: "/beds",
        icon: Bed,
        color: "bg-purple-500",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "discharge",
        label: "Discharge & Exit",
        description: "Open a chart to complete discharge",
        href: "/queue/search",
        icon: DoorOpen,
        color: "bg-amber-600",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "control-tower",
        label: "Control Tower",
        description: "Real-time facility operations",
        href: "/clinical/control-tower",
        icon: Gauge,
        color: "bg-rose-600",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "operations",
        label: "Operations & Roster",
        description: "Shifts & workforce",
        href: "/shift",
        icon: Clock,
        color: "bg-cyan-600",
      },
      {
        id: "handoff",
        label: "Shift Handoff",
        description: "Care continuity",
        href: "/shift/handover",
        icon: ArrowRightLeft,
        color: "bg-teal-500",
        requires: ["clinical"],
        restricted: true,
      },
    ],
  },
  {
    id: "consults-referrals",
    title: "Consults & Referrals",
    description: "Telemedicine, consults, and referrals",
    icon: Video,
    color: "bg-teal-500",
    modules: [
      {
        id: "telemedicine-hub",
        label: "Telemedicine Hub",
        description: "Teleconsultation workspace",
        href: "/telemedicine",
        icon: Video,
        color: "bg-impilo-600",
      },
      {
        id: "referrals",
        label: "Referrals",
        description: "Outgoing & incoming referrals",
        href: "/queue/incoming-referrals",
        icon: ArrowRightLeft,
        color: "bg-blue-500",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "consults",
        label: "Consultations",
        description: "Specialist consults",
        href: "/telemedicine",
        icon: Stethoscope,
        color: "bg-teal-500",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "case-reviews",
        label: "Case Reviews & Boards",
        description: "Reviews & MDT",
        href: "/telemedicine",
        icon: Users,
        color: "bg-purple-500",
        requires: ["clinical"],
        restricted: true,
      },
    ],
  },
  {
    id: "orders",
    title: "Orders & Diagnostics",
    description: "Lab, imaging, pharmacy, and orders",
    icon: ShoppingCart,
    color: "bg-green-500",
    modules: [
      {
        id: "orders",
        label: "Order Entry",
        description: "Medications, labs & imaging",
        href: "/clinical",
        icon: ShoppingCart,
        color: "bg-green-500",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "eprescriptions",
        label: "ePrescriptions",
        description: "Prescribing & formulary",
        href: "/pharmacy/prescriptions",
        icon: Pill,
        color: "bg-emerald-600",
        requires: ["prescriber"],
        restricted: true,
      },
      {
        id: "pharmacy",
        label: "Pharmacy",
        description: "Dispensing & Rx",
        href: "/pharmacy",
        icon: Syringe,
        color: "bg-pink-500",
        requires: ["dispenser", "clinical"],
        restricted: true,
      },
      {
        id: "lims",
        label: "Laboratory",
        description: "Lab worklist & results",
        href: "/lab",
        icon: FlaskConical,
        color: "bg-amber-500",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "pacs",
        label: "Imaging (PACS)",
        description: "Radiology workflows",
        href: "/queue/search?workflow=pacs",
        icon: FileText,
        color: "bg-indigo-500",
        requires: ["clinical"],
        restricted: true,
      },
    ],
  },
  {
    id: "scheduling",
    title: "Scheduling & Registration",
    description: "Appointments, registration, and theatre",
    icon: Calendar,
    color: "bg-cyan-500",
    modules: [
      {
        id: "appointments",
        label: "Appointments",
        description: "Clinic scheduling",
        href: "/queue/scheduled",
        icon: Calendar,
        color: "bg-cyan-500",
      },
      {
        id: "scheduling",
        label: "Appointment Scheduling",
        description: "Advanced scheduling",
        href: "/scheduling",
        icon: Calendar,
        color: "bg-blue-500",
        requires: ["queue"],
        restricted: true,
      },
      {
        id: "noticeboard",
        label: "Provider Noticeboard",
        description: "Announcements",
        href: "/scheduling/noticeboard",
        icon: Megaphone,
        color: "bg-amber-500",
      },
      {
        id: "resources",
        label: "Resource Calendar",
        description: "Rooms & resources",
        href: "/facility-operations/resources",
        icon: LayoutGrid,
        color: "bg-indigo-500",
        requires: ["queue"],
        restricted: true,
      },
      {
        id: "registration",
        label: "Patient Registration",
        description: "Registry intake",
        href: "/registry/intake",
        icon: UserPlus,
        color: "bg-emerald-500",
      },
      {
        id: "theatre",
        label: "Theatre Booking",
        description: "Surgical scheduling",
        href: "/scheduling",
        icon: Building2,
        color: "bg-rose-500",
        requires: ["clinical"],
        restricted: true,
      },
    ],
  },
  {
    id: "marketplace",
    title: "Health Products & Marketplace",
    description: "Catalogue, vendors, and commerce",
    icon: Store,
    color: "bg-purple-500",
    modules: [
      {
        id: "catalogue",
        label: "Health Products Catalogue",
        description: "Approved products",
        href: "/marketplace/catalog",
        icon: BookOpen,
        color: "bg-blue-600",
        requires: ["commerce"],
        restricted: true,
      },
      {
        id: "marketplace",
        label: "Health Marketplace",
        description: "Orders & vendors",
        href: "/marketplace",
        icon: Store,
        color: "bg-green-600",
      },
      {
        id: "vendor-portal",
        label: "Vendor Portal",
        description: "Vendor fulfilment",
        href: "/marketplace/vendor",
        icon: Building2,
        color: "bg-orange-600",
        requires: ["commerce"],
        restricted: true,
      },
    ],
  },
  {
    id: "finance",
    title: "Finance & Billing",
    description: "Payments, charges, and claims",
    icon: DollarSign,
    color: "bg-emerald-500",
    categoryRequires: ["finance"],
    modules: [
      {
        id: "payments",
        label: "Payments",
        description: "Collections & receipts",
        href: "/finance/payments",
        icon: DollarSign,
        color: "bg-green-600",
      },
      {
        id: "claims",
        label: "Claims",
        description: "Insurance claims",
        href: "/finance/claims",
        icon: ClipboardList,
        color: "bg-purple-600",
      },
      {
        id: "billing",
        label: "Billing workspace",
        description: "Bills & invoices",
        href: "/finance/billing",
        icon: Receipt,
        color: "bg-yellow-600",
      },
    ],
  },
  {
    id: "inventory",
    title: "Inventory & Supply Chain",
    description: "Stock and requisitions",
    icon: Package,
    color: "bg-orange-500",
    modules: [
      {
        id: "stock",
        label: "Stock Management",
        description: "Inventory dashboard",
        href: "/inventory/stock-management",
        icon: Package,
        color: "bg-orange-600",
        requires: ["admin", "dispenser"],
        restricted: true,
      },
      {
        id: "movements",
        label: "Stock Movements",
        description: "Transfers & issues",
        href: "/inventory/movements",
        icon: ArrowRightLeft,
        color: "bg-red-500",
      },
    ],
  },
  {
    id: "identity",
    title: "Identity Services",
    description: "Health IDs and validation",
    icon: Shield,
    color: "bg-indigo-500",
    categoryRequires: ["admin", "registry"],
    modules: [
      {
        id: "id-services",
        label: "ID Services Hub",
        description: "Generate, validate & recover",
        href: "/id-services",
        icon: Shield,
        color: "bg-impilo-600",
        requires: ["admin", "registry"],
        restricted: true,
      },
      {
        id: "phid-generation",
        label: "Patient PHID",
        description: "Patient identifiers",
        href: "/id-services?tab=generate",
        icon: UserCog,
        color: "bg-blue-500",
        requires: ["admin", "registry"],
        restricted: true,
      },
      {
        id: "id-validate",
        label: "ID Validation",
        description: "Verify authenticity",
        href: "/id-services?tab=validate",
        icon: Shield,
        color: "bg-green-500",
      },
    ],
  },
  {
    id: "registries",
    title: "HIE Registries",
    description: "National registries & terminology",
    icon: Database,
    color: "bg-rose-500",
    categoryRequires: ["admin", "registry", "clinical"],
    modules: [
      {
        id: "clients",
        label: "Client Registry",
        description: "Master patient index",
        href: "/registry/clients",
        icon: Users,
        color: "bg-blue-500",
        requires: ["registry", "admin"],
        restricted: true,
      },
      {
        id: "providers",
        label: "Provider Registry",
        description: "National workforce directory",
        href: "/registry/providers",
        icon: Stethoscope,
        color: "bg-teal-500",
      },
      {
        id: "facilities",
        label: "Facility Registry",
        description: "Sites & services",
        href: "/registry/facilities",
        icon: Building2,
        color: "bg-purple-500",
      },
      {
        id: "terminology",
        label: "Terminology Service",
        description: "Codes & concepts",
        href: "/registry/terminology",
        icon: BookOpen,
        color: "bg-amber-500",
      },
      {
        id: "products",
        label: "Product Registry",
        description: "Devices & supplies",
        href: "/registry/products",
        icon: Package,
        color: "bg-green-500",
        requires: ["admin", "registry"],
        restricted: true,
      },
    ],
  },
  {
    id: "admin",
    title: "Administration & Reports",
    description: "Oversight, documents, and settings",
    icon: Settings,
    color: "bg-slate-600",
    categoryRequires: ["admin", "clinical"],
    modules: [
      {
        id: "district",
        label: "District / Above-site",
        description: "Multi-facility operations",
        href: "/facility-operations/district-view",
        icon: TrendingUp,
        color: "bg-rose-600",
        requires: ["admin"],
        restricted: true,
      },
      {
        id: "documents",
        label: "My Documents",
        description: "Personal health documents",
        href: "/home/documents",
        icon: FileHeart,
        color: "bg-cyan-600",
      },
      {
        id: "reports",
        label: "Reports & Analytics",
        description: "Operational insight",
        href: "/reports",
        icon: BarChart3,
        color: "bg-violet-500",
      },
      {
        id: "admin",
        label: "System Admin",
        description: "Users, audit & policies",
        href: "/admin",
        icon: Settings,
        color: "bg-gray-700",
        requires: ["admin"],
        restricted: true,
      },
    ],
  },
  {
    id: "clinical-tools",
    title: "Clinical Tools",
    description: "Documentation utilities",
    icon: Activity,
    color: "bg-pink-500",
    categoryRequires: ["clinical"],
    modules: [
      {
        id: "voice-dictation",
        label: "Voice Dictation",
        description: "Speech-to-text",
        href: "/clinical/dictation",
        icon: Activity,
        color: "bg-rose-500",
        requires: ["clinical"],
        restricted: true,
      },
      {
        id: "clinical-tools",
        label: "Clinical Tools Hub",
        description: "Rules, forms & CDS",
        href: "/clinical-tools",
        icon: FileCheck,
        color: "bg-slate-600",
        requires: ["clinical"],
        restricted: true,
      },
    ],
  },
  {
    id: "support",
    title: "Help & Support",
    description: "Help desk and account",
    icon: HelpCircle,
    color: "bg-teal-500",
    modules: [
      {
        id: "help",
        label: "Help Desk",
        description: "Support & knowledge base",
        href: "/support/knowledge-base",
        icon: HelpCircle,
        color: "bg-teal-500",
      },
      {
        id: "profile",
        label: "Profile Settings",
        description: "Account & preferences",
        href: "/home/profile",
        icon: Heart,
        color: "bg-slate-500",
      },
      {
        id: "kiosk",
        label: "Patient Kiosk",
        description: "Self-service check-in",
        href: "/kiosk",
        icon: Scan,
        color: "bg-blue-600",
      },
    ],
  },
];

export function filterWorkSurfaceCategories(flags: WorkSurfaceFilterFlags): WorkSurfaceCategoryRow[] {
  const practice: WorkSurfaceCategoryRow = {
    ...PRACTICE_MANAGEMENT_CATEGORY,
    modules: PRACTICE_MANAGEMENT_CATEGORY.modules.filter((m) => canSeeModule(m, flags)),
  };

  const rest = WORK_SURFACE_CATEGORIES.filter((cat) => canSeeCategory(cat, flags))
    .map((cat) => ({
      ...cat,
      modules: cat.modules.filter((m) => canSeeModule(m, flags)),
    }))
    .filter((cat) => cat.modules.length > 0);

  return [practice, ...rest];
}
