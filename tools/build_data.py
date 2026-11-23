#!/usr/bin/env python3
"""Genera app/src/main/assets/es_words.txt y es_bigrams.txt.
Fuentes: wordfreq (léxico y frecuencias), NLTK cess_esp + conll2002 (bigramas), seed_es.txt (frases cotidianas)."""
import re, sys, collections, os
from wordfreq import top_n_list, word_frequency
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')
os.makedirs(OUT, exist_ok=True)
rx = re.compile(r'^[a-záéíóúüñ]+$')
ONE = {'a','y','o','e','u'}
words = {}
for w in top_n_list('es', 66000):
    if not rx.match(w) or len(w) > 24: continue
    if len(w) == 1 and w not in ONE: continue
    if len(w) == 2 and w not in {'de','la','el','en','un','es','se','lo','no','me','te','su','al','le','mi','tu','yo','ya','si','sí','ha','he','ir','da','di','va','ve','ni','mí','tú','él','vi','fe','ay','oh'}: continue
    words[w] = int(round(word_frequency(w, 'es') * 1e9))
proper = [l.strip() for l in open(os.path.join(os.path.dirname(__file__), 'proper_nouns.txt'), encoding='utf8') if l.strip()]
for p in proper:
    low = p.lower()
    f = words.pop(low, None) or int(word_frequency(low, 'es') * 1e9) or 200
    words[p] = max(f // 3, 200)  # nombre propio: sólo gana si la secuencia encaja
# alias: 'buenos' es palabra común, no propia
if 'Buenos' in words: words['buenos'] = words.pop('Buenos') * 3
lower_index = {}
for w, f in words.items():
    lower_index.setdefault(w.lower(), []).append(w)
entries = sorted(words.items(), key=lambda kv: -kv[1])
with open(os.path.join(OUT, 'es_words.txt'), 'w', encoding='utf8') as f:
    for w, c in entries: f.write(f'{w}\t{max(c,1)}\n')
print('words', len(entries))

def canon(tok):
    t = tok.lower()
    return t if t in lower_index else None

bi = collections.Counter()
def feed(sent, weight=1):
    prev = '<s>'
    for tok in sent:
        t = canon(tok)
        if t is None:
            prev = '<s>' if tok in '.;:!?' else prev  # puntuación abre contexto nuevo; palabras raras se saltan
            if tok.isalpha(): prev = '<unk>'
            continue
        if prev != '<unk>': bi[(prev, t)] += weight
        prev = t
import nltk
from nltk.corpus import cess_esp
for sent in cess_esp.sents():
    toks = []
    for t in sent:
        toks.extend(t.split('_'))
    feed(toks)
cnt = 0
p = os.path.expanduser('~/nltk_data/corpora/conll2002')
for fn in ('esp.train', 'esp.testa', 'esp.testb'):
    path = os.path.join(p, fn)
    if not os.path.exists(path): continue
    sent = []
    for line in open(path, encoding='latin-1'):
        parts = line.split()
        if not parts:
            feed(sent); sent = []; continue
        sent.append(parts[0])
    if sent: feed(sent)
for line in open(os.path.join(os.path.dirname(__file__), 'seed_es.txt'), encoding='utf8'):
    feed(line.split(), weight=4)
with open(os.path.join(OUT, 'es_bigrams.txt'), 'w', encoding='utf8') as f:
    n = 0
    for (a, b), c in bi.most_common():
        if c < 2: break
        f.write(f'{a} {b} {c}\n'); n += 1
print('bigrams', n)
