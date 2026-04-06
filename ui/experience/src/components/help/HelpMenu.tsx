"use client";

/**
 * HelpMenu -- Popover-based help menu with quick links, searchable resources,
 * contextual tips, keyboard shortcuts, and support contact options.
 *
 * Variant: "icon" | "button" | "floating"
 */

import { useState, useRef, useEffect } from "react";
import { useRouter } from "next/navigation";
import {
  HelpCircle,
  BookOpen,
  MessageCircle,
  FileText,
  Video,
  Keyboard,
  ExternalLink,
  Lightbulb,
  Search,
} from "lucide-react";

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

interface QuickLink {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  description: string;
  path: string;
  tab?: string;
}

const quickLinks: QuickLink[] = [
  {
    icon: BookOpen,
    label: "User Guides",
    description: "Step-by-step tutorials",
    path: "/help",
    tab: "guides",
  },
  {
    icon: FileText,
    label: "FAQs",
    description: "Frequently asked questions",
    path: "/help",
    tab: "faqs",
  },
  {
    icon: Video,
    label: "Video Tutorials",
    description: "Watch and learn",
    path: "/help",
    tab: "guides",
  },
  {
    icon: FileText,
    label: "Documentation",
    description: "Full system docs",
    path: "/help",
    tab: "docs",
  },
];

interface ContextualTip {
  label: string;
  tip: string;
}

const contextualTips: ContextualTip[] = [
  {
    label: "Keyboard Shortcuts",
    tip: "Press Ctrl+K to search patients quickly",
  },
  {
    label: "Quick Tip",
    tip: "Double-click a patient row to open their chart",
  },
  {
    label: "Did you know?",
    tip: "You can use voice commands for hands-free documentation",
  },
];

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface HelpMenuProps {
  variant?: "icon" | "button" | "floating";
  className?: string;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function HelpMenu({ variant = "icon", className }: HelpMenuProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const popoverRef = useRef<HTMLDivElement>(null);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    function handleClickOutside(e: MouseEvent) {
      if (
        popoverRef.current &&
        !popoverRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [open]);

  const filteredLinks = quickLinks.filter(
    (link) =>
      link.label.toLowerCase().includes(searchQuery.toLowerCase()) ||
      link.description.toLowerCase().includes(searchQuery.toLowerCase()),
  );

  const handleNavigate = (path: string, tab?: string) => {
    setOpen(false);
    router.push(tab ? `${path}?tab=${tab}` : path);
  };

  // Render trigger button based on variant
  const triggerButton = (() => {
    switch (variant) {
      case "floating":
        return (
          <button
            onClick={() => setOpen(!open)}
            className={`h-10 rounded-full shadow-md bg-blue-50 text-blue-600 hover:bg-blue-100 border border-blue-200 px-4 flex items-center gap-2 text-xs font-medium backdrop-blur-sm ${className ?? ""}`}
          >
            <HelpCircle className="h-3.5 w-3.5" />
            <span>Need Help?</span>
          </button>
        );
      case "button":
        return (
          <button
            onClick={() => setOpen(!open)}
            className={`flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium bg-emerald-600 hover:bg-emerald-700 text-white rounded-md transition-colors ${className ?? ""}`}
          >
            <HelpCircle className="h-4 w-4" />
            <span>Help</span>
          </button>
        );
      case "icon":
      default:
        return (
          <button
            onClick={() => setOpen(!open)}
            className={`h-9 w-9 flex items-center justify-center text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-md transition-colors ${className ?? ""}`}
          >
            <HelpCircle className="h-4 w-4" />
          </button>
        );
    }
  })();

  return (
    <div className="relative" ref={popoverRef}>
      {triggerButton}

      {open && (
        <div className="absolute right-0 top-full mt-2 w-80 bg-white border border-gray-200 rounded-lg shadow-lg z-50">
          {/* Header */}
          <div className="p-3 pb-2">
            <h3 className="font-semibold text-sm">Help &amp; Resources</h3>
            <p className="text-xs text-gray-500">
              Find answers and learn Impilo
            </p>
          </div>

          {/* Search */}
          <div className="px-3 pb-2">
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-gray-400" />
              <input
                placeholder="Search help..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full h-8 pl-8 pr-3 text-sm border rounded-md border-gray-300 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          <hr className="border-gray-100" />

          {/* Scrollable content */}
          <div className="max-h-[320px] overflow-y-auto">
            {/* Quick Links */}
            <div className="p-2">
              <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wider px-2 mb-1">
                Resources
              </p>
              {filteredLinks.map((link) => {
                const Icon = link.icon;
                return (
                  <button
                    key={link.label}
                    onClick={() => handleNavigate(link.path, link.tab)}
                    className="w-full flex items-center gap-3 px-2 py-2 rounded-md hover:bg-gray-50 transition-colors text-left"
                  >
                    <div className="h-8 w-8 rounded-md bg-blue-50 flex items-center justify-center shrink-0">
                      <Icon className="h-4 w-4 text-blue-600" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-medium leading-tight">
                        {link.label}
                      </p>
                      <p className="text-xs text-gray-500">
                        {link.description}
                      </p>
                    </div>
                    <ExternalLink className="h-3 w-3 text-gray-400 ml-auto shrink-0" />
                  </button>
                );
              })}
            </div>

            <hr className="border-gray-100" />

            {/* Contextual Tips */}
            <div className="p-2">
              <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wider px-2 mb-1">
                Quick Tips
              </p>
              {contextualTips.map((tip, i) => (
                <div
                  key={i}
                  className="flex items-start gap-2 px-2 py-1.5 rounded-md"
                >
                  <Lightbulb className="h-3.5 w-3.5 text-amber-500 mt-0.5 shrink-0" />
                  <div>
                    <p className="text-xs font-medium">{tip.label}</p>
                    <p className="text-xs text-gray-500">{tip.tip}</p>
                  </div>
                </div>
              ))}
            </div>

            <hr className="border-gray-100" />

            {/* Support */}
            <div className="p-2">
              <p className="text-[10px] font-medium text-gray-400 uppercase tracking-wider px-2 mb-1">
                Support
              </p>
              <button
                onClick={() => handleNavigate("/help")}
                className="w-full flex items-center gap-3 px-2 py-2 rounded-md hover:bg-gray-50 transition-colors text-left"
              >
                <div className="h-8 w-8 rounded-md bg-green-50 flex items-center justify-center shrink-0">
                  <MessageCircle className="h-4 w-4 text-green-600" />
                </div>
                <div>
                  <p className="text-sm font-medium">Contact Support</p>
                  <p className="text-xs text-gray-500">
                    Get help from the team
                  </p>
                </div>
              </button>
              <button
                onClick={() => handleNavigate("/help")}
                className="w-full flex items-center gap-3 px-2 py-2 rounded-md hover:bg-gray-50 transition-colors text-left"
              >
                <div className="h-8 w-8 rounded-md bg-blue-50 flex items-center justify-center shrink-0">
                  <Keyboard className="h-4 w-4 text-blue-600" />
                </div>
                <div>
                  <p className="text-sm font-medium">Keyboard Shortcuts</p>
                  <p className="text-xs text-gray-500">View all shortcuts</p>
                </div>
              </button>
            </div>
          </div>

          <hr className="border-gray-100" />

          {/* Footer */}
          <div className="p-2">
            <button
              onClick={() => handleNavigate("/help")}
              className="w-full flex items-center justify-center gap-1.5 px-3 py-1.5 text-xs font-medium border rounded-md border-gray-300 text-gray-700 hover:bg-gray-50 transition-colors"
            >
              Open Full Help Center
              <ExternalLink className="h-3 w-3" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
