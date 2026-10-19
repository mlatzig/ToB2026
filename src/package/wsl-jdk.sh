#!/bin/sh
# ToB 2026, Phase 7c decision 4: installs Eclipse Temurin for Linux x64 into $HOME/jdks (no root needed) for the
# release build's Linux package. Run it once inside WSL, with the version of tob.release.jdk in pom.xml:
#   wsl -d Ubuntu --exec sh /mnt/d/<project>/src/package/wsl-jdk.sh 21.0.8+9
# Downloads the JDK and its SHA-256 file from the Adoptium releases on GitHub, checks the archive and unpacks it to
# $HOME/jdks/jdk-<version>. Does nothing if that JDK is already there.
set -eu
version="${1:?usage: wsl-jdk.sh VERSION, e.g. 21.0.8+9 (tob.release.jdk in pom.xml)}"
feature="${version%%.*}"
target="$HOME/jdks/jdk-$version"
if [ -x "$target/bin/jpackage" ]; then
    echo "Temurin $version is already installed: $target"
    exit 0
fi
file="OpenJDK${feature}U-jdk_x64_linux_hotspot_$(echo "$version" | tr '+' '_').tar.gz"
tag="jdk-$(echo "$version" | sed 's/+/%2B/')"
url="https://github.com/adoptium/temurin${feature}-binaries/releases/download/$tag/$file"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
echo "Downloading $url"
curl -fsSL -o "$work/$file" "$url"
curl -fsSL -o "$work/$file.sha256.txt" "$url.sha256.txt"
(cd "$work" && sha256sum -c "$file.sha256.txt")
mkdir -p "$HOME/jdks"
tar -xzf "$work/$file" -C "$HOME/jdks"
"$target/bin/java" -version
echo "Temurin $version installed: $target"
