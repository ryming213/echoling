"""
Preprocess bundled vocabulary JSON assets for 听言英语 (Echo Ling).

This script reads 13 source wordbook JSONs from
``D:/English/English Vocabulary/`` and produces 11 target assets under
``app/src/main/assets/vocab_*.json`` according to the rules in
``docs/superpowers/plans/2026-07-03-vocab-expansion.md`` (see git history).

Pipeline per target category:

  primary  = source:vocab_primary.json                                      (verbatim)
  junior   = merge(source:vocab_junior.json, source:vocab_junior-1.json)    (headWord dedup, longer-tran wins)
  senior   = merge(source:vocab_senior.json, source:vocab_senior-1.json,
                    source:vocab_senior-2.json)                              (headWord dedup, longer-tran wins)
  cet4     = merge(source:vocab_cet4.json, source:vocab_cet4-1.json)        (headWord dedup, longer-tran wins)
  cet6     = source:vocab_cet6.json − (primary ∪ junior ∪ cet4 headWords)  (cascade)
  cet8     = source:vocab_cet8.json − (primary ∪ junior ∪ senior ∪ cet4
                                        ∪ cet6 headWords)                   (cascade)
  kaoyan   = source:vocab_KaoYan.json                                       (verbatim)
  toefl    = NOT processed here — current assets/vocab_toefl.json is left untouched
  gre      = source:GRE.json                                                 (verbatim)
  ielts    = source:IELTS.json                                               (verbatim)
  bec      = source:BEC.json                                                 (verbatim)

All outputs preserve the source 4-layer nested schema
(``wordRank → content.word.content.trans[]``) verbatim — the
runtime ``DictionaryRepositoryImpl`` consumes them through the
existing ``NestedWordEntry`` mirror data class. The only mutation is
removal of duplicates; no field is dropped, renamed, or flat-flattened.

The script is **idempotent** — running it again with no source changes
produces byte-identical output. This makes it safe to invoke from a
build hook in the future (the plan defers that until the asset shape
stabilises).

Usage::

    python scripts/build_vocab_assets.py
    python scripts/build_vocab_assets.py --source <other_dir> --dest <other_dir>

Each category's final entry count and on-disk size is printed at the end
so a stale run is easy to spot (``vocab_cet8.json`` should be well
under the raw 12197 input — if it's the same size, the cascade subtractor
didn't run).

**Manifest emission**: as of 2026-07-04 this script also (re)writes
``vocab_manifest.json`` next to the vocab JSONs, including each
category's ``size`` (post-prune entry count). The runtime
``DictionaryRepositoryImpl`` reads ``size`` from the manifest to render
the picker UI's "N 词" chip without paying the cost of parsing the
multi-MB entries files — the manifest itself is ~1 KB. If the existing
manifest is present we preserve its ``id``/``name``/``description``
fields (this script is the source of truth for sizes only) and merge
in the new sizes; if it's missing we synthesise placeholder strings
and emit a warning so the human can fix names/descriptions before
shipping.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Iterable

# --- Paths -------------------------------------------------------------------

DEFAULT_SRC = Path(r"D:/English/English Vocabulary")
DEFAULT_DST = Path(
    r"c:/Users/MING/myagent/echoling/app/src/main/assets"
)

SRC_FILES = {
    "primary":   "vocab_primary.json",
    "junior_0":  "vocab_junior.json",
    "junior_1":  "vocab_junior-1.json",
    "senior_0":  "vocab_senior.json",
    "senior_1":  "vocab_senior-1.json",
    "senior_2":  "vocab_senior-2.json",
    "cet4_0":    "vocab_cet4.json",
    "cet4_1":    "vocab_cet4-1.json",
    "cet6":      "vocab_cet6.json",
    "cet8":      "vocab_cet8.json",
    "kaoyan":    "vocab_KaoYan.json",
    "toefl":     "vocab_toefl.json",
    "gre":       "GRE.json",
    "ielts":     "IELTS.json",
    "bec":       "BEC.json",
}


# --- IO helpers --------------------------------------------------------------

def load(p: Path) -> list[dict]:
    """Load a source JSON. Source files are always 4-layer nested arrays
    (verified across all 13 — schema A). Bail loudly if not."""
    with p.open("r", encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError(
            f"{p} is not a JSON array at the top level "
            f"(got {type(data).__name__})"
        )
    return data


def write(items: list[dict], dst: Path) -> None:
    """Compact UTF-8 JSON write. separators=(',',':') keeps the on-disk
    size under control without changing semantics. ensure_ascii=False
    preserves Chinese characters as-is."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    with dst.open("w", encoding="utf-8") as f:
        json.dump(items, f, ensure_ascii=False, separators=(",", ":"))


