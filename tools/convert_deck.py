"""Turn the pasted 90-day vocabulary table into app/src/main/assets/starter_deck.csv.

The source uses markdown table rows:

    | term | meaning<br><br><i>example_en</i><br>example_fa | day07 |

Parentheses that only name a part of speech are moved into the pos column; other
parentheses stay part of the term. Rows are ordered by day, duplicates are dropped
and the level follows the study day (A1 for days 1-30, A2 for 31-60, B1 for 61-90).
"""
import csv
import glob
import re

SRC = sorted(glob.glob('artifacts/deck_raw/part*.txt'))
OUT = 'app/src/main/assets/starter_deck.csv'

ROW = re.compile(r'^\|\s*(?P<term>.+?)\s*\|\s*(?P<body>.+?)\s*\|\s*day(?P<day>\d+)\s*\|?\s*$')
BODY = re.compile(r'^(?P<meaning>.*?)<br><br><i>(?P<en>.*?)</i>(?:<br>\s*(?P<fa>.*))?$', re.S)
TAG = re.compile(r'\s*\(\s*(?P<tag>[^()]*)\s*\)\s*$')

POS = {
    'n': 'noun',
    'v': 'verb',
    'adj': 'adjective',
    'adv': 'adverb',
    'prep': 'preposition',
    'conj': 'conjunction',
    'pron': 'pronoun',
    'rel pron': 'pronoun',
    'exp': 'expression',
}

rows = []
for path in SRC:
    with open(path, encoding='utf-8') as handle:
        for line in handle:
            match = ROW.match(line.strip())
            if not match:
                continue
            term = match.group('term').strip()
            body = match.group('body').strip()
            day = int(match.group('day'))
            parts = BODY.match(body)
            if not parts:
                continue
            meaning = ' '.join(parts.group('meaning').split())
            example_en = ' '.join(parts.group('en').split())
            example_fa = ' '.join((parts.group('fa') or '').split())
            if not term or not meaning or not example_en:
                continue
            if len(example_en.split()) > 16:
                continue

            pos = ''
            tag = TAG.search(term)
            if tag and tag.group('tag').strip().lower() in POS:
                pos = POS[tag.group('tag').strip().lower()]
                term = term[: tag.start()].strip()

            level = 'A1' if day <= 30 else 'A2' if day <= 60 else 'B1'
            rows.append((day, term, meaning, pos, example_en, example_fa, level))

rows.sort(key=lambda row: row[0])

seen = set()
out = []
for day, term, meaning, pos, example_en, example_fa, level in rows:
    key = ' '.join(term.lower().split())
    if key in seen:
        continue
    seen.add(key)
    out.append([str(len(out) + 1), term, meaning, pos, '', example_en, example_fa, level])

with open(OUT, 'w', encoding='utf-8', newline='') as handle:
    writer = csv.writer(handle, quoting=csv.QUOTE_MINIMAL)
    writer.writerow(['rank', 'term', 'meaning_fa', 'pos', 'ipa', 'example_en', 'example_fa', 'level'])
    writer.writerows(out)

levels = {}
for row in out:
    levels[row[7]] = levels.get(row[7], 0) + 1
print('parsed', len(rows), 'written', len(out), 'levels', levels)
