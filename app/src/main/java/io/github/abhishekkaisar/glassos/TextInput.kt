package io.github.abhishekkaisar.glassos.personal

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * A glass text field backed by a real [EditText], so the TV's own keyboard (Gboard, Fire TV's keyboard) and the
 * keyboard in the Android TV remote app on a phone can type into it, and text can be pasted. A white outline
 * shows when it has focus. OK opens the keyboard; the keyboard's Done key calls [onDone].
 */
class TextInput(ctx: Context, hintText: String, maxLength: Int, kind: Kind, sizeDp: Float = if (kind == Kind.KEY) 16f else 22f) : EditText(ctx) {
    /** NAME: capitalised words. KEY: no spaces. PLAIN: as typed (subreddit names). ARTIST: capitalised words, free text. */
    enum class Kind { NAME, KEY, PLAIN, ARTIST }

    var onDone: (() -> Unit)? = null
    private val shape = GradientDrawable().apply { cornerRadius = ctx.dp(23f); setColor(0x26FFFFFF) }

    init {
        background = shape
        typeface = Fonts.regular
        setTextSize(TypedValue.COMPLEX_UNIT_PX, ctx.dp(sizeDp))
        setTextColor(Color.WHITE)
        setHintTextColor(0x80FFFFFF.toInt())
        hint = hintText
        setHighlightColor(0x55FFFFFF)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ctx.dpi(22f), 0, ctx.dpi(22f), 0)
        isFocusable = true
        isFocusableInTouchMode = true
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        if (Build.VERSION.SDK_INT >= 29) textCursorDrawable = GradientDrawable().apply { setColor(Color.WHITE); setSize(ctx.dpi(2f), 0) }
        val filters = ArrayList<InputFilter>()
        filters.add(InputFilter.LengthFilter(maxLength))
        val base = InputType.TYPE_CLASS_TEXT
        when (kind) {
            Kind.NAME, Kind.ARTIST -> inputType = base or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            Kind.PLAIN -> inputType = base or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            Kind.KEY -> inputType = base or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        if (kind == Kind.KEY || kind == Kind.PLAIN) {
            // No spaces or line breaks in an API key or a subreddit name; drop them when pasted or sent from a phone.
            filters.add(InputFilter { src, s, e, _, _, _ ->
                if ((s until e).none { src[it].isWhitespace() }) null
                else src.subSequence(s, e).filterNot { it.isWhitespace() }
            })
        }
        setFilters(filters.toTypedArray())
        setSingleLine(true)
        setOnClickListener { showKeyboardSoon(0) }
        setOnEditorActionListener { _, id, ev ->
            val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_ACTION_NEXT || enter) { onDone?.invoke(); true } else false
        }
        outlineFocus(false)
    }

    private fun outlineFocus(on: Boolean) { shape.setStroke(context.dpi(2f), if (on) 0xE6FFFFFF.toInt() else 0) }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        outlineFocus(gained)
    }

    /** Asks the system keyboard to open for this field once the window has settled. */
    fun showKeyboardSoon(delay: Long = 350) {
        postDelayed({
            if (!isAttachedToWindow) return@postDelayed
            requestFocus()
            try { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, 0) } catch (e: Throwable) { }
        }, delay)
    }

    fun hideKeyboard() {
        val t = windowToken ?: return
        try { (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(t, 0) } catch (e: Throwable) { }
    }
}
