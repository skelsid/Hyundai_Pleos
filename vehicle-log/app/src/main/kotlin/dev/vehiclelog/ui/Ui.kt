package dev.vehiclelog.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.vehiclelog.R

/**
 * 차량 화면용 뷰. 레이아웃 XML 없이 코드로 만든다.
 *
 * 색·글자·크기는 Pleos 공식 디자인 자산(Pleos_Connect_design_assets_215.fig)의 변수·컴포넌트 값을 그대로 쓴다.
 * - 크기: form_factor 모드 connect-s (OS 기본 16:9 디스플레이, 에뮬레이터 2560x1440). 값은 px 이고 밀도 160dpi 라 dp = px.
 * - 색: Color 변수의 Light/Dark 모드. 낮/밤은 시스템 설정(uiMode)을 따른다.
 * - 컴포넌트: ivi_basic_button(Large), ivi_text_field(Medium), ivi_title(Main/Depth), List/item.
 * 화면 바탕(background/primary)은 res/values(-night)/colors.xml, 글꼴 Asta Sans 는 res/font/asta_sans.xml.
 */
internal object Ui {
    // 글자 크기/줄높이 (Asta Sans)
    private const val TITLE_SP = 40f // ivi_title: Bold 40/52
    private const val TITLE_LINE_SP = 52f
    private const val TITLE_MAX_LINES = 2
    private const val BODY_SP = 28f // Body Medium 28/36
    private const val BODY_LINE_SP = 36f
    private const val FIELD_SP = 36f // ivi_text_field Medium: Regular 36/44
    private const val FIELD_LINE_SP = 44f
    private const val ITEM_TITLE_SP = 30f // List/item: Bold 30/38
    private const val ITEM_TITLE_LINE_SP = 38f
    private const val ITEM_SUBTITLE_SP = 26f // List/item 부제: Regular 26/34
    private const val ITEM_SUBTITLE_LINE_SP = 34f
    private const val WEIGHT_REGULAR = 400
    private const val WEIGHT_BOLD = 700

    // 버튼 (ivi_basic_button, buttons/basic/{large,medium}/*)
    private class ButtonSize(val heightDp: Int, val radiusDp: Float, val paddingHDp: Int, val paddingVDp: Int, val textSp: Float, val lineSp: Float)

    private val LARGE = ButtonSize(heightDp = 100, radiusDp = 32f, paddingHDp = 40, paddingVDp = 16, textSp = 32f, lineSp = 40f)
    private val MEDIUM = ButtonSize(heightDp = 80, radiusDp = 24f, paddingHDp = 32, paddingVDp = 16, textSp = 30f, lineSp = 38f)
    private const val BUTTON_GAP_DP = 16 // gap_horizontal
    private const val BUTTON_DISABLED_BORDER_DP = 2
    private const val BUTTON_MAX_LINES = 4

    // 입력칸 (fields/text/medium/*)
    private const val FIELD_HEIGHT_DP = 100
    private const val FIELD_RADIUS_DP = 24f
    private const val FIELD_PADDING_H_DP = 32
    private const val FIELD_BORDER_DP = 2 // Enabled 테두리
    private const val FIELD_FOCUSED_BORDER_DP = 3 // fields/text/medium/border
    private const val CURSOR_WIDTH_DP = 3
    private const val CURSOR_HEIGHT_DP = 32

    // 상단바 (page_navigation/*)
    private const val BAR_HEIGHT_DP = 80
    private const val BAR_GAP_DP = 24 // title/depth_gap_horizontal
    private const val APP_ICON_DP = 80
    private const val APP_ICON_RADIUS_DP = 20f
    private const val APP_ICON_GAP_DP = 16 // menu_group 간격
    private const val APP_ICON_BORDER_DP = 1
    private const val BACK_ICON_DP = 56 // title/depth_icon_square
    private const val BACK_PRESSED_RADIUS_DP = 16f

    // 목록 항목 (vehicle_setting_popup_content/List/item)
    private const val ITEM_MIN_HEIGHT_DP = 112
    private const val ITEM_RADIUS_DP = 24f
    private const val ITEM_PADDING_H_DP = 20
    private const val ITEM_PADDING_V_DP = 16
    private const val ITEM_LABEL_PADDING_H_DP = 16
    private const val ITEM_TEXT_GAP_DP = 4

