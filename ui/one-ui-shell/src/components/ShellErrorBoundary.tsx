"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";
import Link from "next/link";
import { AlertTriangle } from "lucide-react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  message: string;
}

/** Prevents a single page/component failure from blanking the whole shell. */
export class ShellErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, message: "" };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, message: error.message || "Unexpected error" };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("[ShellErrorBoundary]", error, info.componentStack);
  }

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <div className="mx-auto max-w-lg rounded-2xl border border-danger/30 bg-card p-6 shadow-impilo-card m-4">
        <div className="flex items-start gap-3">
          <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-danger" aria-hidden />
          <div>
            <h2 className="text-sm font-semibold text-foreground">This page hit an error</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              {this.state.message.includes("fetch")
                ? "A backend service may not be deployed in this preview yet, or the page needs more defensive loading."
                : this.state.message}
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              <button
                type="button"
                className="impilo-btn-secondary text-xs"
                onClick={() => this.setState({ hasError: false, message: "" })}
              >
                Try again
              </button>
              <Link href="/home" className="impilo-btn-primary text-xs">
                Go home
              </Link>
              <Link href="/platform/all-features" className="impilo-btn-secondary text-xs">
                All features
              </Link>
            </div>
          </div>
        </div>
      </div>
    );
  }
}
