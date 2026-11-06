#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# setup-signing-secrets.sh
#
# Interactive helper that:
#   1. Generates a NEW release keystore (if you don't have one)
#   2. OR uses an EXISTING keystore
#   3. Encodes it to base64
#   4. Presents each GitHub Actions secret one-by-one for easy copy-paste
#
# Usage:
#   chmod +x scripts/setup-signing-secrets.sh
#   ./scripts/setup-signing-secrets.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# Colors (no-op if not a terminal)
if [ -t 1 ]; then
  BOLD='\033[1m'    DIM='\033[2m'
  GREEN='\033[0;32m' CYAN='\033[0;36m'
  YELLOW='\033[0;33m' RED='\033[0;31m' MAGENTA='\033[0;35m'
  RESET='\033[0m'
else
  BOLD='' DIM='' GREEN='' CYAN='' YELLOW='' RED='' MAGENTA='' RESET=''
fi

divider() { echo -e "\n${DIM}────────────────────────────────────────────────────────────${RESET}\n"; }
header()  { echo -e "\n${BOLD}${CYAN}  $1${RESET}\n"; }
success() { echo -e "  ${GREEN}✓${RESET} $1"; }
warn()    { echo -e "  ${YELLOW}⚠${RESET} $1"; }
err()     { echo -e "  ${RED}✗${RESET} $1"; }
info()    { echo -e "  ${MAGENTA}→${RESET} $1"; }

pause_for_copy() {
  echo ""
  echo -e "  ${DIM}Press ENTER when you've copied this to GitHub...${RESET}"
  read -r
}

# ── Welcome ─────────────────────────────────────────────────────────────────
clear 2>/dev/null || true
echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}${CYAN}║     Android Signing Keystore & GitHub Secrets Setup         ║${RESET}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "  This script will:"
echo -e "    ${BOLD}1.${RESET} Generate a new keystore  ${DIM}(or use an existing one)${RESET}"
echo -e "    ${BOLD}2.${RESET} Encode it for GitHub Actions"
echo -e "    ${BOLD}3.${RESET} Walk you through adding all 4 secrets to GitHub"
echo ""
echo -e "  ${DIM}GitHub repo → Settings → Secrets and variables → Actions${RESET}"

divider

# ── Check prerequisites ─────────────────────────────────────────────────────
if ! command -v keytool &>/dev/null; then
  err "keytool not found. It's required to generate a keystore."
  echo ""
  echo -e "  ${DIM}Install a JDK to get keytool:${RESET}"
  echo -e "    macOS:   ${BOLD}brew install openjdk${RESET}"
  echo -e "    Ubuntu:  ${BOLD}sudo apt install default-jdk${RESET}"
  echo -e "    Windows: Download Adoptium JDK from https://adoptium.net"
  echo ""
  exit 1
fi
success "keytool found: $(keytool 2>&1 | head -1)"

# ── Step 0: Generate or use existing ────────────────────────────────────────
header "Do you already have a keystore file?"

echo -e "  ${BOLD}[1]${RESET} Generate a ${BOLD}new${RESET} keystore ${DIM}(recommended for first-time setup)${RESET}"
echo -e "  ${BOLD}[2]${RESET} I ${BOLD}already have${RESET} a .jks / .keystore file"
echo ""
echo -ne "  ${BOLD}Choose 1 or 2 > ${RESET}"
read -r CHOICE

KEYSTORE_FILE=""
KEY_ALIAS=""
KEYSTORE_PASSWORD=""
KEY_PASSWORD=""

if [[ "$CHOICE" == "2" ]]; then
  # ── Use existing keystore ───────────────────────────────────────────────
  divider
  header "Enter path to your existing keystore"

  while true; do
    echo -ne "  ${BOLD}> ${RESET}"
    read -r KEYSTORE_FILE
    KEYSTORE_FILE="${KEYSTORE_FILE/#\~/$HOME}"

    if [ ! -f "$KEYSTORE_FILE" ]; then
      err "File not found: $KEYSTORE_FILE"
      continue
    fi
    FILE_SIZE=$(wc -c < "$KEYSTORE_FILE")
    if [ "$FILE_SIZE" -lt 100 ]; then
      err "File is only $FILE_SIZE bytes — doesn't look like a valid keystore."
      continue
    fi
    success "Found: $KEYSTORE_FILE ($FILE_SIZE bytes)"
    break
  done

  divider
  header "Keystore password"
  echo -ne "  ${BOLD}> ${RESET}"
  read -rs KEYSTORE_PASSWORD
  echo ""

  header "Key alias"
  echo -e "  ${DIM}(e.g. 'release', 'upload', 'my-key-alias')${RESET}"
  echo -ne "  ${BOLD}> ${RESET}"
  read -r KEY_ALIAS

  header "Key password"
  echo -e "  ${DIM}(may be the same as keystore password)${RESET}"
  echo -ne "  ${BOLD}> ${RESET}"
  read -rs KEY_PASSWORD
  echo ""

