#!/usr/bin/env python3
"""Pins Mozilla's live models.json into app/src/main/assets/bergamot/manifest.json.

Picks, per direction, the best *released* model for every language in Language.kt:
base-memory > base > tiny, releaseStatus in {Release, Release Android}. Run when bumping models.

Usage: pin-manifest.py --version N

The version is what every marker and stamp on disk is compared against, so it is the only thing
standing between an app update that changes a pack's URL or hash and a device that resumes a v1
`.part` into a v2 pack. Writing new packs under the old version is therefore refused: bump it.
"""
import argparse, json, sys, urllib.request, pathlib

parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
parser.add_argument("--version", type=int, required=True,
                    help="manifest version; bump it whenever any pack changes")
args = parser.parse_args()

LANGS = ["uk","de","fr","es","it","pt","nl","pl","zh","ja","ko","ar","hi","tr","sv","cs","ru"]
URL = "https://storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data/db/models.json"
RANK = {"base-memory": 0, "base": 1, "tiny": 2}
OK = {"Release", "Release Android"}

live = json.load(urllib.request.urlopen(URL))
packs = []
for lang in LANGS:
    for src, tgt in ((("en", lang)), ((lang, "en"))):
        cands = [m for m in live["models"].get(f"{src}-{tgt}", []) if m.get("releaseStatus") in OK]
        if not cands:
            sys.exit(f"no released model for {src}-{tgt}")
        m = sorted(cands, key=lambda m: RANK.get(m["architecture"], 9))[0]
        f = m["files"]
        # Most directions ship one shared "vocab" file; a few (en-zh/ja/ko) ship separate
        # srcVocab/trgVocab instead — a Marian model trained with split vocabs needs both files
        # (vocabs: [src.spm, trg.spm]), so keep both rather than dropping one.
        if "vocab" in f:
            vocab_fields = {"vocab": {"path": f["vocab"]["path"]}}
        elif "srcVocab" in f and "trgVocab" in f:
            vocab_fields = {
                "vocab": {"path": f["srcVocab"]["path"]},
                "targetVocab": {"path": f["trgVocab"]["path"]},
            }
        else:
            sys.exit(f"no vocab or srcVocab/trgVocab for {src}-{tgt}")
        packs.append({
            "source": src, "target": tgt, "architecture": m["architecture"],
            "model": {"path": f["model"]["path"], "size": f["model"]["uncompressedSize"],
                      "sha256": f["model"]["uncompressedHash"]},
            **vocab_fields,
            "shortlist": {"path": f["lexicalShortlist"]["path"]},
        })
out = {"version": args.version, "generated": live["generated"], "baseUrl": live["baseUrl"], "packs": packs}
dest = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets/bergamot/manifest.json"

# A device only re-downloads a pack when the version it is stamped with changes. Shipping different
# packs under the same version leaves every existing install pointing at files that no longer match.
if dest.exists():
    old = json.loads(dest.read_text())
    if old.get("packs") != packs and old.get("version") == args.version:
        sys.exit(
            f"refusing to write: the packs changed but --version is still {args.version}.\n"
            f"Devices key their installed packs on this number, so an unchanged version means they\n"
            f"keep the old files forever. Re-run with --version {args.version + 1}."
        )

dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n")
print(f"wrote {len(packs)} packs to {dest}")
