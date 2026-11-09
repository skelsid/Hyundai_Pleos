package dev.vehiclelog.ui

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 차량 화면용 큰 글씨·큰 버튼. 레이아웃 XML 없이 코드로 만든다. */
internal object Ui {
    private const val TITLE_SP = 34f
    private const val BODY_SP = 26f
    private const val BUTTON_SP = 28f
    private const val PADDING_PX = 40
    private const val GAP_PX = 16
    private const val BUTTON_MIN_HEIGHT_PX = 130
    private const val BUTTON_MAX_LINES = 4

    fun screen(context: Context, content: LinearLayout): ScrollView =
        ScrollView(context).apply { addView(content, MATCH_PARENT, WRAP_CONTENT) }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(PADDING_PX, PADDING_PX, PADDING_PX, PADDING_PX)
    }

    /** 자식들을 같은 너비로 나란히 놓는다. */
    fun row(context: Context, vararg children: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        for (child in children) {
            addView(child, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { setMargins(GAP_PX / 2, 0, GAP_PX / 2, 0) })
        }
        layoutParams = spaced()
    }

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = TITLE_SP
        layoutParams = spaced()
    }

    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = BODY_SP
        layoutParams = spaced()
    }

    fun button(context: Context, text: String, onClick: () -> Unit): Button = Button(context).apply {
        this.text = text
        textSize = BUTTON_SP
        isAllCaps = false
        // 기본 버튼 스타일은 한 줄만 보여 줘서 여러 줄 라벨(월별 목록)이 잘린다
        isSingleLine = false
        maxLines = BUTTON_MAX_LINES
        minHeight = BUTTON_MIN_HEIGHT_PX
        layoutParams = spaced()
        setOnClickListener { onClick() }
    }

    fun input(context: Context, hint: String, value: String = "", caps: Boolean = false): EditText = EditText(context).apply {
        this.hint = hint
        setText(value)
        textSize = BODY_SP
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or (if (caps) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0)
        layoutParams = spaced()
    }

    private fun spaced() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, GAP_PX, 0, GAP_PX) }
}
