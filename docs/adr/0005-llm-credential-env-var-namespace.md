---
status: accepted
---

# LLM credentials use LLM_ANTHROPIC_* env vars, not ANTHROPIC_*

The backend calls the Anthropic API for AI evaluation. The obvious env var names would be `ANTHROPIC_API_KEY`/`ANTHROPIC_BASE_URL` — but this project is developed inside Claude Code, which already sets `ANTHROPIC_BASE_URL` (and potentially other `ANTHROPIC_*` vars) in the shell for its own purposes. All LLM configuration is namespaced under `LLM_ANTHROPIC_*` (`LLM_ANTHROPIC_API_KEY`, `LLM_ANTHROPIC_BASE_URL`, `LLM_ANTHROPIC_MODEL`, `LLM_ANTHROPIC_MAX_TOKENS`) instead.

Using the standard names would make the backend silently inherit Claude Code's own environment values in local dev, pointing evaluation calls at the wrong base URL with no error at all. If a future engineer "cleans this up" back to `ANTHROPIC_API_KEY` for convention's sake, this bug returns — don't, without re-reading this.
