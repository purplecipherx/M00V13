#!/usr/bin/env python3
"""Compile a conservative Cardigann v11 subset into M00V13's tiny Android runtime format.

The compiler is intentionally strict. A definition is emitted only when its search row,
title/details link, seeders, size and magnet extraction can be represented by the native
Android engine without executing Cardigann templates or arbitrary filters at runtime.
Unsupported definitions remain available in indexers/ for future interpreter expansion.
"""
from __future__ import annotations

import argparse, json, pathlib, re
from typing import Any, Dict, List, Optional, Tuple
import yaml

DEFAULT_INPUT = pathlib.Path("indexers/definitions/v11")
DEFAULT_OUTPUT = pathlib.Path("android/app/src/main/assets/cardigann_providers.json")
DEFAULT_REPORT = pathlib.Path("indexers/android_compile_manifest.json")

MANUAL = {
    "1337x": {
        "searchPath": "search/{query}/1/",
        "rowSelector": 'tr:has(a[href^=/torrent/])',
        "titleSelector": 'td[class^=coll-1] a[href^=/torrent/]',
        "detailsAttribute": "href",
        "seedersSelector": 'td[class^=coll-2]',
        "sizeSelector": 'td[class^=coll-4]',
        "magnetSelector": 'ul li a[href^=magnet:]',
        "maxResults": 16,
    }
}


def static(value: Any) -> Optional[str]:
    if not isinstance(value, str) or not value.strip() or "{{" in value or "}}" in value:
        return None
    return value.strip()


def simple_path(value: Any) -> Optional[str]:
    if not isinstance(value, str): return None
    s = value.strip()
    # Accept the common direct Keywords placeholder only. Any other template logic is rejected.
    s = re.sub(r"\{\{\s*\.Keywords\s*\}\}", "{query}", s)
    if "{{" in s or "}}" in s or "{query}" not in s: return None
    return s.lstrip("/")


def selector(field: Any) -> Optional[str]:
    if not isinstance(field, dict): return None
    return static(field.get("selector"))


def choose_path(search: Dict[str, Any]) -> Optional[str]:
    paths = search.get("paths") or []
    for entry in paths:
        value = entry.get("path") if isinstance(entry, dict) else entry
        p = simple_path(value)
        if p: return p
    return None


def magnet_selector(download: Dict[str, Any]) -> Optional[str]:
    selectors = download.get("selectors") or []
    for entry in selectors:
        if not isinstance(entry, dict): continue
        s = static(entry.get("selector"))
        attr = entry.get("attribute", "href")
        if s and attr == "href" and "magnet" in s.lower(): return s
    return None


def compile_one(raw: Dict[str, Any]) -> Tuple[Optional[Dict[str, Any]], List[str]]:
    reasons: List[str] = []
    ident = str(raw.get("id") or "").strip()
    name = str(raw.get("name") or ident).strip()
    links = [x for x in (raw.get("links") or []) if isinstance(x, str) and x.startswith("https://")]
    if not ident: reasons.append("missing id")
    if not links: reasons.append("no HTTPS links")
    if ident in MANUAL and not reasons:
        return {"id": ident, "name": name, "mirrors": links[:8], **MANUAL[ident]}, []

    search = raw.get("search") if isinstance(raw.get("search"), dict) else {}
    fields = search.get("fields") if isinstance(search.get("fields"), dict) else {}
    rows = search.get("rows") if isinstance(search.get("rows"), dict) else {}
    path = choose_path(search)
    row_sel = static(rows.get("selector"))
    title_field = fields.get("title") if isinstance(fields.get("title"), dict) else None
    title_sel = selector(title_field)
    title_attr = title_field.get("attribute", "href") if isinstance(title_field, dict) else None
    seed_sel = selector(fields.get("seeders"))
    size_sel = selector(fields.get("size"))
    mag_sel = magnet_selector(raw.get("download") if isinstance(raw.get("download"), dict) else {})

    if not path: reasons.append("search path needs unsupported template logic")
    if not row_sel: reasons.append("row selector is dynamic/missing")
    if not title_sel: reasons.append("title must be a direct static selector")
    if title_attr not in (None, "href"): reasons.append("title/details attribute is not href")
    if not seed_sel: reasons.append("seeders must be a direct static selector")
    if not size_sel: reasons.append("size must be a direct static selector")
    if not mag_sel: reasons.append("no static magnet download selector")
    if reasons: return None, reasons

    return {
        "id": ident, "name": name, "mirrors": links[:8], "searchPath": path,
        "rowSelector": row_sel, "titleSelector": title_sel, "detailsAttribute": "href",
        "seedersSelector": seed_sel, "sizeSelector": size_sel, "magnetSelector": mag_sel,
        "maxResults": 16,
    }, []


def main() -> None:
    p=argparse.ArgumentParser(); p.add_argument("--input",type=pathlib.Path,default=DEFAULT_INPUT); p.add_argument("--output",type=pathlib.Path,default=DEFAULT_OUTPUT); p.add_argument("--report",type=pathlib.Path,default=DEFAULT_REPORT); a=p.parse_args()
    providers=[]; rejected=[]
    for path in sorted(a.input.glob("*.yml")):
        try:
            raw=yaml.safe_load(path.read_text(encoding="utf-8"))
            if not isinstance(raw,dict): raise ValueError("root is not a mapping")
            compiled,reasons=compile_one(raw)
            if compiled: providers.append(compiled)
            else: rejected.append({"file":path.name,"id":raw.get("id"),"reasons":reasons})
        except Exception as exc: rejected.append({"file":path.name,"reasons":[f"parse error: {exc}"]})
    providers.sort(key=lambda x:x["id"])
    a.output.parent.mkdir(parents=True,exist_ok=True); a.report.parent.mkdir(parents=True,exist_ok=True)
    a.output.write_text(json.dumps({"schema":1,"providers":providers},separators=(",",":"))+"\n",encoding="utf-8")
    a.report.write_text(json.dumps({"compiled_count":len(providers),"rejected_count":len(rejected),"compiled":[p["id"] for p in providers],"rejected":rejected},indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(f"compiled {len(providers)} Android-native providers; rejected {len(rejected)}")

if __name__=="__main__": main()
