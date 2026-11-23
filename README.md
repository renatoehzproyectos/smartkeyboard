# Smart Keyboard

Teclado Android nativo (Kotlin) en español que interpreta letras escritas sin espacios,
predice antes de terminar la palabra y reconstruye frases. Todo local, sin internet.

## Compilar (sin Android Studio)
1. Sube este directorio a un repositorio de GitHub (rama `main`).
2. El workflow `.github/workflows/build.yml` se ejecuta solo en cada push (o a mano: *Actions → Build APK → Run workflow*).
3. Descarga el APK desde *Artifacts → smartkeyboard-debug-apk* (`app/build/outputs/apk/debug/*.apk`).
4. Instálalo, abre la app y sigue los pasos: activar el teclado → seleccionarlo.

## Motor (`engine/`)
- `Lexicon`: Trie compacto, 65k palabras (wordfreq) + nombres propios; top-32 completaciones por prefijo.
- `WordSegmenter`: candidatos por tramo: palabra completa, prefijo (palabra incompleta), errores de tecleo
  (letra duplicada/extra/omitida/vecina/transpuesta) o literal desconocida.
- `BeamSearch`: una capa por letra (incremental); borrar descarta una capa; reinterpreta palabras anteriores.
  Admite palabras implícitas («construir [una] aplicación») sólo si el contexto las respalda por ambos lados.
- `LanguageModel`: bigramas interpolados (NLTK cess_esp + conll2002 + frases cotidianas `tools/seed_es.txt`).
- `PredictionEngine`: S = αL + βC + γP + δU (pesos en `EngineConfig`).

## Datos
`python3 tools/build_data.py` regenera `app/src/main/assets/es_*.txt` (requiere `pip install wordfreq nltk`).

## Firma release
El APK debug es instalable. Para distribuir hay que crear un keystore y configurar `signingConfigs` con secretos de GitHub.
