#!/usr/bin/env bash
set -euo pipefail

# Official YSM is a test input only; never publish or embed this binary.
target="${1:?Fabric target required}"
case "$target" in
  fabric-1.20.1)
    minecraft=1.20.1
    version=LF0NYrRj
    sha=ed2b5a6fc97df098ef7e731c12f2367ebec2612a2a08b72bb958f508b5802e82858dc406f6b6cc4f99406988aabd6f2bd929a459afd1d101bdd2fdfd125ab1be
    ;;
  fabric-1.21.1)
    minecraft=1.21.1
    version=eGOUtHWJ
    sha=c5125c05c9d681ecb38ed7fca4a7915c8af65f90927393b84ab7e0494eda967c3398265c44ac15c2c25188bc2ee16b1311f5cd09848f1d38ad0846d526340ed1
    ;;
  *) echo "Unsupported YSM compatibility test target: $target" >&2; exit 1 ;;
esac
mkdir -p libs/compatibility
file="libs/compatibility/ysm-2.6.5-fabric+mc${minecraft}-release.jar"
curl --fail --location --retry 3 --connect-timeout 30 \
  "https://cdn.modrinth.com/data/86xjpqqS/versions/${version}/ysm-2.6.5-fabric%2Bmc${minecraft}-release.jar" \
  --output "${file}.part"
echo "$sha  ${file}.part" | sha512sum --check
mv "${file}.part" "$file"
