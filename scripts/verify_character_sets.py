#!/usr/bin/env python3
"""
verify_character_sets.py

字库数据完整性校验。可在本地与 CI 运行（无第三方依赖）。

为什么需要它
------------
字库是"数据即代码"的部分，出问题不会编译失败、只在运行时表现异常：

1. **顺序不能变**。v1.5.0 起进度按"内容键"（汉字本身）存储，但**从 v1.4.4 升级时**
   仍要用「位置 id」翻译一次旧数据。翻译依赖"当前顺序与 v1.4.4 一致"——
   一旦有人插字/重排，老用户的进度就会静默错位到别的字上。
   本脚本用一份顺序基线指纹把这件事钉死。
2. **数量与去重**。简单 1200 / 中等 1000 / 困难 800，共 3000；不得有重复字。
3. **词组/例句覆盖**。`scripts/fill_easy_words.py` 负责补齐，这里校验结果：
   每个有词组的条目，词组与例句都必须带非空拼音（否则卡片会出现空白读音）。
4. **不能有空对象**。历史上出现过 44 个 `{"char":"X"}` —— 既没内容又把原格式改写了一遍。

用法
----
    python scripts/verify_character_sets.py            # 校验
    python scripts/verify_character_sets.py --update   # 人工确认顺序变更后，刷新基线指纹
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = Path(__file__).resolve().parent.parent
JSON_PATH = ROOT / "app" / "src" / "main" / "assets" / "character_sets.json"
BASELINE_PATH = ROOT / "scripts" / "character_sets_order.sha256"

EXPECTED_COUNTS = {"easy": 1200, "medium": 1000, "hard": 800}
MIN_WORDS_COVERED = 1200  # 前 N 个简单字应具备词组与例句

# 经人工确认「找不到任何适合 3~6 岁幼儿的常用词组」的字。
# 这些字在现代汉语里只有书面语/负面/姓氏用法(毋庸、自愧弗如、奴隶、囚禁、弘扬),
# 不编造词条是对内容质量的保护,因此单列为允许缺失。
KNOWN_NO_WORDS = {
    # 第一批确认(现代汉语只有书面/负面/姓氏用法)
    "毋", "弗", "奴", "囚", "弘",
    # 第四批确认。分三类:
    # ① 负面或成人义,不适合幼儿:死 杀 血 伤 奸 邪 伪 妄 讼 刑
    # ② 文言/生僻,只出现在书面语或成语里:亦 岂 汝 迄 忖 讳 迂 夷 旨 廷 吏 玑 吁 讽 牟
    #    (兆/旬 虽常见但义项抽象,如"预兆""上旬",对 3~6 岁不适用)
    # ③ 生僻姓氏用字或词义抽象:伦 邦 仲 贞 妃 执 劣 巩 朽 匈
    "死", "杀", "血", "伤", "奸", "邪", "伪", "妄", "讼", "刑",
    "亦", "岂", "汝", "迄", "忖", "讳", "迂", "兆", "夷", "旬",
    "旨", "廷", "吏", "玑", "吁", "讽", "牟",
    "伦", "邦", "仲", "贞", "妃", "执", "劣", "巩", "朽", "匈",
    # 第五批确认(easy 601~1200 补内容时发现):含义或语境不适合 4~6 岁
    # 妓(歌妓) 坟(坟墓) 隶(奴隶) 怖(恐怖) 劫(打劫) 歼(歼灭) 役(服役) 扼(扼要,过于抽象)
    "妓", "坟", "隶", "怖", "劫", "歼", "役", "扼",
}


def char_of(item) -> str:
    return item.strip() if isinstance(item, str) else str(item.get("char", "")).strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--update", action="store_true", help="刷新顺序基线指纹")
    args = parser.parse_args()

    data = json.loads(JSON_PATH.read_text(encoding="utf-8"))
    problems: list[str] = []

    # ---- 1. 数量 ----
    for key, expected in EXPECTED_COUNTS.items():
        actual = len(data.get(key, []))
        if actual != expected:
            problems.append(f"{key} 数量为 {actual}，期望 {expected}")

    # ---- 2. 顺序 + 去重 ----
    seq = [char_of(it) for k in ("easy", "medium", "hard") for it in data.get(k, [])]
    digest = hashlib.sha256("\n".join(seq).encode("utf-8")).hexdigest()

    if len(set(seq)) != len(seq):
        dups = sorted({c for c in seq if seq.count(c) > 1})
        problems.append(f"存在重复汉字 {len(dups)} 个：{''.join(dups[:20])}")

    if args.update:
        BASELINE_PATH.write_text(digest + "\n", encoding="utf-8")
        print(f"已刷新顺序基线：{digest}")
    elif BASELINE_PATH.exists():
        expected_digest = BASELINE_PATH.read_text(encoding="utf-8").strip()
        if digest != expected_digest:
            problems.append(
                "**字库顺序发生了变化**（这会让 v1.4.4 升级用户的进度错位）：\n"
                f"    基线 {expected_digest[:16]}…  现在 {digest[:16]}…\n"
                "    若确为有意调整，请同时提供数据迁移方案并运行 --update"
            )
    else:
        BASELINE_PATH.write_text(digest + "\n", encoding="utf-8")
        print(f"[info] 首次运行，已写入顺序基线 {digest}")

    # ---- 3. 空对象 ----
    empties = [
        f"{k}#{i}:{char_of(it)}"
        for k in ("easy", "medium", "hard")
        for i, it in enumerate(data.get(k, []))
        if isinstance(it, dict) and not it.get("words") and not it.get("examples")
    ]
    if empties:
        problems.append(f"存在 {len(empties)} 个既无词组也无例句的空对象：{' '.join(empties[:15])}")

    # ---- 4. 词组/例句的拼音完整性 ----
    missing_pinyin = []
    for k in ("easy", "medium", "hard"):
        for i, it in enumerate(data.get(k, [])):
            if not isinstance(it, dict):
                continue
            for w in it.get("words") or []:
                if not str(w.get("word", "")).strip() or not str(w.get("pinyin", "")).strip():
                    missing_pinyin.append(f"{k}#{i}:{char_of(it)} 词组")
                    break
            for e in it.get("examples") or []:
                if not str(e.get("sentence", "")).strip() or not str(e.get("pinyin", "")).strip():
                    missing_pinyin.append(f"{k}#{i}:{char_of(it)} 例句")
                    break
    if missing_pinyin:
        problems.append(f"{len(missing_pinyin)} 处词条缺拼音：{' '.join(missing_pinyin[:10])}")

    # ---- 4b. 词条必须包含目标字（幼儿识字的关键）----
    not_containing: list[str] = []
    for k in ("easy", "medium", "hard"):
        for i, it in enumerate(data.get(k, [])):
            if not isinstance(it, dict):
                continue
            ch = char_of(it)
            if not ch:
                continue
            for w in it.get("words") or []:
                if ch not in str(w.get("word", "")):
                    not_containing.append(f"{k}#{i}:{ch} 词组「{w.get('word')}」")
            for e in it.get("examples") or []:
                if ch not in str(e.get("sentence", "")):
                    not_containing.append(f"{k}#{i}:{ch} 例句「{e.get('sentence')}」")
    if not_containing:
        problems.append(
            f"{len(not_containing)} 处词条不含目标字：{' '.join(not_containing[:8])}"
        )

    # ---- 5. 前 N 个简单字的覆盖 ----
    easy = data.get("easy", [])
    expected_cover = MIN_WORDS_COVERED - len(KNOWN_NO_WORDS)
    covered = sum(1 for it in easy[:MIN_WORDS_COVERED] if isinstance(it, dict) and it.get("words"))
    total_covered = sum(1 for it in easy if isinstance(it, dict) and it.get("words"))
    if covered < expected_cover:
        missing = [
            char_of(it) for it in easy[:MIN_WORDS_COVERED]
            if not (isinstance(it, dict) and it.get("words")) and char_of(it) not in KNOWN_NO_WORDS
        ]
        problems.append(
            f"前 {MIN_WORDS_COVERED} 个简单字只有 {covered} 个有词组(应≥{expected_cover})，缺：{''.join(missing)}"
        )

    print(f"汉字总数            : {len(seq)}")
    print(f"顺序指纹            : {digest[:16]}…")
    print(f"前 {MIN_WORDS_COVERED} 简单字有词组  : {covered}/{expected_cover}（另有 {len(KNOWN_NO_WORDS)} 字确认无合适幼儿词组）")
    print(f"全库有词组的条目    : {total_covered}")
    # v1.6.0:分桶报告覆盖度 —— 词组属于内容创作,还剩多少必须一眼可见,
    # 避免"以为做完了"或"以为漏了"两种误判。
    _easy = data.get("easy", [])
    for _label, _seg in (
        ("简单 1~600", _easy[:600]),
        ("简单 601~1200", _easy[600:1200]),
        ("中等 1000", data.get("medium", [])),
        ("困难 800", data.get("hard", [])),
    ):
        _done = sum(1 for it in _seg if isinstance(it, dict) and it.get("words"))
        print(f"  词组覆盖 {_label:14}: {_done}/{len(_seg)}")

    # ---- 6. 英文词库 ----
    en_path = ROOT / "app" / "src" / "main" / "assets" / "english_sets.json"
    if en_path.exists():
        en = json.loads(en_path.read_text(encoding="utf-8"))
        letters = en.get("letters", [])
        words = en.get("words", [])
        LETTER_FIELDS = {"letter", "uppercase", "lowercase", "phonetic",
                         "exampleWord", "exampleWordChinese"}
        WORD_FIELDS = {"word", "phonetic", "chineseMeaning", "exampleSentence",
                       "exampleSentenceTranslation", "difficulty"}
        seen_en: set[str] = set()
        for i, w in enumerate(words):
            if set(w.keys()) != WORD_FIELDS:
                problems.append(f"英文单词#{i} 字段不符: {sorted(w.keys())}")
            if w["word"] in seen_en:
                problems.append(f"英文单词重复: {w['word']}")
            seen_en.add(w["word"])
            if not (str(w["phonetic"]).startswith("/") and str(w["phonetic"]).endswith("/")):
                problems.append(f"英文单词「{w['word']}」音标缺斜杠: {w['phonetic']}")
            if str(w["word"]).lower() not in str(w["exampleSentence"]).lower():
                problems.append(f"英文单词「{w['word']}」例句不含该词")
            if w.get("difficulty") not in ("EASY", "MEDIUM", "HARD"):
                problems.append(f"英文单词「{w['word']}」难度非法: {w.get('difficulty')}")
            blanks = [f for f in WORD_FIELDS if not str(w.get(f, "")).strip()]
            if blanks:
                problems.append(f"英文单词「{w['word']}」字段为空: {blanks}")
        for i, l in enumerate(letters):
            if set(l.keys()) != LETTER_FIELDS:
                problems.append(f"英文字母#{i} 字段不符: {sorted(l.keys())}")
                continue
            # v1.6.0:字段齐全还不够,还要保证**值非空** ——
            # 空音标/空示例词在界面上就是一块空白,用户会当成 bug
            blanks = [f for f in LETTER_FIELDS if not str(l.get(f, "")).strip()]
            if blanks:
                problems.append(f"英文字母「{l.get('letter')}」字段为空: {blanks}")
        dist: dict[str, int] = {}
        for w in words:
            dist[w.get("difficulty")] = dist.get(w.get("difficulty"), 0) + 1
        print(f"英文字母            : {len(letters)}")
        print(f"英文单词            : {len(words)}  按难度 {dist}")
        if len(words) < 100:
            problems.append(f"英文单词仅 {len(words)} 个,对幼儿英语启蒙偏少(建议 ≥ 100)")
    print()

    if problems:
        print("校验未通过：", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        return 1

    print("校验通过 ✓")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
