# 01 — Site Map

> This document was provided inline during the Impilo vNext build session.

The site map defines 98 routes distributed across 15+ zones. Each route entry specifies its zone, authentication requirements, layout variant, sidebar context, and primary component. Sidebar context is resolved dynamically from the URL path. Route guard rules enforce access control at the navigation layer, ensuring users can only reach routes permitted by their current auth state and role.