# --- Manifest emission -------------------------------------------------------
#
# The runtime `DictionaryRepositoryImpl` reads `size` from the manifest
# to render the picker UI's "N 词" chip WITHOUT paying the cost of
# parsing the multi-MB entries files. Previously the manifest was
# hand-maintained and the size came from `DictCategory.entries.size`
# (which forced the full parse). The 2-phase lazy-loading refactor
# (2026-07-04) needs the manifest to carry the count, so this script
# becomes the single source of truth for sizes.
#
# We preserve `id`/`name`/`description` from the existing manifest
# (this script doesn't generate Chinese labels — a human does that
# once) and merge in `size` from each category's pruned list.

MANIFEST_FILENAME = "vocab_manifest.json"


def read_existing_manifest(dst: Path) -> dict[str, dict]:
    """Read the previously-committed manifest, indexed by ``asset``
    filename. Returns an empty dict if the file is missing or malformed —
    callers must tolerate the missing-case (placeholder strings are
    used in that branch) so a fresh checkout can still build."""
    manifest_path = dst / MANIFEST_FILENAME
    if not manifest_path.exists():
        return {}
    try:
        with manifest_path.open("r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"  warn: could not read existing {MANIFEST_FILENAME}: {e}",
              file=sys.stderr)
        return {}
    indexed: dict[str, dict] = {}
    for entry in data.get("categories", []):
        asset = entry.get("asset")
        if asset:
            indexed[asset] = entry
    return indexed


def asset_to_default_id(asset: str) -> str:
    """Derive a category id from the asset filename when the existing
    manifest is missing. e.g. ``vocab_cet4.json`` → ``cet4``,
    ``vocab_primary.json`` → ``primary``. We strip the ``vocab_`` prefix
    and the ``.json`` suffix; this matches the historic naming."""
    name = asset
    if name.startswith("vocab_"):
        name = name[len("vocab_"):]
    if name.endswith(".json"):
        name = name[:-len(".json")]
    return name


def write_manifest(
    dst: Path,
    asset_to_size: dict[str, int],
    existing: dict[str, dict],
) -> None:
    """Write ``vocab_manifest.json`` with each category's ``size`` while
    preserving ``id``/``name``/``description`` from the existing file.
    Emits a one-time warning per category that has no prior entry so
    the human can fix the names before shipping — placeholder strings
    keep the app buildable but would surface ugly slugs in the UI."""
    categories: list[dict] = []
    missing_meta: list[str] = []
    for asset, size in asset_to_size.items():
        prior = existing.get(asset, {})
        if not prior:
            missing_meta.append(asset)
        categories.append({
            "id": prior.get("id") or asset_to_default_id(asset),
            "name": prior.get("name") or asset_to_default_id(asset),
            "asset": asset,
            "description": prior.get("description") or "",
            "size": size,
        })
    manifest_path = dst / MANIFEST_FILENAME
    with manifest_path.open("w", encoding="utf-8") as f:
        json.dump(
            {"categories": categories},
            f, ensure_ascii=False, separators=(",", ":"),
        )
    if missing_meta:
        print(
            f"  warn: no prior manifest entry for {missing_meta} — "
            f"wrote placeholder id/name. Edit {MANIFEST_FILENAME} before shipping.",
            file=sys.stderr,
        )


