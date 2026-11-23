package com.smartkeyboard.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

/** Comunicación con la aplicación activa a través de InputConnection. */
class InputController(private val service: InputMethodService) : InputSink {
    private val ic get() = service.currentInputConnection

    override fun showComposing(text: String) { ic?.setComposingText(text, 1) }
    override fun commit(text: String) { ic?.commitText(text, 1) }
    override fun finishComposing() { ic?.finishComposingText() }
    override fun deleteBefore(n: Int) { ic?.deleteSurroundingText(n, 0) }
    override fun textBefore(n: Int): String = ic?.getTextBeforeCursor(n, 0)?.toString() ?: ""
    override fun sendBackspace() { service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }

    override fun sendEnter() {
        val ei = service.currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = (ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (ei != null && !noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic?.performEditorAction(action)
        } else service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    override fun autoCapitalize(): Boolean {
        val ei = service.currentInputEditorInfo ?: return false
        if (ei.inputType and InputType.TYPE_MASK_CLASS == 0) return false
        return (ic?.getCursorCapsMode(ei.inputType) ?: 0) != 0
    }
}
