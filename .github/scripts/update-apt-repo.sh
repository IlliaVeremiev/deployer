#!/usr/bin/env bash
# Usage: update-apt-repo.sh <channel> <deb-file>
#   channel:  snapshot | stable
#   deb-file: path to the .deb file to publish
set -euo pipefail

CHANNEL="$1"
DEB_FILE="$2"

git config --global user.email "github-actions[bot]@users.noreply.github.com"
git config --global user.name "github-actions[bot]"

REPO_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"

if git ls-remote --heads "$REPO_URL" gh-pages | grep -q gh-pages; then
  git clone --depth 1 --branch gh-pages "$REPO_URL" apt-repo
else
  mkdir apt-repo
  cd apt-repo
  git init
  git remote add origin "$REPO_URL"
  git checkout -b gh-pages
  cd ..
fi

cd apt-repo
mkdir -p "pool/$CHANNEL" "dists/$CHANNEL/main/binary-amd64"

# Snapshot channel keeps only the latest build
if [ "$CHANNEL" = "snapshot" ]; then
  rm -f pool/snapshot/*.deb
fi

cp "../$DEB_FILE" "pool/$CHANNEL/"

# Generate Packages index
apt-ftparchive packages "pool/$CHANNEL" > "dists/$CHANNEL/main/binary-amd64/Packages"
gzip -9c "dists/$CHANNEL/main/binary-amd64/Packages" > "dists/$CHANNEL/main/binary-amd64/Packages.gz"

# Generate Release file
apt-ftparchive \
  -o "APT::FTPArchive::Release::Origin=deployer" \
  -o "APT::FTPArchive::Release::Label=deployer" \
  -o "APT::FTPArchive::Release::Suite=$CHANNEL" \
  -o "APT::FTPArchive::Release::Codename=$CHANNEL" \
  -o "APT::FTPArchive::Release::Architectures=amd64" \
  -o "APT::FTPArchive::Release::Components=main" \
  release "dists/$CHANNEL" > "dists/$CHANNEL/Release"

git add -A
git diff --cached --quiet && echo "Nothing to commit" && exit 0
git commit -m "chore: update $CHANNEL apt repository"
git push origin gh-pages