# --- Field pruning (size optimisation) ---------------------------------------

# Fields actually consumed by `DictionaryRepositoryImpl.nestedToFlat`:
#
#   - headWord                                (top level)
#   - content.word.content.usphone           (phonetic)
#   - content.word.content.trans[0].pos      (part of speech)
#   - content.word.content.trans[*].tranCn   (Chinese gloss, all POS)
#   - content.word.content.sentence.sentences[0].sContent_eng
#   - content.word.content.sentence.sentences[0].sCn
#                                            (first example sentence EN/CN)
#
# We must ALSO keep a `wordRank` key at the top level — the runtime
# schema sniff does ``firstLine.contains("\"wordRank\"")`` and would
# otherwise route the file down the wrong code path. The value can be
# anything (the runtime ignores it).
#
# Everything else that the source files carry — ``bookId``,
# ``content.word.wordHead`` (duplicate of headWord), ``wordId``,
# ``ukphone`` (British phonetic), ``sContent`` (plain English fallback
# for the example sentence), ``sSpeech`` (TTS path), ``syno``
# (synonyms), ``phrase`` (phrases), ``exam``, ``relWord``,
# ``sentence.desc`` (fixed label "例句"), and any trans[1+] /
# sentence[1+] entries — is read by nothing in the app. Dropping them
# cuts on-disk size ~3× because ``syno`` carries the bulk of the
# remaining bytes; we keep ``sentence[0]`` only because the flashcard
# back face renders it under the translation (2026-07-04 user
# request: "有的话就加到闪卡的下方").
#
# The mirror ``NestedWordEntry`` / ``NestedContent`` / ``NestedWord`` /
# ``NestedWordContent`` / ``NestedTrans`` / ``NestedSentence`` /
# ``NestedSentenceItem`` data classes declare extra
# ``@SerializedName`` fields, but Gson treats absent JSON keys as
# null on a nullable Kotlin property, so the runtime keeps working
# without any code change.

NEEDED_TOP_KEYS = ("wordRank", "headWord")


def _trans_list(entry: dict) -> list:
    """Return the entry's ``trans[]`` array, or ``[]`` if any of the
    four intermediate layers (``content`` → ``word`` → ``content`` →
    ``trans``) is missing or non-dict. Both [prune_entry] and
    [merge.total_tran] walk this path; centralising it means a source
    schema change (e.g. ``content.word`` → ``content.entry``) only
    needs one fix instead of two."""
    try:
        wc = ((entry.get("content") or {}).get("word") or {}).get("content")
        trans = wc.get("trans") if wc else None
        return trans if isinstance(trans, list) else []
    except AttributeError:
        return []


def _tran_cn(t: dict) -> str:
    """Return the trimmed ``tranCn`` of a single trans item (or ``''``
    if the field is missing). Used by [prune_entry] and
    [merge.total_tran] to keep their accessor shape consistent."""
    return (t.get("tranCn") or "").strip()


