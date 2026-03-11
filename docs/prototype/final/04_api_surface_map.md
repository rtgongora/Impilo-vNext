# 04 — API Surface Map

> This document was provided inline during the Impilo vNext build session.

The API surface encompasses approximately 60 Supabase tables, 30 RPC functions, 30 Edge Functions, and 2 storage buckets. Each table entry documents its full CRUD operations with row-level security policies. RPC functions are specified with parameter signatures and return types. Edge Functions cover server-side logic that cannot run at the database layer. Storage buckets handle file uploads for clinical documents and profile assets.