    // 화면 여백 (page_navigation/padding_extralarge) 과 세로 간격
    private const val PADDING_DP = 40
    private const val GAP_DP = 16
    private const val PERCENT_TO_ALPHA = 2.55f

    /** Pleos Color 변수. 주석은 .fig 변수 이름. */
    private class Palette(
        val textPrimary: Int, // text/dark/primary
        val textSecondary: Int, // text/dark/secondary (84%)
        val textTertiary: Int, // text/dark/tertiary (64%)
        val textQuaternary: Int, // text/dark/quaternary (32%)
        val textOnFilled: Int, // text/light/primary
        val basicEnabled: Int, // button/basic/enabled
        val basicPressed: Int, // button/basic/pressed
        val basicDisabled: Int, // button/basic/disabled
        val filledEnabled: Int, // button/filled/enabled
        val filledPressed: Int, // button/filled/pressed
        val filledDisabled: Int, // button/filled/disabled
        val dividerSecondary: Int, // divider/secondary
        val fieldBackground: Int, // field/background
        val fieldFocused: Int, // field/focused
        val itemPressed: Int, // surface/basic
    )

    private fun alpha(rgb: String, percent: Int): Int {
        val color = Color.parseColor(rgb)
        return Color.argb(Math.round(percent * PERCENT_TO_ALPHA), Color.red(color), Color.green(color), Color.blue(color))
    }

    private val LIGHT = Palette(
        textPrimary = Color.parseColor("#131417"),
        textSecondary = alpha("#131417", 84),
        textTertiary = alpha("#131417", 64),
        textQuaternary = alpha("#131417", 32),
        textOnFilled = Color.parseColor("#FFFFFF"),
        basicEnabled = alpha("#131417", 5),
        basicPressed = alpha("#131417", 10),
        basicDisabled = alpha("#FFFFFF", 20),
        filledEnabled = Color.parseColor("#44464E"),
        filledPressed = Color.parseColor("#313236"),
        filledDisabled = alpha("#131417", 5),
        dividerSecondary = alpha("#131417", 10),
        fieldBackground = Color.parseColor("#FFFFFF"),
        fieldFocused = Color.parseColor("#313236"),
        itemPressed = Color.parseColor("#FFFFFF"),
    )

