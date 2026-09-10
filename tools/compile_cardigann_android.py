#!/usr/bin/env python3
"""Compile the Cardigann subset M00V13 can execute with a tiny Android runtime.

Template/default/filter complexity is resolved at build time whenever it is deterministic.
Definitions are rejected rather than approximated when behavior cannot be represented safely.
"""
from __future__ import annotations

import argparse, json, pathlib, re
from typing import Any, Dict, List, Optional, Tuple
from urllib.parse import quote_plus
import yaml

COMPILER_SCHEMA = 5
DEFAULT_INPUT = pathlib.Path("indexers/definitions/v11")
DEFAULT_OUTPUT = pathlib.Path("android/app/src/main/assets/cardigann_providers.json")
DEFAULT_REPORT = pathlib.Path("indexers/android_compile_manifest.json")

TIER1 = {"1337x", "yts", "torrentdownload", "limetorrents"}
TIER2 = {"nyaasi", "uindex", "kickasstorrents-to", "extratorrent-st", "tokyotosho"}

MANUAL = {
    "1337x": {"searchPath":"search/{query}/1/","rowSelector":'tr:has(a[href^=/torrent/])',"titleSelector":'td[class^=coll-1] a[href^=/torrent/]','detailsSelector':'td[class^=coll-1] a[href^=/torrent/]','detailsAttribute':'href','seedersSelector':'td[class^=coll-2]','sizeSelector':'td[class^=coll-4]','detailMagnetSelector':'ul li a[href^=magnet:]','maxResults':16},
    "torrentdownload": {"searchPath":"searchd?q={query}","rowSelector":"table.table2 > tbody > tr:has(span.smallish)","titleSelector":'div.tt-name > a[href^="/"]',"detailsSelector":'div.tt-name > a[href^="/"]',"detailsAttribute":"href","seedersSelector":"td.tdseed","sizeSelector":"td:nth-child(3)","detailMagnetSelector":'a[href^="magnet:?xt="]',"maxResults":16},
    "limetorrents": {"searchPath":"search/all/{query}/date/1/","rowSelector":".table2 > tbody > tr[bgcolor]","titleSelector":'div.tt-name > a[href^="/"]',"detailsSelector":'div.tt-name > a[href^="/"]',"detailsAttribute":"href","seedersSelector":".tdseed","sizeSelector":"td:nth-child(3)","detailMagnetSelector":'a.csprite_dltorrent[href^="magnet:"]',"maxResults":16},
    "nyaasi": {"searchPath":"?q={query}&f=0&c=0_0&s=id&o=desc","rowSelector":"tr.default,tr.danger,tr.success","titleSelector":"td:nth-child(2) a:last-of-type","detailsSelector":"td:nth-child(2) a:last-of-type","detailsAttribute":"href","seedersSelector":"td:nth-child(6):not(:empty)","sizeSelector":"td:nth-child(4)","rowMagnetSelector":'td:nth-child(3) a[href^="magnet:?"]',"maxResults":16},
    "yts": {"responseType":"json","searchPath":"https://movies-api.accel.li/api/v2/list_movies.json?query_term={query}&limit=50&sort_by=date_added&order_by=desc","jsonRowsPath":"data.movies","jsonExpandArrayPath":"torrents","jsonTitlePath":"..title_long","jsonSeedersPath":"seeds","jsonSizePath":"size_bytes","jsonInfoHashPath":"hash","jsonQualityPath":"quality","jsonCodecPath":"video_codec","jsonAudioPath":"audio_channels","jsonUrlPath":"url","maxResults":20},
}

CONFIG_RE = re.compile(r"\{\{\s*\.Config\.([A-Za-z0-9_-]+)\s*\}\}")
KEYWORDS_RE = re.compile(r"\{\{\s*\.Keywords\s*\}\}")
IF_KEYWORDS_RE = re.compile(r'^\s*\{\{\s*if\s+\.Keywords\s*\}\}(.*?)\{\{\s*else\s*\}\}(.*?)\{\{\s*end\s*\}\}\s*$', re.S)


def provider_tier(ident: str, compiled: Dict[str, Any]) -> int:
    if ident in TIER1:
        return 1
    if ident in TIER2:
        return 2
    # Direct-magnet providers avoid an extra details-page request and are cheap enough
    # for the normal fallback tier. Everything else remains the broad final tier.
    return 2 if compiled.get("rowMagnetSelector") else 3


