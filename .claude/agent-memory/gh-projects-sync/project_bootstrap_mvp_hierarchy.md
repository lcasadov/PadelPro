---
name: bootstrap-mvp-hierarchy-sync
description: EP-AUTH/FT-AUTH-LOCAL/Stories/Tasks hierarchy created in GitHub for bootstrap-mvp change (issues #90-121, all Done)
metadata:
  type: project
---

Bootstrap-mvp change fully populated in GitHub Project #1 (lcasadov/PadelPro) with a 4-level hierarchy.

**Structure created (2026-05-24):**
- #90 EP-AUTH · Autenticación y gestión de identidad (type:epic)
  - #91 FT-AUTH-LOCAL · Login y registro con email + contraseña (type:feature, parent #90)
    - #92 ST-AUTH-LOCAL-R1 · Registro de usuario con email único (type:story)
    - #93 ST-AUTH-LOCAL-R2 · Login con credenciales válidas (type:story)
    - #94 ST-AUTH-LOCAL-R3 · Emisión y validación de JWT (type:story)
    - #95 ST-AUTH-LOCAL-R4 · Rate limiting de endpoints públicos (type:story)
    - #96 ST-AUTH-LOCAL-R5 · Auditoría de intentos de login (type:story)
    - Tasks T-009..T-032 as #97..#121 (type:task, each with Parent: #story in body)

All 32 items added to Project v2 PVT_kwHOAGwvnc4BWZD6 and set to status Done.
Implemented in PR: https://github.com/lcasadov/PadelPro/pull/89

**Why:** Traceability between OpenSpec change bootstrap-mvp and GitHub Project board.
**How to apply:** When next change is implemented, follow same EP/FT/ST/Task structure. Use issue #90 as the existing EP-AUTH epic parent for auth-related features.