    private val DARK = Palette(
        textPrimary = Color.parseColor("#FFFFFF"),
        textSecondary = alpha("#FFFFFF", 84),
        textTertiary = alpha("#FFFFFF", 64),
        textQuaternary = alpha("#FFFFFF", 32),
        textOnFilled = Color.parseColor("#131417"),
        basicEnabled = alpha("#FFFFFF", 5),
        basicPressed = alpha("#FFFFFF", 10),
        basicDisabled = alpha("#131417", 20),
        filledEnabled = Color.parseColor("#E0E1E6"),
        filledPressed = Color.parseColor("#C7C9CE"),
        filledDisabled = alpha("#FFFFFF", 5),
        dividerSecondary = alpha("#FFFFFF", 10),
        fieldBackground = Color.parseColor("#131417"),
        fieldFocused = Color.parseColor("#EDEEF2"),
        itemPressed = Color.parseColor("#1D1E21"),
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
        val gap = context.dp(BUTTON_GAP_DP)
        children.forEachIndexed { index, child ->
            addView(child, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = gap })
        }
        layoutParams = spaced(context)
    }

    /** 화면 제목 글자. header 로 감싸 상단바에 놓는다. */
    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = TITLE_SP
        lineHeightSp(TITLE_LINE_SP)
        font(WEIGHT_BOLD)
        setTextColor(palette(context).textPrimary)
        maxLines = TITLE_MAX_LINES
    }

    /**
     * 상단바 (Pleos Page Navigation, ivi_title).
     * main 이면 앱 아이콘 + 제목(Main), 아니면 뒤로 화살표 + 제목(Depth).
     * 뒤로는 시스템 뒤로와 같게 onBackPressed 를 부른다 (관리자 첫 등록 화면의 재정의를 따르게).
     */
    fun header(activity: Activity, title: TextView, main: Boolean = false): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.dp(BAR_HEIGHT_DP)
        val colors = palette(activity)
        val lead: View
        val gap: Int
        if (main) {
            lead = appIcon(activity, colors)
            gap = activity.dp(APP_ICON_GAP_DP)
            addView(lead, LinearLayout.LayoutParams(activity.dp(APP_ICON_DP), activity.dp(APP_ICON_DP)))
        } else {
            lead = ImageView(activity).apply {
                setImageResource(R.drawable.ic_pleos_back)
                imageTintList = ColorStateList.valueOf(colors.textPrimary)
                contentDescription = "뒤로"
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(activity, colors.basicPressed, BACK_PRESSED_RADIUS_DP))
                }
                @Suppress("DEPRECATION")
                setOnClickListener { activity.onBackPressed() }
            }
            gap = activity.dp(BAR_GAP_DP)
            addView(lead, LinearLayout.LayoutParams(activity.dp(BACK_ICON_DP), activity.dp(BACK_ICON_DP)))
        }
        addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = gap })
        layoutParams = spaced(activity)
    }

    private fun appIcon(context: Context, colors: Palette) = ImageView(context).apply {
        setImageDrawable(context.applicationInfo.loadIcon(context.packageManager))
        scaleType = ImageView.ScaleType.CENTER_CROP
        val radius = context.dp(APP_ICON_RADIUS_DP)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        clipToOutline = true
        foreground = rounded(context, Color.TRANSPARENT, APP_ICON_RADIUS_DP).apply {
            setStroke(context.dp(APP_ICON_BORDER_DP), colors.dividerSecondary)
        }
    }

    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = BODY_SP
        lineHeightSp(BODY_LINE_SP)
        font(WEIGHT_REGULAR)
        setTextColor(palette(context).textSecondary)
        layoutParams = spaced(context)
    }

    /**
     * 기본 버튼 (ivi_basic_button). basic 이 기본, filled 는 화면의 주 동작 하나에만 쓴다.
     * 화면 버튼은 Large, 목록 항목 뒤에 붙는 버튼은 Medium.
     */
    fun button(context: Context, text: String, filled: Boolean = false, medium: Boolean = false, onClick: () -> Unit): Button =
        Button(context).apply {
            val colors = palette(context)
            val size = if (medium) MEDIUM else LARGE
            this.text = text
            textSize = size.textSp
            lineHeightSp(size.lineSp)
            font(WEIGHT_BOLD)
            isAllCaps = false
            // 기본 버튼 스타일은 한 줄만 보여 줘서 여러 줄 라벨이 잘린다
            isSingleLine = false
            maxLines = BUTTON_MAX_LINES
            minHeight = context.dp(size.heightDp)
            minimumHeight = context.dp(size.heightDp)
            // 기본 버튼 스타일의 최소 너비(약 88dp)를 없애고 .fig 의 패딩만 둔다
            minWidth = 0
            minimumWidth = 0
            gravity = Gravity.CENTER
            setPadding(context.dp(size.paddingHDp), context.dp(size.paddingVDp), context.dp(size.paddingHDp), context.dp(size.paddingVDp))
            // 그림자·눌림 높이 변화를 없앤다 (Pleos 버튼은 평면)
            stateListAnimator = null
            elevation = 0f
            setTextColor(
                ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(colors.textQuaternary, if (filled) colors.textOnFilled else colors.textPrimary),
                ),
            )
            val enabled = if (filled) colors.filledEnabled else colors.basicEnabled
            val pressed = if (filled) colors.filledPressed else colors.basicPressed
            val disabled = if (filled) colors.filledDisabled else colors.basicDisabled
            // 눌림은 물결(ripple) 대신 색만 바꾼다. 주행 중 화면에 움직임을 두지 않는다 (Pleos 주행 중 UI 원칙)
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(-android.R.attr.state_enabled),
                    rounded(context, disabled, size.radiusDp).apply {
                        setStroke(context.dp(BUTTON_DISABLED_BORDER_DP), colors.dividerSecondary)
                    },
                )
                addState(intArrayOf(android.R.attr.state_pressed), rounded(context, pressed, size.radiusDp))
                addState(intArrayOf(), rounded(context, enabled, size.radiusDp))
            }
            layoutParams = spaced(context)
            setOnClickListener { onClick() }
        }

    /**
     * 목록 항목 (List/item). 제목·부제 뒤에 버튼(suffix)을 붙일 수 있다.
     * onClick 이 있으면 항목 전체를 누를 수 있고, 평소엔 바탕이 없다가 누르면 surface/basic 으로 바뀐다.
     */
    fun listItem(context: Context, title: String, subtitle: String?, suffix: View? = null, onClick: (() -> Unit)? = null): LinearLayout =
        LinearLayout(context).apply {
            val colors = palette(context)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(ITEM_MIN_HEIGHT_DP)
            setPadding(context.dp(ITEM_PADDING_H_DP), context.dp(ITEM_PADDING_V_DP), context.dp(ITEM_PADDING_H_DP), context.dp(ITEM_PADDING_V_DP))
            if (onClick != null) {
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(context, colors.itemPressed, ITEM_RADIUS_DP))
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
                setOnClickListener { onClick() }
            }
            val label = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(ITEM_LABEL_PADDING_H_DP), 0, context.dp(ITEM_LABEL_PADDING_H_DP), 0)
                addView(
                    TextView(context).apply {
                        text = title
                        textSize = ITEM_TITLE_SP
                        lineHeightSp(ITEM_TITLE_LINE_SP)
                        font(WEIGHT_BOLD)
                        setTextColor(colors.textPrimary)
                    },
                )
                if (!subtitle.isNullOrEmpty()) {
                    addView(
                        TextView(context).apply {
                            text = subtitle
                            textSize = ITEM_SUBTITLE_SP
                            lineHeightSp(ITEM_SUBTITLE_LINE_SP)
                            font(WEIGHT_REGULAR)
                            setTextColor(colors.textTertiary)
                        },
                        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(ITEM_TEXT_GAP_DP) },
                    )
                }
            }
            addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (suffix != null) addView(suffix, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

    /** 입력칸 (ivi_text_field Medium). */
    fun input(context: Context, hint: String, value: String = "", caps: Boolean = false): EditText = EditText(context).apply {
        val colors = palette(context)
        this.hint = hint
        setText(value)
        textSize = FIELD_SP
        lineHeightSp(FIELD_LINE_SP)
        font(WEIGHT_REGULAR)
        setTextColor(colors.textPrimary)
        setHintTextColor(colors.textTertiary)
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or (if (caps) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0)
        // 가로 화면에선 키보드가 앱 전체를 덮는 전체 화면 입력으로 뜬다. 입력칸이 보이게 끈다
        imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        minHeight = context.dp(FIELD_HEIGHT_DP)
        minimumHeight = context.dp(FIELD_HEIGHT_DP)
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                field(context, colors.fieldBackground, colors.fieldFocused, FIELD_FOCUSED_BORDER_DP),
            )
            addState(intArrayOf(), field(context, colors.fieldBackground, colors.dividerSecondary, FIELD_BORDER_DP))
        }
        // InsetDrawable 은 들인 만큼을 패딩으로 알려 배경을 넣으면 뷰 패딩이 그 값으로 바뀐다. 그래서 배경 뒤에 준다
        setPadding(context.dp(FIELD_PADDING_H_DP), 0, context.dp(FIELD_PADDING_H_DP), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = GradientDrawable().apply {
                setColor(colors.textPrimary)
                setSize(context.dp(CURSOR_WIDTH_DP), context.dp(CURSOR_HEIGHT_DP))
            }
        }
        layoutParams = spaced(context)
    }

    private fun rounded(context: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = context.dp(radiusDp)
    }

    // GradientDrawable 테두리는 선 가운데가 가장자리에 걸려 반이 밖으로 잘린다. .fig 의 안쪽(INSIDE) 테두리처럼 보이게 반만큼 들인다
    private fun field(context: Context, fill: Int, stroke: Int, widthDp: Int) = InsetDrawable(
        rounded(context, fill, FIELD_RADIUS_DP).apply { setStroke(context.dp(widthDp), stroke) },
        context.dp(widthDp) / 2,
    )

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
