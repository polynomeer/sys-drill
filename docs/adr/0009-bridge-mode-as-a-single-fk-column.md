---
status: accepted
---

# Bridge Mode is a nullable FK column on Session, not a separate domain

docs/PRD.md calls Bridge Mode (Build → Design → Wargame in one continuous flow) the product's core differentiator, which might suggest it deserves its own aggregate, state machine, or table. Instead it's one nullable `sessions.build_submission_id` column (FK to `build_submissions`), plus a JSON snapshot (`reports.build_summary`) computed when a report is generated. No separate Bridge entity, table, or status field exists.

Every piece Bridge Mode needs — a completed Build submission, a Design/Wargame session, a report — already existed as an independent flow; the only new fact worth persisting is "this session followed from that Build submission." Modeling it as its own domain would have duplicated state that `Session` and `Report` already own. This keeps Bridge Mode's backend footprint to one migration; the "connectedness" is entirely a frontend/UX construction (the `BridgeProgress` stepper, the `/bridge` page) built on top of existing APIs, not a backend concept in its own right.
