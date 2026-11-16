#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_REPOSITORY:?}" "${RELEASE_VERSION:?}" "${GITHUB_RUN_NUMBER:?}"
readme_scripts_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readme_temp_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/release-readme.XXXXXX")"
trap 'rm -rf "$readme_temp_dir"' EXIT
readme_tag="v${RELEASE_VERSION}-build.${GITHUB_RUN_NUMBER}"
readme_branch="docs/release-readme-${readme_tag}"
readme_compare_url="https://github.com/${GITHUB_REPOSITORY}/compare/main...${readme_branch}?expand=1"

gh release view "$readme_tag" --repo "$GITHUB_REPOSITORY" --json body --jq .body > "$readme_temp_dir/notes.md"
git diff --quiet
git diff --cached --quiet
git fetch origin main:refs/remotes/origin/main
if git ls-remote --exit-code --heads origin "refs/heads/$readme_branch" >/dev/null; then
  git fetch origin "refs/heads/$readme_branch:refs/remotes/origin/$readme_branch"
  git switch --detach "origin/$readme_branch"
else
  git switch --detach origin/main
fi

python3 "$readme_scripts_dir/release_readme.py" --readme README.md \
  --notes "$readme_temp_dir/notes.md" --version "$RELEASE_VERSION" \
  --run-number "$GITHUB_RUN_NUMBER" --repository "$GITHUB_REPOSITORY"
if ! git diff --quiet -- README.md; then
  git config user.name 'github-actions[bot]'
  git config user.email '41898282+github-actions[bot]@users.noreply.github.com'
  git add README.md
  git commit -m "docs: update release information for $readme_tag"
fi
if git diff --quiet origin/main HEAD -- README.md; then
  echo 'README already matches the published release.'
  exit 0
fi

# Only the dedicated docs branch is pushed. Protected main is updated through review.
git push origin "HEAD:refs/heads/$readme_branch"
readme_pr_url="$(gh pr list --repo "$GITHUB_REPOSITORY" --head "$readme_branch" \
  --base main --state open --json url --jq '.[0].url // empty' 2> "$readme_temp_dir/pr-list-error.txt")" || {
  cat "$readme_temp_dir/pr-list-error.txt" >&2
  readme_pr_url=''
}
if [[ -z "$readme_pr_url" ]]; then
  printf 'Обновление README для опубликованного релиза %s.\n\n## Заметки релиза\n\nнет\n' \
    "$readme_tag" > "$readme_temp_dir/pr-body.md"
  if gh pr create --repo "$GITHUB_REPOSITORY" --base main --head "$readme_branch" \
    --title "docs: обновить README для $readme_tag" --body-file "$readme_temp_dir/pr-body.md" \
    > "$readme_temp_dir/pr-url.txt" 2> "$readme_temp_dir/pr-error.txt"; then
    readme_pr_url="$(cat "$readme_temp_dir/pr-url.txt")"
  else
    # Some repositories disallow PR creation by GITHUB_TOKEN. The branch and
    # review link remain usable; publication itself has already succeeded.
    cat "$readme_temp_dir/pr-error.txt" >&2
    echo "::notice::README update branch is ready. Create its pull request: $readme_compare_url"
    readme_pr_url="$readme_compare_url"
  fi
fi
printf '### Обновление README\n\nРелиз опубликован. Обновление README ожидает проверки: %s\n' \
  "$readme_pr_url" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
