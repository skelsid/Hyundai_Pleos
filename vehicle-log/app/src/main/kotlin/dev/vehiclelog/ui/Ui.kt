package dev.vehiclelog.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.vehiclelog.R

/**
 * 차량 화면용 뷰. 레이아웃 XML 없이 코드로 만든다.
 * 색·글자·버튼 크기는 Pleos Connect UI 디자인 가이드의 토큰을 따른다
 * (https://document.pleos.ai/en/docs/connect/guide/docs-design/foundations/colors , typography, components/actions/basic-button).
 * 가이드 값은 px 이고 Pleos 화면 밀도가 160dpi(1dp = 1px)라 dp·sp 로 그대로 쓴다.
 * 낮/밤 테마는 시스템 설정(uiMode)을 따른다. 화면 바탕색은 res/values(-night)/colors.xml 의 background.
 * 글꼴은 Asta Sans (res/font/asta_sans.xml).
 */
internal object Ui {
    // 글자: Title Small 32/40 Bold, Body Medium 28/36 Strong(버튼), Body Small 26/34 Normal
    private const val TITLE_SP = 32f
    private const val TITLE_LINE_SP = 40f
    private const val BODY_SP = 26f
    private const val BODY_LINE_SP = 34f
    private const val BUTTON_SP = 28f
    private const val WEIGHT_NORMAL = 400
    private const val WEIGHT_STRONG = 600
    private const val WEIGHT_BOLD = 700

    // 버튼: Large 높이 100·모서리 32. 입력칸은 Small 버튼 모서리(16)에 맞춘다
    private const val BUTTON_HEIGHT_DP = 100
    private const val BUTTON_RADIUS_DP = 32f
    private const val FIELD_HEIGHT_DP = 80
    private const val FIELD_RADIUS_DP = 16f
    private const val FIELD_STROKE_DP = 2
    private const val BUTTON_MAX_LINES = 4
    private const val PADDING_DP = 40
    private const val GAP_DP = 16
    private const val BUTTON_PADDING_DP = 24

    /** Pleos 시스템 색 토큰. 밝음/어두움 값은 디자인 가이드 표의 견본에서 읽었다 (2026-11-14). */
    private class Palette(
        val textPrimary: Int, // icon/text primary
        val textSecondary: Int,
        val textHint: Int, // tertiary
        val textDisabled: Int, // quaternary
        val basicButton: Int, // button_basic_enabled
        val basicButtonPressed: Int,
        val filledButton: Int, // button_filled_enabled
        val filledButtonPressed: Int,
        val fieldBackground: Int, // field_background
        val fieldStroke: Int, // surface_accent
        val fieldFocused: Int, // field_focused (basic_700)
    )

    private val LIGHT = Palette(
        textPrimary = Color.parseColor("#131417"),
        textSecondary = Color.parseColor("#393A3C"),
        textHint = Color.parseColor("#68696B"),
        textDisabled = Color.parseColor("#B3B4B5"),
        // alpha_dark_50·100: #131417 에 불투명도 5%·10%
        basicButton = Color.argb(0x0D, 0x13, 0x14, 0x17),
        basicButtonPressed = Color.argb(0x1A, 0x13, 0x14, 0x17),
        filledButton = Color.parseColor("#44464E"),
        filledButtonPressed = Color.parseColor("#313236"),
        fieldBackground = Color.parseColor("#FFFFFF"),
        fieldStroke = Color.parseColor("#E0E1E6"),
        fieldFocused = Color.parseColor("#131417"),
    )

    private val DARK = Palette(
        textPrimary = Color.parseColor("#FFFFFF"),
        textSecondary = Color.parseColor("#D9D9DA"),
        textHint = Color.parseColor("#AAAAAB"),
        textDisabled = Color.parseColor("#5F5F61"),
        // 어두운 바탕에선 alpha_dark 견본(검정 5%)이 바탕과 구분되지 않는다.
        // 보조 버튼은 바탕보다 어두운 surface_low, 주 버튼(filled)은 바탕보다 밝게 두어 위계를 보인다
        basicButton = Color.parseColor("#131417"),
        basicButtonPressed = Color.parseColor("#313236"),
        filledButton = Color.parseColor("#44464E"),
        filledButtonPressed = Color.parseColor("#686A72"),
        fieldBackground = Color.parseColor("#131417"),
        fieldStroke = Color.parseColor("#44464E"),
        fieldFocused = Color.parseColor("#FFFFFF"),
    )

