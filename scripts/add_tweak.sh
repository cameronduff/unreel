#!/usr/bin/env bash
# ==============================================================================
# add_tweak.sh - Recursively Add Android Tweaks & Sync with AGENTS.md & Scripts
# ==============================================================================
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AGENTS_FILE="$REPO_DIR/AGENTS.md"
PROVISION_SCRIPT="$REPO_DIR/provision_dumbphone.sh"
REVERT_SCRIPT="$REPO_DIR/revert_dumbphone.sh"

ADB="${ADB:-$(command -v adb || echo /mnt/d/platform-tools/adb.exe)}"

usage() {
    cat <<EOF
Usage: $0 <command> [options]

Commands:
  package   Add a package to be stripped or preserved
            Options: --pkg <id> --action <remove|preserve|restore> --category <cat> [--reason <why>]

  setting   Record a system/secure/global Android setting tweak
            Options: --ns <system|secure|global> --key <key> --val <value> --desc <description>

  note      Recursively append a custom engineering note or discovery to AGENTS.md
            Options: --text "<note markdown>"

  audit     Inspect connected Android device for 3rd-party packages and compare with AGENTS.md

Examples:
  $0 package --pkg com.reddit.frontpage --action remove --category "Social Media" --reason "Infinite scroll"
  $0 setting --ns secure --key doze_enabled --val 1 --desc "Enable ambient display doze"
  $0 note --text "Discovered Android 11+ permission grant for notification listener."
  $0 audit
EOF
    exit 1
}

TIMESTAMP="$(date '+%Y-%m-%d %H:%M:%S')"

cmd_package() {
    local pkg="" action="remove" category="General" reason=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --pkg) pkg="$2"; shift 2 ;;
            --action) action="$2"; shift 2 ;;
            --category) category="$2"; shift 2 ;;
            --reason) reason="$2"; shift 2 ;;
            *) echo "Unknown option $1"; usage ;;
        esac
    done

    if [[ -z "$pkg" ]]; then
        echo "Error: --pkg <package_name> is required."
        exit 1
    fi

    echo "Recording package tweak: $pkg (Action: $action, Category: $category)..."

    # Append to AGENTS.md Ledger
    cat >> "$AGENTS_FILE" <<EOF

- **[$TIMESTAMP] Package Update**: \`$pkg\`
  - **Action**: \`$action\`
  - **Category**: $category
  - **Rationale / Notes**: ${reason:-"None provided"}
EOF

    echo "Successfully appended package tweak to $AGENTS_FILE."
}

cmd_setting() {
    local ns="" key="" val="" desc=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --ns) ns="$2"; shift 2 ;;
            --key) key="$2"; shift 2 ;;
            --val) val="$2"; shift 2 ;;
            --desc) desc="$2"; shift 2 ;;
            *) echo "Unknown option $1"; usage ;;
        esac
    done

    if [[ -z "$ns" || -z "$key" || -z "$val" ]]; then
        echo "Error: --ns, --key, and --val are all required."
        exit 1
    fi

    echo "Recording setting tweak: settings put $ns $key $val..."

    # Append to AGENTS.md Ledger
    cat >> "$AGENTS_FILE" <<EOF

- **[$TIMESTAMP] Setting Tweak**: \`settings put $ns $key $val\`
  - **Namespace**: \`$ns\`
  - **Key**: \`$key\`
  - **Target Value**: \`$val\`
  - **Description**: ${desc:-"Custom UI/System tweak"}
EOF

    echo "Successfully appended setting tweak to $AGENTS_FILE."
}

cmd_note() {
    local text=""
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --text) text="$2"; shift 2 ;;
            *) echo "Unknown option $1"; usage ;;
        esac
    done

    if [[ -z "$text" ]]; then
        echo "Error: --text is required."
        exit 1
    fi

    cat >> "$AGENTS_FILE" <<EOF

- **[$TIMESTAMP] Discovery / Engineering Note**:
  - $text
EOF

    echo "Successfully appended note to $AGENTS_FILE."
}

cmd_audit() {
    echo "=== Running Device Package Audit via ADB ==="
    if ! timeout 5 "$ADB" get-state >/dev/null 2>&1; then
        echo "ADB device not responding or disconnected. Ensure phone is connected with USB debugging authorized."
        exit 1
    fi

    echo "Fetching currently installed third-party packages..."
    local installed
    installed="$("$ADB" shell pm list packages -3 --user 0 | sed 's/package://' | tr -d '\r')"

    echo "Installed 3rd-party packages on device:"
    echo "$installed"
    echo ""
    echo "Tip: Run '$0 package --pkg <name> --action remove --category <category>' to append new packages."
}

case "${1:-}" in
    package) shift; cmd_package "$@" ;;
    setting) shift; cmd_setting "$@" ;;
    note)    shift; cmd_note "$@" ;;
    audit)   shift; cmd_audit "$@" ;;
    *) usage ;;
esac
