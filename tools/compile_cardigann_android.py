#!/usr/bin/env python3
"""Compile a conservative Cardigann v11 subset into M00V13's tiny Android runtime format.

The compiler intentionally emits only patterns the Android runtime can faithfully execute.
Complex definitions remain in indexers/ for later interpreter expansion.
"""
from __future__ import annotations

import argparse, json, pathlib, re
from typing import Any, Dict, List, Optional, Tuple
import yaml

COMPILER_SCHEMA = 3
DEFAULT_INPUT = pathlib.Path("indexers/definitions/v11")
DEFAULT_OUTPUT = pathlib.Path("android/app/src/main/assets/cardigann_providers.json")
DEFAULT_REPORT = pathlib.Path("indexers/android_compile_manifest.json")

MANUAL = {
    "1337x": {
        "searchPath": "search/{query}/1/",
        "rowSelector": 'tr:has(a[href^=/torrent/])',
        "titleSelector": 'td[class^=coll-1] a[href^=/torrent/]',
        "detailsSelector": 'td[class^=coll-1] a[href^=/torrent/]',
        "detailsAttribute": "href",
        "seedersSelector": 'td[class^=coll-2]',
        "sizeSelector": 'td[class^=coll-4]',
        "detailMagnetSelector": 'ul li a[href^=magnet:]',
        "maxResults": 16,
    },
    "torrentdownload": {
        "searchPath": "searchd?q={query}",
        "rowSelector": "table.table2 > tbody > tr:has(span.smallish)",
        "titleSelector": 'div.tt-name > a[href^="/"]',
        "detailsSelector": 'div.tt-name > a[href^="/"]',
        "detailsAttribute": "href",
        "seedersSelector": "td.tdseed",
        "sizeSelector": "td:nth-child(3)",
        "detailMagnetSelector": 'a[href^="magnet:?xt="]',
        "maxResults": 16,
    },
    "limetorrents": {
        "searchPath": "search/all/{query}/date/1/",
        "rowSelector": ".table2 > tbody > tr[bgcolor]",
        "titleSelector": 'div.tt-name > a[href^="/"]',
        "detailsSelector": 'div.tt-name > a[href^="/"]',
        "detailsAttribute": "href",
        "seedersSelector": ".tdseed",
        "sizeSelector": "td:nth-child(3)",
        "detailMagnetSelector": 'a.csprite_dltorrent[href^="magnet:"]',
        "maxResults": 16,
    },
    "nyaasi": {
        "searchPath": "?q={query}&f=0&c=0_0&s=id&o=desc",
        "rowSelector": "tr.default,tr.danger,tr.success",
        "titleSelector": "td:nth-child(2) a:last-of-type",
        "detailsSelector": "td:nth-child(2) a:last-of-type",
        "detailsAttribute": "href",
        "seedersSelector": "td:nth-child(6):not(:empty)",
        "sizeSelector": "td:nth-child(4)",
        "rowMagnetSelector": 'td:nth-child(3) a[href^="magnet:?"]',
        "maxResults": 16,
    },
    "yts": {
        "responseType": "json",
        "searchPath": "https://movies-api.accel.li/api/v2/list_movies.json?query_term={query}&limit=50&sort_by=date_added&order_by=desc",
        "jsonRowsPath": "data.movies",
        "jsonExpandArrayPath": "torrents",
        "jsonTitlePath": "..title_long",
        "jsonSeedersPath": "seeds",
        "jsonSizePath": "size_bytes",
        "jsonInfoHashPath": "hash",
        "jsonQualityPath": "quality",
        "jsonCodecPath": "video_codec",
        "jsonAudioPath": "audio_channels",
        "jsonUrlPath": "url",
        "maxResults": 20,
    },
}


def static(value: Any) -> Optional[str]:
    if not isinstance(value, str) or not value.strip() or "{{" in value or "}}" in value:
        return None
    return value.strip()


def simple_path(value: Any) -> Optional[str]:
    if not isinstance(value, str): return None
    s = value.strip()
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


def compile_one(raw: Dict[str, Any]) -> Tuple[Optional[Dict[str, Any]], List[str]]:
    reasons: List[str] = []
    ident = str(raw.get("id") or "").strip()
    name = str(raw.get("name") or ident).strip()
    links = [x for x in (raw.get("links") or []) if isinstance(x, str) and x.startswith("https://")]
    if not ident: reasons.append("missing id")
    if not links: reasons.append("no HTTPS links")
    if ident in MANUAL and not reasons:
        provider = {"id": ident, "name": name, "mirrors": links[:8], **MANUAL[ident]}
        return provider, []

    search = raw.get("search") if isinstance(raw.get("search"), dict) else {}
    fields = search.get("fields") if isinstance(search.get("fields"), dict) else {}
    rows = search.get("rows") if isinstance(search.get("rows"), dict) else {}
    path = choose_path(search)
    row_sel = static(rows.get("selector"))
    title_field = fields.get("title") if isinstance(fields.get("title"), dict) else None
    details_field = fields.get("details") if isinstance(fields.get("details"), dict) else title_field
    title_sel = selector(title_field)
    details_sel = selector(details_field)
    details_attr = details_field.get("attribute", "href") if isinstance(details_field, dict) else "href"
    seed_sel = selector(fields.get("seeders"))
    size_sel = selector(fields.get("size"))
    magnet_field = fields.get("magnet") if isinstance(fields.get("magnet"), dict) else None
    row_magnet = selector(magnet_field)

    if not path: reasons.append("search path needs unsupported template logic")
    if not row_sel: reasons.append("row selector is dynamic/missing")
    if not title_sel: reasons.append("title must be a direct static selector")
    if not details_sel: reasons.append("details must be a direct static selector")
    if details_attr != "href": reasons.append("details attribute is not href")
    if not seed_sel: reasons.append("seeders must be a direct static selector")
    if not size_sel: reasons.append("size must be a direct static selector")
    if not row_magnet: reasons.append("no direct row magnet selector")
    if reasons: return None, reasons

    return {
        "id": ident, "name": name, "mirrors": links[:8], "searchPath": path,
        "rowSelector": row_sel, "titleSelector": title_sel, "detailsSelector": details_sel,
        "detailsAttribute": "href", "seedersSelector": seed_sel, "sizeSelector": size_sel,
        "rowMagnetSelector": row_magnet, "maxResults": 16,
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
    a.output.write_text(json.dumps({"schema":COMPILER_SCHEMA,"providers":providers},separators=(",",":"))+"\n",encoding="utf-8")
    a.report.write_text(json.dumps({"compiler_schema":COMPILER_SCHEMA,"compiled_count":len(providers),"rejected_count":len(rejected),"compiled":[p["id"] for p in providers],"rejected":rejected},indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(f"compiled {len(providers)} Android-native providers; rejected {len(rejected)}")

if __name__=="__main__": main()
