#!/usr/bin/env bash
set -euo pipefail

tag="${1:?release tag required}"
directory="${2:?release directory required}"
version="${tag#v}"
jars=(
  "ysmragdoll-${version}+mc1.20.1-forge-all.jar"
  "ysmragdoll-${version}+mc1.20.1-fabric-all.jar"
  "ysmragdoll-${version}+mc1.21.1-fabric-all.jar"
)
checksum="ysmragdoll-${version}-SHA256SUMS.txt"

for jar in "${jars[@]}"; do test -s "$directory/$jar"; done
test -s "$directory/$checksum"
test -s "$directory/release-notes.md"
(cd "$directory" && sha256sum --check "$checksum")

# Listing errors must fail closed rather than being mistaken for a missing release.
releases="$(gh release list --limit 1000 --json tagName)"
if ! jq -e --arg tag "$tag" 'any(.[]; .tagName == $tag)' <<< "$releases" >/dev/null; then
  flags=(--draft --verify-tag)
  if [[ "$version" == *-* ]]; then flags+=(--prerelease); fi
  gh release create "$tag" "${flags[@]}" \
    --title "YSM Ragdoll $version" \
    --notes-file "$directory/release-notes.md"
fi

metadata="$(gh release view "$tag" --json isDraft,assets)"
if ! jq -e '.isDraft' <<< "$metadata" >/dev/null; then
  echo "Refusing to modify an already published release: $tag" >&2
  exit 1
fi

for name in "${jars[@]}" "$checksum"; do
  if jq -e --arg name "$name" 'any(.assets[]; .name == $name)' <<< "$metadata" >/dev/null; then
    temporary="$(mktemp -d)"
    gh release download "$tag" --pattern "$name" --dir "$temporary"
    if ! cmp --silent "$directory/$name" "$temporary/$name"; then
      echo "Refusing to overwrite a different existing asset: $name" >&2
      exit 1
    fi
  else
    gh release upload "$tag" "$directory/$name"
  fi
done
echo "Draft release verified. Review it before publishing."