def config_defaults(raw: Dict[str, Any]) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for item in raw.get("settings") or []:
        if isinstance(item, dict) and item.get("name") and "default" in item:
            v=item.get("default")
            if isinstance(v,(str,int,float,bool)): out[str(item["name"])]=str(v).lower() if isinstance(v,bool) else str(v)
    return out


def resolve_config(value: Any, defaults: Dict[str,str]) -> Any:
    if not isinstance(value,str): return value
    def repl(m: re.Match[str]) -> str:
        return defaults.get(m.group(1), m.group(0))
    return CONFIG_RE.sub(repl,value)


def static(value: Any, defaults: Dict[str,str]) -> Optional[str]:
    value=resolve_config(value,defaults)
    if not isinstance(value,str) or not value.strip() or "{{" in value or "}}" in value: return None
    return value.strip()


def keyword_branch(value: str) -> str:
    m=IF_KEYWORDS_RE.match(value)
    return m.group(1).strip() if m else value


def scalar_template(value: Any, defaults: Dict[str,str]) -> Optional[str]:
    value=resolve_config(value,defaults)
    if isinstance(value,(int,float)): return str(value)
    if isinstance(value,bool): return "true" if value else "false"
    if not isinstance(value,str): return None
    s=keyword_branch(value.strip())
    s=KEYWORDS_RE.sub("{query}",s)
    if "{{" in s or "}}" in s: return None
    return s


def build_inputs(search: Dict[str,Any], path_entry: Any, defaults: Dict[str,str]) -> Optional[str]:
    merged: Dict[str,Any]={}
    for src in (search.get("inputs"), path_entry.get("inputs") if isinstance(path_entry,dict) else None):
        if isinstance(src,dict): merged.update(src)
    if not merged: return ""
    parts=[]
    for key,value in merged.items():
        if str(key).startswith("$"): return None
        rendered=scalar_template(value,defaults)
        if rendered is None: return None
        k=quote_plus(str(key)); v=rendered if "{query}" in rendered else quote_plus(rendered)
        parts.append(f"{k}={v}")
    return "&".join(parts)


def choose_path(search: Dict[str,Any], defaults: Dict[str,str]) -> Optional[str]:
    for entry in search.get("paths") or []:
        raw=entry.get("path") if isinstance(entry,dict) else entry
        rendered=scalar_template(raw,defaults)
        if rendered is None: continue
        inputs=build_inputs(search,entry,defaults)
        if inputs is None: continue
        if "{query}" not in rendered and "{query}" not in inputs: continue
        sep="&" if "?" in rendered else "?"
        return rendered.lstrip("/") + (sep+inputs if inputs else "")
    return None


def selector(field: Any, defaults: Dict[str,str]) -> Optional[str]:
    return static(field.get("selector"),defaults) if isinstance(field,dict) else None


def download_magnet_selector(raw: Dict[str,Any], defaults: Dict[str,str]) -> Optional[str]:
    download=raw.get("download") if isinstance(raw.get("download"),dict) else {}
    for item in download.get("selectors") or []:
        if not isinstance(item,dict) or item.get("attribute","href")!="href": continue
        s=static(item.get("selector"),defaults)
        if s and "magnet" in s.lower(): return s
    return None