def prune_entry(entry: dict) -> dict | None:
    """Return a minimal copy of *entry* with only the runtime-needed
    fields. Returns ``None`` if the entry has no usable translation —
    callers should drop those (mirrors ``DictionaryRepositoryImpl.isUsable``).

    Keeps ALL ``content.word.content.trans[]`` items, not just
    ``trans[0]`` — a single source record can list multiple POS groups
    (e.g. "concordance" has both an "n." gloss and a "vt." gloss) and
    the flashcard back face should surface every POS's translation, not
    just the first. The runtime ``nestedToFlat`` already joins them
    with "；"; this function just needs to make sure the data survives
    the prune step.

    Also keeps the **first** example sentence from
    ``content.word.content.sentence.sentences[]`` — the flashcard back
    face renders ``exampleSentenceEn`` / ``exampleSentenceCn`` under
    the translation. We only carry ``sContent_eng`` (final value after
    fallback — see below) and ``sCn`` (Chinese) since the runtime only
    reads those two fields; omitting ``sContent`` as a separate field
    trims ~60-150 bytes per entry that has a sentence.

    The English fallback chain is **sContent_eng → sContent**: most
    words in the corpus (~96% in junior/senior/cet4) only carry the
    plain ``sContent`` field — the headword-highlighted ``sContent_eng``
    exists only on a smaller subset (~5-30% by category). Without this
    fallback the flashcard back face would show no English sentence
    for the vast majority of words; the UI guards on
    ``entry.exampleSentenceEn.isNotBlank()``.

    After the fallback, ``<b>...</b>`` markup around the headword is
    stripped — the flashcard back face has no bold styling and would
    otherwise render the literal ``<b>younger</b>`` text. Strip at
    asset-build time so the runtime never has to think about it.
    """
    word = (entry.get("headWord") or "").strip()
    if not word:
        return None
    word_rank = entry.get("wordRank", 0) or 0
    word_content = (
        (entry.get("content") or {}).get("word") or {}
    ).get("content") or {}
    usphone = (word_content.get("usphone") or "").strip()
    trans = _trans_list(entry)
    if not trans:
        return None
    # Keep every trans item, but drop blanks. We deliberately don't
    # dedupe across items here — the runtime `nestedToFlat` does that
    # (via the shared `joinDistinctGlosses` helper) and would dedupe a
    # verbatim repeat even if the source kept two POS lines with
    # identical glosses (unusual but possible in the corpus).
    pruned_trans = []
    for t in trans:
        if not isinstance(t, dict):
            continue
        pos = (t.get("pos") or "").strip()
        tran_cn = _tran_cn(t)
        if not tran_cn:
            continue
        pruned_trans.append({"pos": pos, "tranCn": tran_cn})
    if not pruned_trans:
        return None
    # First example sentence (sContent_eng preferred — headword-highlighted
    # form with `<b>...</b>` around the headword; falls back to `sContent`
    # plain English, then strips the `<b>` markup so the runtime can render
    # the sentence verbatim without parsing HTML). Probed on 2026-07-04
    # across the bundled corpus: in vocab_primary/junior/senior/cet4 only
    # ~5-30% of entries carry `sContent_eng`, the rest have plain
    # `sContent` only — without this fallback ~96% of flashcards would
    # render with no English sentence at all (UI guards on
    # `isNotBlank()`). The `<b>` strip is also done here rather than at
    # render time so the asset already carries clean text and the
    # runtime never has to think about it.
    sent_obj = word_content.get("sentence")
    sentences_field = sent_obj.get("sentences") if isinstance(sent_obj, dict) else None
    first_sent = sentences_field[0] if sentences_field and isinstance(sentences_field, list) else None
    sentence_en = ""
    sentence_cn = ""
    if isinstance(first_sent, dict):
        # 1. sContent_eng (with <b>...</b>)  →  2. sContent (plain English)
        raw_en = (first_sent.get("sContent_eng") or "").strip() \
                 or (first_sent.get("sContent") or "").strip()
        # Strip any remaining <b> / </b> markup. The source's
        # sContent_eng wraps the headword in `<b>...</b>`; the flashcard
        # UI isn't bold-capable (maxLines=2 + small font), so we render
        # plain text and let the user see the headword like any other
        # word. If bold-display is requested later, re-introduce
        # AnnotatedString parsing on the runtime side rather than
        # letting <b> tags leak into the asset.
        sentence_en = re.sub(r"</?b>", "", raw_en).strip()
        sentence_cn = (first_sent.get("sCn") or "").strip()
    pruned_sentence = None
    if sentence_en or sentence_cn:
        pruned_sentence = {
            "sentences": [
                {"sContent_eng": sentence_en, "sCn": sentence_cn}
            ],
            "desc": "例句",
        }
    return {
        "wordRank": word_rank,
        "headWord": word,
        "content": {
            "word": {
                "content": {
                    "usphone": usphone,
                    "trans": pruned_trans,
                    "sentence": pruned_sentence,
                },
            },
        },
    }


