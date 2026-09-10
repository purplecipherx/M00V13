#!/usr/bin/env python3
"""Sync compatible public English movie/TV Cardigann definitions.

This is a build/update tool. M00V13 does not require a running Prowlarr server.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import shutil
import tempfile
import urllib.request
import zipfile
from typing import Any, Dict, Iterable, List, Tuple

import yaml

from m00v13.indexers.definition import IndexerDefinition


ARCHIVE_URL = "https://github.com/Prowlarr/Indexers/archive/refs/heads/master.zip"
DEFAULT_OUTPUT = pathlib.Path("indexers/definitions/v11")
DEFAULT_MANIFEST = pathlib.Path("indexers/public_en_manifest.json")


def download_archive(destination: pathlib.Path) -> None:
    request = urllib.request.Request(
        ARCHIVE_URL,
        headers={"User-Agent": "M00V13-indexer-sync/0.1", "Accept": "application/zip"},
    )
    with urllib.request.urlopen(request, timeout=45) as response, destination.open("wb") as handle:
        shutil.copyfileobj(response, handle)


def iter_v11_definitions(extracted_root: pathlib.Path) -> Iterable[pathlib.Path]:
    candidates = list(extracted_root.glob("Indexers-*/definitions/v11/*.yml"))
    if not candidates:
        raise RuntimeError("Prowlarr Indexers v11 definitions were not found in archive")
    return sorted(candidates)


def load_yaml(path: pathlib.Path) -> Dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        raw = yaml.safe_load(handle)
    if not isinstance(raw, dict):
        raise ValueError("definition root is not a mapping")
    return raw


def sync(output: pathlib.Path, manifest_path: pathlib.Path) -> Tuple[int, int]:
    output.mkdir(parents=True, exist_ok=True)
    manifest_path.parent.mkdir(parents=True, exist_ok=True)

    # Remove stale generated definitions so removed/upstream-reclassified trackers
    # do not linger in the M00V13 catalog.
    for stale in output.glob("*.yml"):
        stale.unlink()

    included: List[Dict[str, Any]] = []
    rejected: List[Dict[str, Any]] = []

    with tempfile.TemporaryDirectory(prefix="m00v13-indexers-") as temp_dir:
        temp = pathlib.Path(temp_dir)
        archive = temp / "indexers.zip"
        download_archive(archive)
        with zipfile.ZipFile(archive) as bundle:
            bundle.extractall(temp / "src")

        for source in iter_v11_definitions(temp / "src"):
            try:
                raw = load_yaml(source)
                definition = IndexerDefinition.from_mapping(raw)
                compatibility = definition.compatibility()
            except Exception as exc:
                rejected.append({"file": source.name, "reasons": [f"parse error: {exc}"]})
                continue

            record = {
                "id": definition.id,
                "name": definition.name,
                "file": source.name,
                "language": definition.language,
                "type": definition.type,
            }
            if compatibility.compatible:
                shutil.copy2(source, output / source.name)
                included.append(record)
            else:
                record["reasons"] = list(compatibility.reasons)
                rejected.append(record)

    manifest = {
        "source": "Prowlarr/Indexers definitions/v11",
        "policy": "public + English + movie/TV + no explicit account login",
        "included_count": len(included),
        "rejected_count": len(rejected),
        "included": sorted(included, key=lambda item: item["id"]),
        "rejected": sorted(rejected, key=lambda item: item.get("id") or item["file"]),
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return len(included), len(rejected)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=pathlib.Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--manifest", type=pathlib.Path, default=DEFAULT_MANIFEST)
    args = parser.parse_args()
    included, rejected = sync(args.output, args.manifest)
    print(f"synced {included} compatible public English indexers; rejected {rejected}")


if __name__ == "__main__":
    main()