    private fun palette(context: Context): Palette {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun Context.dp(value: Float): Float = value * resources.displayMetrics.density

    fun screen(context: Context, content: LinearLayout): ScrollView =
        ScrollView(context).apply {
            setBackgroundColor(context.getColor(R.color.background))
            addView(content, MATCH_PARENT, WRAP_CONTENT)
        }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = context.dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
    }

    /** 자식들을 같은 너비로 나란히 놓는다. 양끝은 위아래 전체 너비 버튼과 맞춘다. */
    fun row(context: Context, vararg children: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        // 글자와 버튼을 한 줄에 놓을 때(사용자 목록) 글자를 버튼 가운데 높이에 맞춘다
        gravity = Gravity.CENTER_VERTICAL
        val gap = context.dp(GAP_DP)
        children.forEachIndexed { index, child ->
            addView(child, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = gap })
        }
        layoutParams = spaced(context)
    }

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = TITLE_SP
        lineHeightSp(TITLE_LINE_SP)
        font(WEIGHT_BOLD)
        setTextColor(palette(context).textPrimary)
        layoutParams = spaced(context)
    }

    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = BODY_SP
        lineHeightSp(BODY_LINE_SP)
        font(WEIGHT_NORMAL)
        setTextColor(palette(context).textSecondary)
        layoutParams = spaced(context)
    }

    /**
     * 기본 버튼(basic). filled 는 화면의 주 동작 하나에만 쓴다 (Pleos 버튼 위계: filled > basic).
     */
    fun button(context: Context, text: String, filled: Boolean = false, onClick: () -> Unit): Button =
        Button(context).apply {
            val colors = palette(context)
            this.text = text
            textSize = BUTTON_SP
            font(WEIGHT_STRONG)
            isAllCaps = false
            // 기본 버튼 스타일은 한 줄만 보여 줘서 여러 줄 라벨(월별 목록)이 잘린다
            isSingleLine = false
            maxLines = BUTTON_MAX_LINES
            minHeight = context.dp(BUTTON_HEIGHT_DP)
            minimumHeight = context.dp(BUTTON_HEIGHT_DP)
            gravity = Gravity.CENTER
            val padding = context.dp(BUTTON_PADDING_DP)
            setPadding(padding, padding / 2, padding, padding / 2)
            // 기본 버튼 그림자·눌림 높이 변화를 없앤다 (Pleos 버튼은 평면)
            stateListAnimator = null
            elevation = 0f
            val (normal, pressed) = if (filled) colors.filledButton to colors.filledButtonPressed else colors.basicButton to colors.basicButtonPressed
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(colors.textDisabled, if (filled) Color.WHITE else colors.textPrimary),
                ),
            )
            // 눌림은 물결(ripple) 대신 색만 바꾼다. 주행 중 화면에 움직임을 두지 않는다 (Pleos 주행 중 UI 원칙)
            background = StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), rounded(context, colors.basicButton, BUTTON_RADIUS_DP))
                addState(intArrayOf(android.R.attr.state_pressed), rounded(context, pressed, BUTTON_RADIUS_DP))
                addState(intArrayOf(), rounded(context, normal, BUTTON_RADIUS_DP))
            }
            layoutParams = spaced(context)
            setOnClickListener { onClick() }
        }

    fun input(context: Context, hint: String, value: String = "", caps: Boolean = false): EditText = EditText(context).apply {
        val colors = palette(context)
        this.hint = hint
        setText(value)
        textSize = BODY_SP
        font(WEIGHT_NORMAL)
        setTextColor(colors.textPrimary)
        setHintTextColor(colors.textHint)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or (if (caps) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0)
        minHeight = context.dp(FIELD_HEIGHT_DP)
        val padding = context.dp(BUTTON_PADDING_DP)
        setPadding(padding, 0, padding, 0)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), field(context, colors.fieldBackground, colors.fieldFocused))
            addState(intArrayOf(), field(context, colors.fieldBackground, colors.fieldStroke))
        }
        layoutParams = spaced(context)
    }

    private fun rounded(context: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radiusDp)
    }

    private fun field(context: Context, fill: Int, stroke: Int) = rounded(context, fill, FIELD_RADIUS_DP).apply {
        setStroke(context.dp(FIELD_STROKE_DP), stroke)
    }

    private fun TextView.lineHeightSp(sp: Float) {
        lineHeight = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt()
    }

    /** res/font/asta_sans.xml 에 묶어 둔 굵기(400·600·700·800) 중 가장 가까운 것을 고른다. */
    private fun TextView.font(weight: Int) {
        typeface = Typeface.create(resources.getFont(R.font.asta_sans), weight, false)
    }

    private fun spaced(context: Context) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        val gap = context.dp(GAP_DP)
        setMargins(0, gap / 2, 0, gap / 2)
    }
}