def prune_list(items: list[dict]) -> list[dict]:
    """Apply [prune_entry] to each item, dropping any that come back ``None``."""
    out: list[dict] = []
    skipped = 0
    for entry in items:
        pruned = prune_entry(entry)
        if pruned is None:
            skipped += 1
            continue
        out.append(pruned)
    if skipped:
        print(
            f"  note: pruned out {skipped} entries with no usable translation",
            file=sys.stderr,
        )
    return out


# --- Dedup key + merge helpers ----------------------------------------------

def dedup_key(entry: dict) -> str:
    """Lookup key: lowercase trimmed headWord. Empty string for missing
    headWord (defensive — sources observed to always have it, but the
    NestedWordEntry mirror schema marks it nullable so this can drift)."""
    return (entry.get("headWord") or "").strip().lower()


def merge(*sources: Path) -> list[dict]:
    """Union + dedup by lowercase headWord. On conflict, keep the entry
    whose combined ``trans[*].tranCn`` is longer (richer gloss).
    Order is preserved by first-seen (and overrides go to the longer
    translation's position — minor; flashcards iterate by sorted
    index, not original order).

    Note: this compares the SUM of every POS's tranCn across the two
    candidate entries, not just trans[0] — since prune_entry now keeps
    the full trans[], comparing only the first would discard entries
    that happen to have a short trans[0] but a long trans[1].
    """
    def total_tran(entry: dict) -> int:
        return sum(len(_tran_cn(t)) for t in _trans_list(entry))

    seen: dict[str, dict] = {}
    for src in sources:
        if not src.exists():
            print(f"  warn: skip missing source {src}", file=sys.stderr)
            continue
        for entry in load(src):
            k = dedup_key(entry)
            if not k:
                # empty/missing headWord — drop defensively rather than
                # risking a single "" entry slipping through all 11
                # categories' lookup map.
                continue
            if k not in seen:
                seen[k] = entry
                continue
            if total_tran(entry) > total_tran(seen[k]):
                seen[k] = entry
    return list(seen.values())


def subtract(items: list[dict], *removal_sources: Path) -> list[dict]:
    """Remove items whose headWord (lowercased) appears in any of the
    removal sources. The removal sets are unioned across all sources —
    e.g. CET-8 cascade bans headWords from primary ∪ junior ∪ junior-1
    ∪ senior ∪ senior-1 ∪ senior-2 ∪ cet4 ∪ cet4-1 ∪ cet6."""
    banned: set[str] = set()
    for src in removal_sources:
        if not src.exists():
            print(f"  warn: skip missing removal source {src}", file=sys.stderr)
            continue
        for entry in load(src):
            k = dedup_key(entry)
            if k:
                banned.add(k)
    return [e for e in items if dedup_key(e) not in banned]


# --- Pipeline ---------------------------------------------------------------

