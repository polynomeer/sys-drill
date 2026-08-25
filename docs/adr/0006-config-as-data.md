---
status: accepted
---

# Grading scripts and prompt templates live in the database, not the filesystem

Build Mode's per-stage grading scripts (`build_stages.test_script`) and the AI evaluator's prompt bodies (`prompt_templates.template_body`) could both have been files in the repo, loaded by path at runtime — the more common pattern. Instead both are stored as full text content in DB columns: grading scripts are seeded via Flyway migrations (dollar-quoted SQL), and prompt templates are versioned rows with an `active` flag and an admin API (`POST /admin/prompt-templates/{id}/activate`) to switch versions.

This makes both independently versionable and swappable without a deploy — a prompt template can be rolled back by flipping `active`; a grading script's history lives in the same migration history as the schema it grades against. The trade-off is real: editing a test script means editing a SQL migration or a DB row, not opening a `.py` file. `challenges/*/stages/*.py` files exist in the repo purely as human-readable mirrors for template-repo users, and must be kept in sync with the seeded `test_script` values by hand — there's no automated check that they match.
