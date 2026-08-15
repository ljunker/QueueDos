#!/usr/bin/env bash

set -euo pipefail

readonly image="kryptikker/queuedos"
readonly builder="queuedos-builder"

usage() {
  cat <<'EOF'
Usage: scripts/publish-docker.sh [--dry-run]

Build and push the committed QueueDos version for linux/amd64 and linux/arm64.
The version is read from VERSION and published as latest, v<VERSION>, and the
current short Git commit SHA.

Options:
  --dry-run  Print the planned builder setup and build command without Docker changes.
  -h, --help Show this help.
EOF
}

validate_version() {
  local candidate="$1"
  local prerelease
  local identifier
  local identifiers=()

  if [[ ! "$candidate" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-([0-9A-Za-z-]+)(\.[0-9A-Za-z-]+)*)?$ ]]; then
    return 1
  fi

  if [[ "$candidate" == *-* ]]; then
    prerelease="${candidate#*-}"
    IFS='.' read -r -a identifiers <<< "$prerelease"
    for identifier in "${identifiers[@]}"; do
      if [[ "$identifier" =~ ^[0-9]+$ && ${#identifier} -gt 1 && "$identifier" == 0* ]]; then
        return 1
      fi
    done
  fi
}

print_command() {
  printf '  '
  printf '%q ' "$@"
  printf '\n'
}

main() {
  local dry_run=false

  case "${1:-}" in
    "") ;;
    --dry-run) dry_run=true ;;
    -h|--help)
      usage
      return 0
      ;;
    *)
      usage >&2
      return 2
      ;;
  esac

  if (( $# > 1 )); then
    usage >&2
    return 2
  fi

  command -v git >/dev/null 2>&1 || { echo "git is required." >&2; return 1; }

  local script_directory
  local repository_root
  local version_file
  local version
  local commit_sha
  local worktree_status
  local version_tag
  local sha_tag
  local build_command

  script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
  repository_root="$(cd -- "$script_directory/.." && pwd)"
  version_file="$repository_root/VERSION"

  cd "$repository_root"
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 || { echo "Not inside a Git worktree." >&2; return 1; }
  [[ -f "$version_file" ]] || { echo "VERSION is missing." >&2; return 1; }

  version="$(<"$version_file")"
  version="${version%$'\r'}"
  if ! validate_version "$version"; then
    echo "VERSION must contain SemVer without a leading 'v' or build metadata, but was '$version'." >&2
    return 1
  fi

  commit_sha="$(git rev-parse --short=8 HEAD)"
  worktree_status="$(git status --porcelain --untracked-files=normal)"
  version_tag="$image:v$version"
  sha_tag="$image:$commit_sha"
  build_command=(
    docker buildx build
    --builder "$builder"
    --platform linux/amd64,linux/arm64
    --build-arg "APP_VERSION=$version"
    --build-arg "VCS_REF=$commit_sha"
    -t "$image:latest"
    -t "$version_tag"
    -t "$sha_tag"
    --push
    .
  )

  if [[ "$dry_run" == true ]]; then
    if [[ -n "$worktree_status" ]]; then
      echo "Warning: a real publish would stop because the Git worktree is not clean." >&2
    fi
    if ! git ls-files --error-unmatch -- VERSION >/dev/null 2>&1; then
      echo "Warning: a real publish would stop because VERSION is not committed." >&2
    fi
    echo "Dry run for QueueDos v$version ($commit_sha)"
    echo "Builder: use '$builder'; create it with the docker-container driver if missing; bootstrap it."
    print_command "${build_command[@]}"
    return 0
  fi

  [[ -z "$worktree_status" ]] || {
    echo "Refusing to publish from a dirty Git worktree. Commit VERSION and all release changes first." >&2
    return 1
  }
  git ls-files --error-unmatch -- VERSION >/dev/null 2>&1 || {
    echo "Refusing to publish because VERSION is not committed." >&2
    return 1
  }
  command -v docker >/dev/null 2>&1 || { echo "docker is required." >&2; return 1; }

  if docker buildx inspect "$builder" >/dev/null 2>&1; then
    docker buildx use "$builder"
  else
    docker buildx create --name "$builder" --driver docker-container --use
  fi
  docker buildx inspect --bootstrap "$builder"

  echo "Publishing QueueDos v$version ($commit_sha) to $image."
  "${build_command[@]}"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
