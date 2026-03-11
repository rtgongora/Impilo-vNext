# 05 — State & Storage

> This document was provided inline during the Impilo vNext build session.

The provider tree is structured as QueryClient wrapping Auth, then Facility, Workspace, Shift, and finally the Router. Six React contexts are defined with their full state shapes and update semantics. Two sessionStorage keys persist ephemeral state across page reloads. Capability gating controls feature visibility based on facility and role configuration. Ten role-checking database functions enforce authorization at the data layer.