def run(src: Path, dst: Path) -> dict[str, int]:
    """Execute the full 11-category pipeline. Returns a
    ``{asset_name: final_count}`` mapping for the verification report."""
    s = lambda name: src / SRC_FILES[name]   # noqa: E731 — short alias

    # Stage 1: 5 verbatim copies (no source-side dedup needed — primary
    # has 108 unique-enough entries; KaoYan / TOEFL / GRE / IELTS / BEC
    # come straight from single source files). NOTE: the original plan
    # said "TOEFL 保持不变" for the categories list, but on 2026-07-03
    # we extended pruning to toefl too — it's a single-source file with
    # the same runtime-irrelevant heavy fields (sentences, etc.) as the
    # others, so trimming it is on-policy.
    primary_items = load(s("primary"))
    kaoyan_items = load(s("kaoyan"))
    toefl_items  = load(s("toefl"))
    gre_items    = load(s("gre"))
    ielts_items  = load(s("ielts"))
    bec_items    = load(s("bec"))

    # Stage 2: 3 internal merges.
    junior_items = merge(s("junior_0"), s("junior_1"))
    senior_items = merge(s("senior_0"), s("senior_1"), s("senior_2"))
    cet4_items   = merge(s("cet4_0"),   s("cet4_1"))

    # Stage 3: 2 cascade subtractions. Order matches the user's intent:
    # CET-6 loses only primary ∪ junior ∪ cet4; CET-8 also loses senior
    # ∪ cet6 (which is already itself reduced).
    cet6_items = subtract(
        load(s("cet6")),
        s("primary"),
        s("junior_0"), s("junior_1"),
        s("cet4_0"),   s("cet4_1"),
    )
    cet8_items = subtract(
        load(s("cet8")),
        s("primary"),
        s("junior_0"), s("junior_1"),
        s("senior_0"), s("senior_1"), s("senior_2"),
        s("cet4_0"),   s("cet4_1"),
        s("cet6"),                # already-cascaded cet6 headWords
    )

    # Stage 4: prune to runtime-needed fields, then write 11 target
    # assets. Pruning happens AFTER dedup so the "longer translation
    # wins" tiebreaker still sees the full trans[].
    targets: dict[str, list[dict]] = {
        "vocab_primary.json": prune_list(primary_items),
        "vocab_junior.json":  prune_list(junior_items),
        "vocab_senior.json":  prune_list(senior_items),
        "vocab_cet4.json":    prune_list(cet4_items),
        "vocab_cet6.json":    prune_list(cet6_items),
        "vocab_cet8.json":    prune_list(cet8_items),
        "vocab_kaoyan.json":  prune_list(kaoyan_items),
        "vocab_toefl.json":   prune_list(toefl_items),
        "vocab_gre.json":     prune_list(gre_items),
        "vocab_ielts.json":   prune_list(ielts_items),
        "vocab_bec.json":     prune_list(bec_items),
    }
    counts: dict[str, int] = {}
    for name, items in targets.items():
        write(items, dst / name)
        counts[name] = len(items)
    # Stage 5: emit the manifest with each category's post-prune size.
    # The runtime uses `size` to render the picker's "N 词" chip
    # without having to parse the entries — see
    # [com.echoling.app.data.repository.DictionaryRepositoryImpl].
    existing = read_existing_manifest(dst)
    write_manifest(dst, counts, existing)
    return counts


# --- CLI ---------------------------------------------------------------------

def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Build 11 deduped vocab JSON assets from source wordbooks.",
    )
    parser.add_argument("--source", type=Path, default=DEFAULT_SRC,
                        help="Source directory of raw vocab JSONs.")
    parser.add_argument("--dest", type=Path, default=DEFAULT_DST,
                        help="Destination directory for app/src/main/assets/.")
    args = parser.parse_args(list(argv) if argv is not None else None)

    if not args.source.is_dir():
        print(f"error: source dir not found: {args.source}", file=sys.stderr)
        return 1

    print(f"Source: {args.source}")
    print(f"Dest:   {args.dest}")
    counts = run(args.source, args.dest)

    # Pretty verification table — match the format expected by the plan.
    print()
    print(f"{'asset':<22}{'count':>10}{'size (KB)':>12}")
    print("-" * 44)
    for name, n in counts.items():
        path = args.dest / name
        size_kb = path.stat().st_size / 1024
        print(f"{name:<22}{n:>10}{size_kb:>12.1f}")
    manifest_path = args.dest / MANIFEST_FILENAME
    if manifest_path.exists():
        size_kb = manifest_path.stat().st_size / 1024
        print(f"{MANIFEST_FILENAME:<22}{'(meta)':>10}{size_kb:>12.1f}")
    print("-" * 44)
    print(f"{'(11 + manifest)':<22}{sum(counts.values()):>10}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
