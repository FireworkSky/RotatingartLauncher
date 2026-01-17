#!/usr/bin/env python3
"""
Script to parse strings.xml files for different locales and report missing fields.

This script:
1. Finds all strings.xml files in app/src/main/res/values*/ directories
2. Parses each XML file to extract string keys
3. Compares each locale against the base (values/strings.xml)
4. Reports missing keys for each locale
"""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Set


def parse_strings_xml(file_path: Path) -> Set[str]:
    """
    Parse a strings.xml file and return a set of all string keys.
    
    Args:
        file_path: Path to the strings.xml file
        
    Returns:
        Set of string keys found in the file
    """
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        
        keys = set()
        for string_elem in root.findall('.//string'):
            name_attr = string_elem.get('name')
            if name_attr:
                keys.add(name_attr)
        
        return keys
    except ET.ParseError as e:
        print(f"Error parsing {file_path}: {e}")
        return set()
    except FileNotFoundError:
        print(f"File not found: {file_path}")
        return set()


def find_locale_strings_files(base_dir: Path) -> Dict[str, Path]:
    """
    Find all strings.xml files in values*/ directories.
    
    Args:
        base_dir: Base directory (usually app/src/main/res)
        
    Returns:
        Dictionary mapping locale name to file path
    """
    locale_files = {}
    
    # Find all values* directories
    res_dir = base_dir / "res"
    if not res_dir.exists():
        print(f"Warning: {res_dir} does not exist")
        return locale_files
    
    for values_dir in res_dir.iterdir():
        if values_dir.is_dir() and values_dir.name.startswith('values'):
            strings_file = values_dir / "strings.xml"
            if strings_file.exists():
                # Extract locale name (e.g., "values-en" -> "en", "values" -> "default")
                locale_name = values_dir.name
                if locale_name == "values":
                    locale_name = "default"
                else:
                    # Extract locale code from "values-XX"
                    locale_name = locale_name.replace("values-", "")
                
                locale_files[locale_name] = strings_file
    
    return locale_files


def analyze_missing_strings(base_keys: Set[str], locale_keys: Set[str]) -> Set[str]:
    """
    Find keys that are in base but missing in locale.
    
    Args:
        base_keys: Set of keys from base file
        locale_keys: Set of keys from locale file
        
    Returns:
        Set of missing keys
    """
    return base_keys - locale_keys


def analyze_extra_strings(base_keys: Set[str], locale_keys: Set[str]) -> Set[str]:
    """
    Find keys that are in locale but not in base.
    
    Args:
        base_keys: Set of keys from base file
        locale_keys: Set of keys from locale file
        
    Returns:
        Set of extra keys
    """
    return locale_keys - base_keys


def format_report(
    locale_files: Dict[str, Path],
    base_keys: Set[str],
    all_locale_keys: Dict[str, Set[str]]
) -> str:
    """
    Format a comprehensive report of missing and extra strings.
    
    Args:
        locale_files: Dictionary mapping locale names to file paths
        base_keys: Set of keys from base file
        all_locale_keys: Dictionary mapping locale names to their key sets
        
    Returns:
        Formatted report string
    """
    report_lines = []
    report_lines.append("=" * 80)
    report_lines.append("Strings.xml Localization Analysis Report")
    report_lines.append("=" * 80)
    report_lines.append("")
    
    # Base file info
    report_lines.append(f"Base file (values/strings.xml):")
    report_lines.append(f"  Total strings: {len(base_keys)}")
    report_lines.append("")
    
    # Summary table
    report_lines.append("Summary:")
    report_lines.append("-" * 80)
    report_lines.append(f"{'Locale':<15} {'Total':<10} {'Missing':<10} {'Extra':<10} {'Complete':<10}")
    report_lines.append("-" * 80)
    
    summary_data = []
    for locale_name in sorted(locale_files.keys()):
        if locale_name == "default":
            continue
        
        locale_keys = all_locale_keys.get(locale_name, set())
        missing = analyze_missing_strings(base_keys, locale_keys)
        extra = analyze_extra_strings(base_keys, locale_keys)
        total = len(locale_keys)
        complete_pct = ((len(base_keys) - len(missing)) / len(base_keys) * 100) if base_keys else 0
        
        summary_data.append((locale_name, total, len(missing), len(extra), complete_pct))
        report_lines.append(
            f"{locale_name:<15} {total:<10} {len(missing):<10} {len(extra):<10} {complete_pct:>6.1f}%"
        )
    
    report_lines.append("")
    report_lines.append("=" * 80)
    report_lines.append("Detailed Missing Strings by Locale")
    report_lines.append("=" * 80)
    report_lines.append("")
    
    # Detailed missing strings
    for locale_name in sorted(locale_files.keys()):
        if locale_name == "default":
            continue
        
        locale_keys = all_locale_keys.get(locale_name, set())
        missing = analyze_missing_strings(base_keys, locale_keys)
        extra = analyze_extra_strings(base_keys, locale_keys)
        
        if missing or extra:
            report_lines.append(f"Locale: {locale_name}")
            report_lines.append(f"  File: {locale_files[locale_name]}")
            
            if missing:
                report_lines.append(f"  Missing strings ({len(missing)}):")
                for key in sorted(missing):
                    report_lines.append(f"    - {key}")
            
            if extra:
                report_lines.append(f"  Extra strings ({len(extra)}):")
                for key in sorted(extra):
                    report_lines.append(f"    + {key}")
            
            report_lines.append("")
    
    return "\n".join(report_lines)


def main():
    """Main function to run the analysis."""
    # Determine base directory (project root)
    # Script is in scripts/ directory, so go up one level to project root
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    base_dir = project_root / "app" / "src" / "main"
    
    # Check if base directory exists
    if not base_dir.exists():
        print(f"Error: Base directory not found: {base_dir}", file=sys.stderr)
        print(f"Current working directory: {Path.cwd()}", file=sys.stderr)
        return 1
    
    print("Finding strings.xml files...", flush=True)
    locale_files = find_locale_strings_files(base_dir)
    
    if not locale_files:
        print("No strings.xml files found!", file=sys.stderr)
        return 1
    
    print(f"Found {len(locale_files)} locale file(s):", flush=True)
    for locale_name, file_path in sorted(locale_files.items()):
        print(f"  - {locale_name}: {file_path}", flush=True)
    print(flush=True)
    
    # Parse base file (default)
    if "default" not in locale_files:
        print("Error: Base file (values/strings.xml) not found!", file=sys.stderr)
        return 1
    
    print("Parsing base file (values/strings.xml)...", flush=True)
    base_keys = parse_strings_xml(locale_files["default"])
    if not base_keys:
        print("Warning: No keys found in base file!", file=sys.stderr)
    print(f"Found {len(base_keys)} strings in base file", flush=True)
    print(flush=True)
    
    # Parse all locale files
    print("Parsing locale files...", flush=True)
    all_locale_keys = {}
    for locale_name, file_path in locale_files.items():
        keys = parse_strings_xml(file_path)
        all_locale_keys[locale_name] = keys
        print(f"  {locale_name}: {len(keys)} strings", flush=True)
    
    print(flush=True)
    
    # Generate and print report
    report = format_report(locale_files, base_keys, all_locale_keys)
    print(report, flush=True)
    
    # Save report to file in project root
    report_file = project_root / "strings_xml_analysis_report.txt"
    try:
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write(report)
        print(flush=True)
        print(f"Report saved to: {report_file}", flush=True)
    except Exception as e:
        print(f"Error saving report: {e}", file=sys.stderr)
        return 1
    
    return 0


if __name__ == "__main__":
    import sys
    sys.exit(main())