def compile_one(raw: Dict[str,Any]) -> Tuple[Optional[Dict[str,Any]],List[str]]:
    reasons=[]; ident=str(raw.get("id") or "").strip(); name=str(raw.get("name") or ident).strip(); defaults=config_defaults(raw)
    links=[x for x in (raw.get("links") or []) if isinstance(x,str) and x.startswith("https://")]
    if not ident: reasons.append("missing id")
    if not links: reasons.append("no HTTPS links")
    if ident in MANUAL and not reasons:
        record={"id":ident,"name":name,"mirrors":links[:8],**MANUAL[ident]}
        record["tier"]=provider_tier(ident,record)
        return record,[]

    search=raw.get("search") if isinstance(raw.get("search"),dict) else {}; fields=search.get("fields") if isinstance(search.get("fields"),dict) else {}; rows=search.get("rows") if isinstance(search.get("rows"),dict) else {}
    response="html"
    for pe in search.get("paths") or []:
        if isinstance(pe,dict) and isinstance(pe.get("response"),dict) and pe["response"].get("type")=="json": response="json"
    if response=="json": reasons.append("generic JSON definition not yet representable")

    path=choose_path(search,defaults); row_sel=static(rows.get("selector"),defaults)
    title_field=fields.get("title") if isinstance(fields.get("title"),dict) else None; details_field=fields.get("details") if isinstance(fields.get("details"),dict) else title_field
    title_sel=selector(title_field,defaults); details_sel=selector(details_field,defaults); details_attr=details_field.get("attribute","href") if isinstance(details_field,dict) else "href"
    seed_sel=selector(fields.get("seeders"),defaults) or ""; size_sel=selector(fields.get("size"),defaults) or ""
    magnet_field=fields.get("magnet") if isinstance(fields.get("magnet"),dict) else None
    download_field=fields.get("download") if isinstance(fields.get("download"),dict) else None
    row_magnet=selector(magnet_field,defaults)
    if not row_magnet:
        ds=selector(download_field,defaults)
        if ds and "magnet" in ds.lower() and download_field.get("attribute","href")=="href": row_magnet=ds
    detail_magnet=download_magnet_selector(raw,defaults)

    if not path: reasons.append("search path/inputs need unsupported template logic")
    if not row_sel: reasons.append("row selector is dynamic/missing")
    if not title_sel: reasons.append("title must be a direct static selector")
    if isinstance(title_field,dict) and title_field.get("attribute") not in (None,""): reasons.append("title attribute/filter extraction unsupported")
    # A details page is only needed if the result row does not already expose a magnet.
    if not row_magnet:
        if not details_sel: reasons.append("details must be a direct static selector")
        if details_attr!="href": reasons.append("details attribute is not href")
    if not row_magnet and not detail_magnet: reasons.append("no supported magnet selector")
    if reasons: return None,reasons

    record={"id":ident,"name":name,"mirrors":links[:8],"searchPath":path,"rowSelector":row_sel,"titleSelector":title_sel,"detailsSelector":details_sel or title_sel or "","detailsAttribute":"href","seedersSelector":seed_sel,"sizeSelector":size_sel,"rowMagnetSelector":row_magnet or "","detailMagnetSelector":detail_magnet or "","maxResults":16}
    record["tier"]=provider_tier(ident,record)
    return record,[]


def main()->None:
    p=argparse.ArgumentParser();p.add_argument("--input",type=pathlib.Path,default=DEFAULT_INPUT);p.add_argument("--output",type=pathlib.Path,default=DEFAULT_OUTPUT);p.add_argument("--report",type=pathlib.Path,default=DEFAULT_REPORT);a=p.parse_args();providers=[];rejected=[]
    for path in sorted(a.input.glob("*.yml")):
        try:
            raw=yaml.safe_load(path.read_text(encoding="utf-8"));
            if not isinstance(raw,dict): raise ValueError("root is not a mapping")
            compiled,reasons=compile_one(raw)
            if compiled: providers.append(compiled)
            else: rejected.append({"file":path.name,"id":raw.get("id"),"reasons":reasons})
        except Exception as exc: rejected.append({"file":path.name,"reasons":[f"parse error: {exc}"]})
    providers.sort(key=lambda x:(x["tier"],x["id"]));a.output.parent.mkdir(parents=True,exist_ok=True);a.report.parent.mkdir(parents=True,exist_ok=True)
    tier_counts={str(t):sum(1 for item in providers if item["tier"]==t) for t in (1,2,3)}
    a.output.write_text(json.dumps({"schema":COMPILER_SCHEMA,"tier_counts":tier_counts,"providers":providers},separators=(",",":"))+"\n",encoding="utf-8")
    a.report.write_text(json.dumps({"compiler_schema":COMPILER_SCHEMA,"compiled_count":len(providers),"rejected_count":len(rejected),"tier_counts":tier_counts,"compiled":[{"id":x["id"],"tier":x["tier"]} for x in providers],"rejected":rejected},indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print(f"compiled {len(providers)} Android-native providers; tiers={tier_counts}; rejected {len(rejected)}")

if __name__=="__main__": main()
