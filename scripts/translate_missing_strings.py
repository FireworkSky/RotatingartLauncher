#!/usr/bin/env python3
"""
Report missing Android string resources across locale files.

Examples:
    python3 scripts/translate_missing_strings.py
    python3 scripts/translate_missing_strings.py --project app
    python3 scripts/translate_missing_strings.py --project shared
    python3 scripts/translate_missing_strings.py --show-missing 20
    python3 scripts/translate_missing_strings.py --format json
"""

from __future__ import annotations

import argparse
import glob
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ENTRY_TAGS = {"string", "plurals", "string-array"}
PROJECT_CONFIG = {
    "app": {
        "base": "app/src/main/res/values/strings.xml",
        "locale_glob": "app/src/main/res/values-*/strings.xml",
    },
    "shared": {
        "base": "shared/src/commonMain/composeResources/values/strings.xml",
        "locale_glob": "shared/src/commonMain/composeResources/values-*/strings.xml",
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Report missing locale entries from base Android strings.xml"
    )
    parser.add_argument(
        "--project",
        choices=["all", "app", "shared"],
        default="all",
        help="Project(s) to scan (default: %(default)s)",
    )
    parser.add_argument(
        "--base",
        default="",
        help="Custom base strings.xml; when set, overrides --project defaults",
    )
    parser.add_argument(
        "--locale-glob",
        default="",
        help="Custom locale glob; when set, overrides --project defaults",
    )
    parser.add_argument(
        "--locales",
        default="",
        help="Comma-separated locale folders to process (e.g. values-es,values-ru)",
    )
    parser.add_argument(
        "--show-missing",
        type=int,
        default=0,
        help="Show first N missing keys per locale (0 = hidden, -1 = show all)",
    )
    parser.add_argument(
        "--format",
        choices=["text", "json"],
        default="text",
        help="Output format (default: %(default)s)",
    )
    parser.add_argument(
        "--fail-on-missing",
        action="store_true",
        help="Exit with code 2 if any locale has missing keys",
    )
    return parser.parse_args()


def load_entries(xml_path: Path) -> tuple[list[str], dict[str, str], list[str]]:
    tree = ET.parse(xml_path)
    root = tree.getroot()
    if root.tag != "resources":
        raise ValueError(f"{xml_path} root is not <resources>")

    order: list[str] = []
    entries: dict[str, str] = {}
    duplicate_keys: list[str] = []

    for child in root:
        if child.tag not in ENTRY_TAGS:
            continue
        name = child.attrib.get("name")
        if not name:
            continue
        if name in entries:
            duplicate_keys.append(name)
            continue
        order.append(name)
        entries[name] = child.tag
    return order, entries, duplicate_keys


def resolve_locale_files(locale_glob: str, locale_filter: set[str]) -> list[Path]:
    paths = [Path(path) for path in glob.glob(locale_glob)]
    paths.sort()
    if locale_filter:
        paths = [path for path in paths if path.parent.name in locale_filter]
    return paths


def resolve_targets(args: argparse.Namespace) -> list[dict[str, str]]:
    # Custom mode keeps backward compatibility for one-off paths.
    if args.base or args.locale_glob:
        if not args.base or not args.locale_glob:
            raise ValueError("--base and --locale-glob must be provided together")
        return [
            {
                "project": "custom",
                "base": args.base,
                "locale_glob": args.locale_glob,
            }
        ]

    if args.project == "all":
        selected = ["app", "shared"]
    else:
        selected = [args.project]
    return [
        {
            "project": project,
            "base": PROJECT_CONFIG[project]["base"],
            "locale_glob": PROJECT_CONFIG[project]["locale_glob"],
        }
        for project in selected
    ]


def missing_preview(missing: list[str], limit: int) -> list[str]:
    if limit == 0:
        return []
    if limit < 0:
        return missing
    return missing[:limit]


def print_text_report(results: list[dict], total_missing: int, show_missing: int) -> None:
    for result in results:
        print(
            f"[{result['project']}:{result['locale']}] missing={result['missing_count']} "
            f"extra={result['extra_count']} "
            f"type_mismatch={result['type_mismatch_count']} "
            f"duplicates={result['duplicate_count']}"
        )

        preview = missing_preview(result["missing_keys"], show_missing)
        if preview:
            print("  missing_keys:")
            for key in preview:
                print(f"    - {key}")
        print()
    print(f"Total missing across locales: {total_missing}")


def print_json_report(results: list[dict], total_missing: int) -> None:
    payload = {
        "total_missing": total_missing,
        "locales": results,
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))


def main() -> int:
    args = parse_args()
    try:
        targets = resolve_targets(args)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 1

    results: list[dict] = []
    total_missing = 0
    locale_filter = {item.strip() for item in args.locales.split(",") if item.strip()}

    for target in targets:
        project = target["project"]
        base_path = Path(target["base"])
        if not base_path.exists():
            print(f"Base strings file not found: {base_path}", file=sys.stderr)
            return 1

        locale_files = resolve_locale_files(target["locale_glob"], locale_filter)
        if not locale_files:
            print(
                f"No locale files matched for project '{project}' "
                f"with glob '{target['locale_glob']}'.",
                file=sys.stderr,
            )
            return 1

        base_order, base_entries, base_duplicates = load_entries(base_path)
        if base_duplicates:
            print(
                f"Warning: duplicate keys in base file ({base_path}) "
                f"({len(base_duplicates)}): {', '.join(base_duplicates)}",
                file=sys.stderr,
            )

        for locale_file in locale_files:
            _, locale_entries, locale_duplicates = load_entries(locale_file)
            missing_keys = [key for key in base_order if key not in locale_entries]
            extra_keys = [key for key in locale_entries if key not in base_entries]
            type_mismatch = [
                key
                for key in locale_entries
                if key in base_entries and locale_entries[key] != base_entries[key]
            ]

            result = {
                "project": project,
                "locale": locale_file.parent.name,
                "path": str(locale_file),
                "base_path": str(base_path),
                "missing_count": len(missing_keys),
                "extra_count": len(extra_keys),
                "type_mismatch_count": len(type_mismatch),
                "duplicate_count": len(locale_duplicates),
                "missing_keys": missing_keys,
                "extra_keys": extra_keys,
                "type_mismatch_keys": type_mismatch,
                "duplicate_keys": locale_duplicates,
            }
            total_missing += result["missing_count"]
            results.append(result)

    if args.format == "json":
        print_json_report(results, total_missing)
    else:
        print_text_report(results, total_missing, args.show_missing)

    if args.fail_on_missing and total_missing > 0:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
