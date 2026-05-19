# Nompilo Journey Companion Architecture

Nompilo is an embedded companion architecture, not a detached chatbot feature.

## Placement

- Experience plane presentation in One UI Shell and mobile surfaces.
- Composed context from Experience BFF using sovereign upstream truth.
- Event traceability through `core.nompilo.*` events on `core.transaction.events`.

## Boundaries

- Nompilo cannot become source-of-truth for registry, trust, clinical, financial, or enterprise data.
- Nompilo must respect Tshepo consent/access policy outcomes.
- Nompilo may explain decisions made by authorized services.

## Companion Surface Types

Floating assistant, inline hints, journey step guidance, smart nudges, explainers, guided workflows, accessibility assist, feedback prompts, human handoff, provider workflow assist, platform operations insights.