else
  # ── Generate new keystore ───────────────────────────────────────────────
  divider
  header "Generating a new release keystore"

  # ── Output path ──────────────────────────────────────────────────────
  echo -e "  ${DIM}Where should the keystore be saved?${RESET}"
  echo -e "  ${DIM}(default: ./release-key.jks — keep this file SAFE and NEVER commit it)${RESET}"
  echo -ne "  ${BOLD}> ${RESET}"
  read -r KEYSTORE_FILE
  if [ -z "$KEYSTORE_FILE" ]; then
    KEYSTORE_FILE="./release-key.jks"
  fi

  if [ -f "$KEYSTORE_FILE" ]; then
    warn "File already exists: $KEYSTORE_FILE"
    echo -e "  ${YELLOW}Overwrite? (y/N)${RESET}"
    echo -ne "  ${BOLD}> ${RESET}"
    read -r OVERWRITE
    if [[ ! "$OVERWRITE" =~ ^[Yy]$ ]]; then
      echo -e "  ${DIM}Aborted.${RESET}"
      exit 0
    fi
  fi

  # ── Key alias ────────────────────────────────────────────────────────
  echo ""
  echo -e "  ${DIM}Key alias (name for the signing key):${RESET}"
  echo -e "  ${DIM}(default: 'release')${RESET}"
  echo -ne "  ${BOLD}> ${RESET}"
  read -r KEY_ALIAS
  if [ -z "$KEY_ALIAS" ]; then
    KEY_ALIAS="release"
  fi

  # ── Keystore password ────────────────────────────────────────────────
  echo ""
  echo -e "  ${DIM}Keystore password (min 6 chars, remember this!):${RESET}"
  while true; do
    echo -ne "  ${BOLD}> ${RESET}"
    read -rs KEYSTORE_PASSWORD
    echo ""
    if [ ${#KEYSTORE_PASSWORD} -lt 6 ]; then
      err "Password must be at least 6 characters. Try again."
      continue
    fi
    break
  done

  # ── Key password ─────────────────────────────────────────────────────
  echo ""
  echo -e "  ${DIM}Key password (can be same as keystore password):${RESET}"
  echo -e "  ${DIM}(press ENTER to use the same password)${RESET}"
  echo -ne "  ${BOLD}> ${RESET}"
  read -rs KEY_PASSWORD
  echo ""
  if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
    success "Using keystore password as key password"
  fi

  # ── Distinguished name ───────────────────────────────────────────────
  echo ""
  echo -e "  ${DIM}Your name or organization for the certificate:${RESET}"
  echo -e "  ${DIM}(default: 'Curio App')${RESET}"
  echo -ne "  ${BOLD}> ${RESET}"
  read -r CERT_CN
  if [ -z "$CERT_CN" ]; then
    CERT_CN="Curio App"
  fi

  divider
  header "Generating keystore..."

  keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 36500 \
    -alias "$KEY_ALIAS" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=$CERT_CN, OU=Development, O=Curio, L=Unknown, ST=Unknown, C=US" \
    2>&1 | while IFS= read -r line; do
      echo -e "  ${DIM}$line${RESET}"
    done

  if [ ! -f "$KEYSTORE_FILE" ]; then
    err "Keystore generation failed. File not created."
    exit 1
  fi

  echo ""
  success "Keystore generated: $KEYSTORE_FILE"
  echo ""
  echo -e "  ${RED}${BOLD}╔══════════════════════════════════════════════════════════════╗${RESET}"
  echo -e "  ${RED}${BOLD}║  ⚠  BACK UP THIS KEYSTORE FILE AND REMEMBER YOUR PASSWORDS ║${RESET}"
  echo -e "  ${RED}${BOLD}║                                                              ║${RESET}"
  echo -e "  ${RED}${BOLD}║  If you lose this file or passwords, you CANNOT update your  ║${RESET}"
  echo -e "  ${RED}${BOLD}║  app on the Play Store. Google won't help you recover it.    ║${RESET}"
  echo -e "  ${RED}${BOLD}║                                                              ║${RESET}"
  echo -e "  ${RED}${BOLD}║  Store backups in:                                           ║${RESET}"
  echo -e "  ${RED}${BOLD}║    • Password manager (1Password, Bitwarden, etc.)           ║${RESET}"
  echo -e "  ${RED}${BOLD}║    • Encrypted cloud storage                                 ║${RESET}"
  echo -e "  ${RED}${BOLD}║    • Offline USB drive                                       ║${RESET}"
  echo -e "  ${RED}${BOLD}╚══════════════════════════════════════════════════════════════╝${RESET}"
  echo ""

  # Add to .gitignore if not already there
  if [ -f ".gitignore" ]; then
    if ! grep -qF "$KEYSTORE_FILE" .gitignore; then
      echo "$KEYSTORE_FILE" >> .gitignore
      success "Added $KEYSTORE_FILE to .gitignore"
    fi
  fi
  if [ -f ".gitignore" ]; then
    if ! grep -qF "release-key.jks" .gitignore; then
      echo "release-key.jks" >> .gitignore
      success "Added release-key.jks to .gitignore"
    fi
  fi
fi

divider

# ── Verify credentials ──────────────────────────────────────────────────────
header "Verifying credentials..."

if keytool -list -keystore "$KEYSTORE_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -alias "$KEY_ALIAS" &>/dev/null; then
  success "Keystore and alias verified!"

  # Show certificate fingerprint
  echo ""
  info "Certificate fingerprint:"
  keytool -list -v -keystore "$KEYSTORE_FILE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -alias "$KEY_ALIAS" 2>/dev/null | grep -E "(SHA-256|SHA1|Alias|Valid from)" | while IFS= read -r line; do
    echo -e "    ${DIM}$line${RESET}"
  done
else
  err "Verification failed — wrong password or alias."
  echo -e "  ${DIM}Run keytool -list -keystore $KEYSTORE_FILE to debug.${RESET}"
  exit 1
fi

divider

# ── Encode keystore ─────────────────────────────────────────────────────────
header "Encoding keystore to base64..."

if base64 --help 2>&1 | grep -q "\-w"; then
  KEYSTORE_BASE64=$(base64 -w 0 "$KEYSTORE_FILE")
else
  KEYSTORE_BASE64=$(base64 -b 0 "$KEYSTORE_FILE")
fi

BASE64_LENGTH=${#KEYSTORE_BASE64}
success "Encoded: $BASE64_LENGTH characters"

divider

# ── Present secrets ─────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}${CYAN}║     Add These 4 Secrets to GitHub (one at a time)           ║${RESET}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "  ${DIM}For each one:${RESET}"
echo -e "    1. Open GitHub repo → Settings → Secrets and variables → Actions"
echo -e "    2. Click ${BOLD}New repository secret${RESET}"
echo -e "    3. Paste the ${BOLD}Name${RESET} and ${BOLD}Value${RESET} below"
echo -e "    4. Click ${BOLD}Add secret${RESET}"

# Save all to /tmp for convenience
SECRET_DIR="/tmp/github-secrets-$$"
mkdir -p "$SECRET_DIR"

# ── Secret 1 ────────────────────────────────────────────────────────────────
divider
echo -e "${BOLD}  Secret 1/4: KEYSTORE_BASE64${RESET}"
echo ""
echo -e "  ${BOLD}Name:${RESET}   KEYSTORE_BASE64"
echo ""
echo -e "  ${BOLD}Value:${RESET}  ${DIM}(very long — saved to file below)${RESET}"
echo ""
echo "$KEYSTORE_BASE64" > "$SECRET_DIR/KEYSTORE_BASE64.txt"
echo -e "  ${GREEN}File saved:${RESET}  $SECRET_DIR/KEYSTORE_BASE64.txt"
echo ""
echo -e "  ${DIM}Copy to clipboard:${RESET}"
if command -v pbcopy &>/dev/null; then
  echo -e "    ${BOLD}cat $SECRET_DIR/KEYSTORE_BASE64.txt | pbcopy${RESET}"
  echo -e "    ${DIM}(or just run the command below to copy it now)${RESET}"
  echo ""
  echo -ne "  ${BOLD}Copy to clipboard now? (Y/n) > ${RESET}"
  read -r COPY_NOW
  if [[ ! "$COPY_NOW" =~ ^[Nn]$ ]]; then
    cat "$SECRET_DIR/KEYSTORE_BASE64.txt" | pbcopy
    success "Copied to clipboard! Paste into GitHub."
  fi
elif command -v xclip &>/dev/null; then
  echo -e "    ${BOLD}cat $SECRET_DIR/KEYSTORE_BASE64.txt | xclip -selection clipboard${RESET}"
elif command -v xsel &>/dev/null; then
  echo -e "    ${BOLD}cat $SECRET_DIR/KEYSTORE_BASE64.txt | xsel --clipboard${RESET}"
else
  echo -e "    ${BOLD}cat $SECRET_DIR/KEYSTORE_BASE64.txt${RESET}"
  echo -e "    ${DIM}(manually select and copy the output)${RESET}"
fi
pause_for_copy

# ── Secret 2 ────────────────────────────────────────────────────────────────
divider
echo -e "${BOLD}  Secret 2/4: KEYSTORE_PASSWORD${RESET}"
echo ""
echo -e "  ${BOLD}Name:${RESET}   KEYSTORE_PASSWORD"
echo -e "  ${BOLD}Value:${RESET}  $KEYSTORE_PASSWORD"
echo "$KEYSTORE_PASSWORD" > "$SECRET_DIR/KEYSTORE_PASSWORD.txt"
pause_for_copy

# ── Secret 3 ────────────────────────────────────────────────────────────────
divider
echo -e "${BOLD}  Secret 3/4: KEY_ALIAS${RESET}"
echo ""
echo -e "  ${BOLD}Name:${RESET}   KEY_ALIAS"
echo -e "  ${BOLD}Value:${RESET}  $KEY_ALIAS"
echo "$KEY_ALIAS" > "$SECRET_DIR/KEY_ALIAS.txt"
pause_for_copy

# ── Secret 4 ────────────────────────────────────────────────────────────────
divider
echo -e "${BOLD}  Secret 4/4: KEY_PASSWORD${RESET}"
echo ""
echo -e "  ${BOLD}Name:${RESET}   KEY_PASSWORD"
echo -e "  ${BOLD}Value:${RESET}  $KEY_PASSWORD"
echo "$KEY_PASSWORD" > "$SECRET_DIR/KEY_PASSWORD.txt"
pause_for_copy

# ── Done ────────────────────────────────────────────────────────────────────
divider
echo ""
echo -e "${BOLD}${GREEN}╔══════════════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}${GREEN}║                    All secrets ready!                        ║${RESET}"
echo -e "${BOLD}${GREEN}╚══════════════════════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "  ${GREEN}✓${RESET} Keystore: $KEYSTORE_FILE"
echo -e "  ${GREEN}✓${RESET} Alias:    $KEY_ALIAS"
echo -e "  ${GREEN}✓${RESET} Validity: 100 years"
echo ""
echo -e "  ${DIM}Next:${RESET}"
echo -e "    1. Make sure all 4 secrets are added to GitHub"
echo -e "    2. Push a tag to trigger a signed release build:"
echo -e "       ${BOLD}git tag v0.1.0 && git push origin v0.1.0${RESET}"
echo ""
echo -e "  ${DIM}Secret files saved to: $SECRET_DIR/${RESET}"
echo -e "  ${DIM}Keystore file: $KEYSTORE_FILE ${RED}(NEVER commit this!)${RESET}"
echo -e "  ${DIM}Delete secrets when done: rm -rf $SECRET_DIR${RESET}"
echo ""
echo -e "  ${DIM}The release workflow will now fail if any secret is missing,${RESET}"
echo -e "  ${DIM}and will verify the APK is signed with YOUR key (not debug).${RESET}"
echo ""
