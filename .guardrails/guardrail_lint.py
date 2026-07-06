#!/usr/bin/env python3
"""
Guardrail linter for Substrata platform.

Enforces hard constraints:
1. No third-party company/vendor names in code, UI, docs, or customer-facing surfaces
2. IP owner name restricted to LICENSE and NOTICE files only
3. Vendor-neutral references to external systems

Exits with non-zero status on violations (enforced in CI).
"""

import json
import os
import re
import sys
from pathlib import Path
from typing import List, Set, Tuple


def load_config(config_path: Path) -> dict:
    """Load guardrail configuration."""
    try:
        with open(config_path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"ERROR: Config file not found: {config_path}", file=sys.stderr)
        sys.exit(1)
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON in config: {e}", file=sys.stderr)
        sys.exit(1)


def get_tracked_files(root: Path) -> List[Path]:
    """Get all files tracked by git, excluding binary and generated files."""
    import subprocess
    
    try:
        result = subprocess.run(
            ["git", "ls-files"],
            cwd=root,
            capture_output=True,
            text=True,
            check=True
        )
        files = [root / line.strip() for line in result.stdout.splitlines() if line.strip()]
        
        # Filter to text files only
        text_files = []
        for f in files:
            if f.is_file() and is_text_file(f):
                text_files.append(f)
        
        return text_files
    except subprocess.CalledProcessError as e:
        print(f"ERROR: Failed to get tracked files: {e}", file=sys.stderr)
        sys.exit(1)


def is_text_file(path: Path) -> bool:
    """Check if file is likely a text file."""
    # Skip known binary extensions
    binary_extensions = {
        '.png', '.jpg', '.jpeg', '.gif', '.ico', '.pdf', '.zip',
        '.tar', '.gz', '.woff', '.woff2', '.ttf', '.eot', '.mp4',
        '.webm', '.ogg', '.mp3', '.pyc', '.so', '.dylib', '.dll'
    }
    
    if path.suffix.lower() in binary_extensions:
        return False
    
    # Try to read as text
    try:
        with open(path, 'r', encoding='utf-8') as f:
            f.read(1024)  # Read first 1KB
        return True
    except (UnicodeDecodeError, PermissionError):
        return False


def scan_file(
    file_path: Path,
    denylist: List[str],
    allowed_standards: List[str],
    owner_name: str,
    allowed_for_owner: Set[str],
    allowed_for_vendors: Set[str],
    root: Path
) -> List[Tuple[int, str, str]]:
    """
    Scan a file for violations.
    
    Returns list of (line_number, matched_term, violation_type) tuples.
    """
    violations = []
    relative_path = str(file_path.relative_to(root))
    
    # Check if file is allowed for owner name
    owner_allowed = relative_path in allowed_for_owner
    
    # Check if file is allowed for vendor names
    vendor_allowed = relative_path in allowed_for_vendors
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            for line_num, line in enumerate(f, start=1):
                # Check for owner name in disallowed locations
                if not owner_allowed and owner_name.lower() in line.lower():
                    violations.append((line_num, owner_name, "owner_name"))
                
                # Check for vendor names in disallowed locations
                if not vendor_allowed:
                    for term in denylist:
                        # Skip if it's an allowed standard
                        if term in allowed_standards:
                            continue
                        
                        # Case-insensitive search for whole words
                        pattern = r'\b' + re.escape(term) + r'\b'
                        if re.search(pattern, line, re.IGNORECASE):
                            violations.append((line_num, term, "vendor_name"))
    
    except Exception as e:
        print(f"WARNING: Could not scan {file_path}: {e}", file=sys.stderr)
    
    return violations


def main():
    """Main linter entry point."""
    # Find repo root
    root = Path(__file__).parent.parent.resolve()
    config_path = root / ".guardrails" / "config.json"
    
    # Load configuration
    config = load_config(config_path)
    denylist = config.get("denylist", [])
    allowed_standards = config.get("allowed_standards", [])
    owner_name = config.get("owner_name", "")
    owner_allowed_files = set(config.get("owner_allowed_files", []))
    vendor_allowed_files = set(config.get("allowed_files", []))
    
    if not denylist and not owner_name:
        print("WARNING: Empty denylist and no owner_name in config", file=sys.stderr)
    
    # Get all tracked files
    tracked_files = get_tracked_files(root)
    print(f"Scanning {len(tracked_files)} tracked files...", file=sys.stderr)
    
    # Scan all files
    all_violations = []
    for file_path in tracked_files:
        violations = scan_file(
            file_path,
            denylist,
            allowed_standards,
            owner_name,
            owner_allowed_files,
            vendor_allowed_files,
            root
        )
        
        if violations:
            all_violations.append((file_path, violations))
    
    # Report violations
    if all_violations:
        print("\n❌ GUARDRAIL VIOLATIONS DETECTED:\n", file=sys.stderr)
        
        for file_path, violations in all_violations:
            rel_path = file_path.relative_to(root)
            print(f"  {rel_path}:", file=sys.stderr)
            
            for line_num, term, vtype in violations:
                if vtype == "owner_name":
                    msg = f"    Line {line_num}: IP owner name '{term}' found (only allowed in LICENSE/NOTICE)"
                else:
                    msg = f"    Line {line_num}: Forbidden vendor name '{term}' found"
                print(msg, file=sys.stderr)
            print(file=sys.stderr)
        
        total = sum(len(v) for _, v in all_violations)
        print(f"Total violations: {total}\n", file=sys.stderr)
        print("Fix violations before proceeding. Do not weaken guardrails to pass.", file=sys.stderr)
        return 1
    
    print("✓ All guardrail checks passed", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
