#!/usr/bin/env bash
# Which Build language variants a pull request needs to grade end to end
# (BuildLanguageVariantsIntegrationTest — a full run is ~11 minutes of real sandbox grading).
#
# Usage: scripts/select-build-variants.sh <base-rev> [<head-rev>, default HEAD]
# Prints one of:
#   all             — something every variant depends on changed (sandbox images, the grading
#                     code, build seed migrations, the test itself): grade everything
#   <slug>,<slug>…  — only these challenges' files changed; the test adds its smoke set
#   smoke           — no challenge was touched: the test's smoke set (one per language) only
#
# The output goes to `./gradlew test -PbuildVariants=<output>`. Pushes to main don't use this
# and always grade every variant.
set -euo pipefail

base=${1:?usage: $0 <base-rev> [<head-rev>]}
head=${2:-HEAD}
cd "$(dirname "${BASH_SOURCE[0]}")/.."

changed=$(git diff --name-only "$base"..."$head")

shared_paths=(
  "backend/src/main/kotlin/com/sysdrill/backend/build/"
  "backend/src/main/resources/application.yml"
  "backend/src/test/kotlin/com/sysdrill/backend/build/BuildLanguageVariantsIntegrationTest.kt"
  "backend/build.gradle.kts"
  "sandbox/"
  "docker-compose.yml"
  "scripts/select-build-variants.sh"
)

slugs=()
while IFS= read -r path; do
  [ -n "$path" ] || continue
  for shared in "${shared_paths[@]}"; do
    if [[ "$path" == "$shared"* ]]; then
      echo all
      exit 0
    fi
  done
  # A migration that seeds or rewrites build stages can change any variant's test script.
  if [[ "$path" == backend/src/main/resources/db/migration/* ]] \
    && git show "$head:$path" 2>/dev/null | grep -qE "build_(challenges|stages)"; then
    echo all
    exit 0
  fi
  if [[ "$path" =~ ^challenges/([^/]+)/ ]] || [[ "$path" =~ ^backend/src/test/resources/build-solutions/([^/]+)/ ]]; then
    slugs+=("${BASH_REMATCH[1]}")
  fi
done <<< "$changed"

if [ ${#slugs[@]} -eq 0 ]; then
  echo smoke
else
  printf '%s\n' "${slugs[@]}" | sort -u | paste -sd, -
fi
