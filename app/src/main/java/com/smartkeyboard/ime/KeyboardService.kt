package com.smartkeyboard.ime

import android.content.Intent
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.inputmethodservice.InputMethodService.Insets
import com.smartkeyboard.DebugLog
import com.smartkeyboard.EngineHolder
import com.smartkeyboard.Prefs
import com.smartkeyboard.SmartKeyboardApp
import com.smartkeyboard.engine.EngineConfig
import com.smartkeyboard.engine.PredictionEngine
import com.smartkeyboard.ui.KeyboardView
import com.smartkeyboard.ui.SettingsActivity
import com.smartkeyboard.engine.Lexicon
import com.smartkeyboard.engine.NgramLanguageModel
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class KeyboardService : InputMethodService(), KeyboardView.Listener {
    private lateinit var manager: CompositionManager
    private val ui = Handler(Looper.getMainLooper())
    private var keyboard: KeyboardView? = null
    private lateinit var controller: InputController
    private var engine: PredictionEngine? = null

    // Última selección notificada, para distinguir ediciones propias de movimientos del cursor.
    private var selStart = 0; private var selEnd = 0; private var candStart = -1; private var candEnd = -1
    private var checkPending = false

    override fun onCreate() {
        super.onCreate()
        controller = InputController(this)
        manager = CompositionManager(controller)
        val learning = SmartKeyboardApp.of(this).learning
        manager.learner = learning
        manager.onSuggestions = { keyboard?.setSuggestions(it) }
        manager.log = { DebugLog.add(it) }
        thread(name = "sk-load") {
            runCatching {
                val words = assets.open("es_words.txt")
                val lex = Lexicon.fromStream(words)
                val lm = NgramLanguageModel(lex, assets.open("es_bigrams.txt"))
                val beam = Prefs.get(this).getInt(Prefs.BEAM, EngineConfig().beamWidth)
                val e = PredictionEngine(lex, lm, learning, EngineConfig(beamWidth = beam))
                EngineHolder.lexicon = lex
                learning.onVocabularyChanged = { v -> ui.post { engine?.setUserLexicon(v) } }
                learning.load()
                ui.post { engine = e; manager.engine = e }
            }
        }
    }

    override fun onCreateInputView(): View {
        val k = KeyboardView(this).also { it.listener = this }
        keyboard = k
        return k
    }

    /** Teclado flotante: no redimensiona la app y solo la tarjeta recibe toques; el resto de la ventana es transparente y atraviesa. */
    override fun onEvaluateFullscreenMode() = false

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val kb = keyboard ?: return
        if (!isInputViewShown) return
        val loc = IntArray(2); kb.getLocationInWindow(loc)
        val r = kb.cardRect
        val windowH = window.window?.decorView?.height ?: (loc[1] + kb.height)
        outInsets.contentTopInsets = windowH
        outInsets.visibleTopInsets = windowH
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(loc[0] + r.left.toInt(), loc[1] + r.top.toInt(), loc[0] + r.right.toInt(), loc[1] + r.bottom.toInt())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboard?.applyTheme()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val prefs = Prefs.get(this)
        val field = FieldPolicy.classify(info.inputType, info.imeOptions)
        val predict = field.predict && prefs.getBoolean(Prefs.PREDICTIONS, true)
        val learn = field.learn && prefs.getBoolean(Prefs.LEARNING, true)
        manager.policy = FieldPolicy(predict, learn)
        val dbg = prefs.getBoolean(Prefs.DEBUG, false)
        DebugLog.enabled = dbg; manager.debug = dbg; keyboard?.debugOn = dbg
        DebugLog.add("CAMPO inputType=0x${Integer.toHexString(info.inputType)} ime=0x${Integer.toHexString(info.imeOptions)} app=${if (predict || learn) info.packageName else "(protegido)"} predecir=$predict aprender=$learn reinicio=$restarting")
        val manual = prefs.getBoolean(Prefs.MANUAL, false)
        manager.setManual(manual); keyboard?.manualMode = manual
        (manager.learner as? com.smartkeyboard.learning.LearningEngine)?.enabled = prefs.getBoolean(Prefs.LEARNING, true)
        engine?.beamWidth = prefs.getInt(Prefs.BEAM, 12)
        keyboard?.haptics = prefs.getBoolean(Prefs.HAPTIC, true)
        keyboard?.enterLabel = enterLabelFor(info)
        keyboard?.setAutoShift(controller.autoCapitalize())
        keyboard?.resetState()
        if (!restarting) manager.reset()
        keyboard?.setSuggestions(emptyList())
        updatePreview(); schedulePreview()
    }

    private fun enterLabelFor(info: EditorInfo): String? = when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
        EditorInfo.IME_ACTION_SEARCH -> "🔍"
        EditorInfo.IME_ACTION_SEND -> "Enviar"
        EditorInfo.IME_ACTION_GO -> "Ir"
        EditorInfo.IME_ACTION_NEXT -> "Sig."
        EditorInfo.IME_ACTION_DONE -> "OK"
        else -> null
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        manager.onExternalChange()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart; selEnd = newSelEnd; candStart = candidatesStart; candEnd = candidatesEnd
        schedulePreview()
        if (manager.composing && !checkPending && (candidatesStart < 0 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            // Las notificaciones llegan con retraso: se vuelve a comprobar con el estado más reciente antes de abandonar la composición.
            checkPending = true
            ui.postDelayed({
                checkPending = false
                if (manager.composing && (candStart < 0 || selStart != candEnd || selEnd != candEnd)) manager.onExternalChange()
            }, 90)
        }
    }

    // ---- KeyboardView.Listener ----
    private fun afterKey() { keyboard?.setAutoShift(controller.autoCapitalize()); updatePreview(); schedulePreview() }

    private val previewTask = Runnable { updatePreview() }
    /** Segunda lectura breve después: las ediciones llegan al campo con un pequeño retraso. */
    private fun schedulePreview() { ui.removeCallbacks(previewTask); ui.postDelayed(previewTask, 70) }

    private fun updatePreview() {
        val kb = keyboard ?: return
        val enabled = Prefs.get(this).getBoolean(Prefs.PREVIEW, true)
        if (!enabled || !manager.previewAllowed) { kb.setPreview("", 0, 0, false); return }
        val (solid, ghost) = manager.composingParts
        val comp = solid + ghost
        val before = controller.textBefore(80)
        val tail = (if (comp.isNotEmpty() && before.endsWith(comp)) before.dropLast(comp.length) else before).substringAfterLast('\n')
        val full = tail + comp
        val cut = max(0, full.length - 60)
        val text = full.substring(cut)
        val compStart = max(0, tail.length - cut)
        kb.setPreview(text, compStart, min(text.length, compStart + solid.length), true)
    }

    override fun onChar(c: Char, shifted: Boolean) {
        if (com.smartkeyboard.engine.TextNorm.isTypable(c)) manager.onLetter(c, shifted) else manager.onPunctuation(c.toString())
        afterKey()
    }
    override fun onText(text: String) { manager.onPunctuation(text); afterKey() }
    override fun onSpace() { manager.onSpace(); afterKey() }
    override fun onBackspace() { manager.onBackspace(); afterKey() }
    override fun onEnter() { manager.onEnter(); afterKey() }
    override fun onSuggestion(index: Int) { manager.onSuggestion(index); afterKey() }
    override fun onToggleManual() {
        val on = !manager.manual
        manager.setManual(on)
        keyboard?.manualMode = on
        Prefs.get(this).edit().putBoolean(Prefs.MANUAL, on).apply()
        afterKey()
    }
    override fun onDebug() {
        val prefs = Prefs.get(this)
        if (!DebugLog.enabled) {
            DebugLog.clear(); DebugLog.enabled = true; manager.debug = true
            prefs.edit().putBoolean(Prefs.DEBUG, true).apply()
            keyboard?.debugOn = true
            DebugLog.add("INICIO del registro")
            android.widget.Toast.makeText(this, "Registro iniciado. Escribe y toca 🐞 de nuevo para copiarlo.", android.widget.Toast.LENGTH_LONG).show()
        } else {
            val n = DebugLog.copy(this)
            android.widget.Toast.makeText(this, "Registro copiado ($n eventos). Desactívalo en Ajustes.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onHide() { manager.onExternalChange(); requestHideSelf(0) }
    override fun onSwitchKeyboard() {
        manager.onExternalChange()
        if (!switchToNextInputMethod(false)) (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
    }
    override fun onOpenSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onDestroy() {
        manager.onExternalChange()
        super.onDestroy()
    }
}
