# Core Transaction Data Ownership

## Source-of-Truth Discipline

- **Registry truth**: Vito, Varapi, Tuso, Zibo, Msika, Indawo.
- **Trust truth**: Tshepo/Mvumo/Tshepo Audit.
- **Clinical truth**: Butano + clinical execution services.
- **Financial truth**: Costa, MusheX, coverage, GL.
- **Experience truth**: UI composition state and presentation preferences only.

## Experience Constraints

Experience and BFF may cache/compose but must not:

1. invent separate patient/provider/facility identifiers;
2. hold clinical source records as canonical;
3. replace payment/claim states from enterprise systems;
4. bypass trust and consent decisions.

## Data Flow Rule

Care transaction capture should produce direct care value first; reporting/analytics should consume transaction outputs rather than duplicate provider entry burdens.
