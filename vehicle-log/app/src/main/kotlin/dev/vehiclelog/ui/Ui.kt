package dev.vehiclelog.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.vehiclelog.R
import dev.vehiclelog.ui.PleosTokens as T
import kotlin.math.roundToInt

/**
 * 차량 화면용 뷰. 레이아웃 XML 없이 코드로 만든다.
 *
 * 모양은 Pleos 공식 디자인 자산(Pleos_Connect_design_assets_215.fig)의 컴포넌트를 옮겼다.
 * - 크기: PleosTokens (화면 종류 S/W/WF 별 .fig 크기 변수). 화면 종류는 디스플레이 해상도로 고른다 (FormFactor).
 * - 색: .fig Color 변수의 Light/Dark 값 (아래 Palette). 낮/밤은 시스템 설정(uiMode)을 따른다.
 * - 컴포넌트: ivi_title(상단바), ivi_basic_button(Large/Medium), ivi_toggle_button(Large), ivi_text_field(Medium), List/item,
 *   ivi_system_popup(Medium).
 * 화면 바탕(background/primary)은 res/values(-night)/colors.xml, 글꼴 Asta Sans 는 res/font/asta_sans.xml.
 */
internal object Ui {
    private const val WEIGHT_REGULAR = 400
    private const val WEIGHT_BOLD = 700
    private const val WEIGHT_EXTRA_BOLD = 800
    private const val TITLE_MAX_LINES = 2
    private const val BUTTON_MAX_LINES = 4
    // .fig 의 커서(Focused 상태 'cursor' 3x32). 변수에 묶여 있지 않은 고정 값
    private const val CURSOR_WIDTH_DP = 3
    private const val CURSOR_HEIGHT_DP = 32
    private const val PERCENT_TO_ALPHA = 2.55f
    // .fig ic_app 의 그림자 두 겹(DROP_SHADOW). 변수에 묶여 있지 않은 고정 값
    private const val ICON_SHADOW_FAR_Y_DP = 8
    private const val ICON_SHADOW_FAR_BLUR_DP = 32
    private const val ICON_SHADOW_NEAR_Y_DP = 2
    private const val ICON_SHADOW_NEAR_BLUR_DP = 2
    // 그림자 Level 2 색 (#000000 16% / 8%). 앱 아이콘·토글 켜짐 공통, .fig 에서 변수 없이 고정
    private const val SHADOW_FAR_ALPHA_PERCENT = 16
    private const val SHADOW_NEAR_ALPHA_PERCENT = 8
    // .fig 버튼 Disabled 테두리 두께. 변수 없이 고정
    private const val DISABLED_BORDER_DP = 2

    /** .fig 텍스트 스타일: 크기·줄높이 변수와 굵기. */
    private class TextStyle(val size: Dimen, val line: Dimen, val weight: Int)

    private val TITLE_LARGE_STRONG = TextStyle(T.TITLE_LARGE_SIZE, T.TITLE_LARGE_LINE, WEIGHT_BOLD) // 상단바 제목
    private val TITLE_LARGE_EXTRA_STRONG = TextStyle(T.TITLE_LARGE_SIZE, T.TITLE_LARGE_LINE, WEIGHT_EXTRA_BOLD) // 팝업 제목
    private val TITLE_MEDIUM_NORMAL = TextStyle(T.TITLE_MEDIUM_SIZE, T.TITLE_MEDIUM_LINE, WEIGHT_REGULAR) // 입력칸
    private val TITLE_SMALL_STRONG = TextStyle(T.TITLE_SMALL_SIZE, T.TITLE_SMALL_LINE, WEIGHT_BOLD) // Large 버튼
    private val BODY_LARGE_STRONG = TextStyle(T.BODY_LARGE_SIZE, T.BODY_LARGE_LINE, WEIGHT_BOLD) // Medium 버튼, 목록 제목
    private val BODY_LARGE_NORMAL = TextStyle(T.BODY_LARGE_SIZE, T.BODY_LARGE_LINE, WEIGHT_REGULAR) // 팝업 설명
    private val BODY_SMALL_NORMAL = TextStyle(T.BODY_SMALL_SIZE, T.BODY_SMALL_LINE, WEIGHT_REGULAR) // 본문·도움말·목록 부제

    /** ivi_basic_button 크기 (Size=Large / Medium). */
    private class ButtonSize(
        val height: Dimen,
        val radius: Dimen,
        val paddingH: Dimen,
        val paddingV: Dimen,
        val minWidth: Dimen,
        val text: TextStyle,
    )

    private val LARGE = ButtonSize(
        T.BUTTON_LARGE_HEIGHT, T.BUTTON_LARGE_RADIUS, T.BUTTON_LARGE_PADDING_H, T.BUTTON_LARGE_PADDING_V, T.BUTTON_LARGE_MIN_WIDTH,
        TITLE_SMALL_STRONG,
    )
    private val MEDIUM = ButtonSize(
        T.BUTTON_MEDIUM_HEIGHT, T.BUTTON_MEDIUM_RADIUS, T.BUTTON_MEDIUM_PADDING_H, T.BUTTON_MEDIUM_PADDING_V, T.BUTTON_MEDIUM_MIN_WIDTH,
        BODY_LARGE_STRONG,
    )
    private val TOGGLE = ButtonSize(
        T.TOGGLE_LARGE_HEIGHT, T.TOGGLE_LARGE_RADIUS, T.TOGGLE_LARGE_PADDING_H, T.TOGGLE_LARGE_PADDING_V, T.TOGGLE_LARGE_MIN_WIDTH,
        TITLE_SMALL_STRONG,
    )

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
        val dividerPrimary: Int, // divider/primary
        val dividerSecondary: Int, // divider/secondary
        val fieldBackground: Int, // field/background
        val fieldFocused: Int, // field/focused
        val itemPressed: Int, // surface/basic
        val popupBackground: Int, // background/popup
        val switchEnabled: Int, // button/switch/enabled (토글 켜짐)
        val switchPressed: Int, // button/switch/pressed
        val switchDisabled: Int, // button/switch/disabled
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
        dividerPrimary = alpha("#131417", 5),
        dividerSecondary = alpha("#131417", 10),
        fieldBackground = Color.parseColor("#FFFFFF"),
        fieldFocused = Color.parseColor("#313236"),
        itemPressed = Color.parseColor("#FFFFFF"),
        popupBackground = Color.parseColor("#FFFFFF"),
        switchEnabled = Color.parseColor("#FFFFFF"),
        switchPressed = alpha("#FFFFFF", 50),
        switchDisabled = alpha("#FFFFFF", 30),
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
        dividerPrimary = alpha("#FFFFFF", 5),
        dividerSecondary = alpha("#FFFFFF", 10),
        fieldBackground = Color.parseColor("#131417"),
        fieldFocused = Color.parseColor("#EDEEF2"),
        itemPressed = Color.parseColor("#1D1E21"),
        popupBackground = Color.parseColor("#313236"),
        switchEnabled = alpha("#FFFFFF", 20),
        switchPressed = alpha("#FFFFFF", 10),
        switchDisabled = alpha("#131417", 30),
    )

    private fun palette(context: Context): Palette {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (night == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
    }

    // 디스플레이는 실행 중에 바뀌지 않으므로 한 번만 읽는다 (UI 스레드에서만 쓴다)
    private var formFactor: FormFactor? = null

    private fun Context.formFactor(): FormFactor = formFactor ?: run {
        val display = getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display?.mode
        val detected = if (mode == null) FormFactor.S else FormFactor.of(mode.physicalWidth, mode.physicalHeight)
        formFactor = detected
        detected
    }

    /** 화면 종류에 맞는 토큰 값을 px 로. */
    private fun Context.px(dimen: Dimen): Int = (dimen.of(formFactor()) * resources.displayMetrics.density).roundToInt()

    private fun Context.px(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    fun screen(context: Context, content: LinearLayout): ScrollView =
        ScrollView(context).apply {
            setBackgroundColor(context.getColor(R.color.background))
            addView(content, MATCH_PARENT, WRAP_CONTENT)
        }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = context.px(T.BAR_PADDING_START)
        setPadding(padding, padding, padding, padding)
        // 앱 아이콘 그림자가 아이콘 밖(위쪽 여백 포함)까지 그려지게 자르지 않는다
        clipChildren = false
        clipToPadding = false
    }

    /** 자식들을 같은 너비로 나란히 놓는다. 양끝은 위아래 전체 너비 버튼과 맞춘다. */
    fun row(context: Context, vararg children: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val gap = context.px(T.BUTTON_LARGE_GAP)
        children.forEachIndexed { index, child ->
            addView(child, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = gap })
        }
        layoutParams = spaced(context)
    }

    /** 화면 제목 글자 (Title/Large/Strong). header 로 감싸 상단바에 놓는다. */
    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        style(TITLE_LARGE_STRONG)
        setTextColor(palette(context).textPrimary)
        maxLines = TITLE_MAX_LINES
    }

    /**
     * 상단바 (ivi_title). main 이면 앱 아이콘 + 제목(Type=Main), 아니면 뒤로 화살표 + 제목(Type=Depth).
     * 뒤로는 시스템 뒤로와 같게 onBackPressed 를 부른다 (관리자 첫 등록 화면의 재정의를 따르게).
     * 화면 좌우 여백(column)이 이미 padding_extralarge 라 상단바 자체 좌우 패딩은 두지 않는다.
     */
    fun header(activity: Activity, title: TextView, main: Boolean = false): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = activity.px(T.BAR_HEIGHT)
        val colors = palette(activity)
        val gap: Int
        if (main) {
            val icon = activity.px(T.BAR_APP_ICON)
            // 아이콘은 둥근 모서리로 잘리므로 그림자는 감싸는 틀의 배경으로 그린다
            val frame = FrameLayout(activity).apply {
                background = appIconShadow(activity)
                addView(appIcon(activity, colors), FrameLayout.LayoutParams(icon, icon))
            }
            addView(frame, LinearLayout.LayoutParams(icon, icon))
            clipChildren = false
            // 제목은 primary_button 안에 있어 그 왼쪽 패딩만큼 더 띄운다
            gap = activity.px(T.BAR_APP_ICON_GAP) + activity.px(T.BAR_TITLE_PADDING_H)
        } else {
            val back = ImageView(activity).apply {
                setImageResource(R.drawable.ic_pleos_back)
                imageTintList = ColorStateList.valueOf(colors.textPrimary)
                contentDescription = "뒤로"
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(activity, colors.basicPressed, activity.px(T.BUTTON_MEDIUM_RADIUS)))
                }
                @Suppress("DEPRECATION")
                setOnClickListener { activity.onBackPressed() }
            }
            val icon = activity.px(T.BAR_BACK_ICON)
            addView(back, LinearLayout.LayoutParams(icon, icon))
            gap = activity.px(T.BAR_BACK_GAP)
        }
        addView(title, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = gap })
        layoutParams = spaced(activity)
    }

    private fun appIconShadow(context: Context) = ShadowDrawable(
        context.px(T.BAR_APP_ICON_RADIUS).toFloat(),
        listOf(
            ShadowDrawable.Shadow(
                context.px(ICON_SHADOW_FAR_Y_DP).toFloat(),
                context.px(ICON_SHADOW_FAR_BLUR_DP).toFloat(),
                alpha("#000000", SHADOW_FAR_ALPHA_PERCENT),
            ),
            ShadowDrawable.Shadow(
                context.px(ICON_SHADOW_NEAR_Y_DP).toFloat(),
                context.px(ICON_SHADOW_NEAR_BLUR_DP).toFloat(),
                alpha("#000000", SHADOW_NEAR_ALPHA_PERCENT),
            ),
        ),
    )

    private fun appIcon(context: Context, colors: Palette) = ImageView(context).apply {
        setImageDrawable(context.applicationInfo.loadIcon(context.packageManager))
        scaleType = ImageView.ScaleType.CENTER_CROP
        val radius = context.px(T.BAR_APP_ICON_RADIUS).toFloat()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
        clipToOutline = true
        foreground = rounded(context, Color.TRANSPARENT, radius.roundToInt()).apply {
            setStroke(context.px(T.LINE), colors.dividerSecondary)
        }
    }

    /** 본문 (Body/Small/Normal, text/dark/secondary). .fig 에 Body/Medium/Normal 스타일은 없다. */
    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        style(BODY_SMALL_NORMAL)
        setTextColor(palette(context).textSecondary)
        layoutParams = spaced(context)
    }

    /**
     * 기본 버튼 (ivi_basic_button). basic 이 기본, filled 는 화면의 주 동작 하나에만 쓴다.
     * 화면 버튼은 Large, 목록 항목 뒤에 붙는 버튼은 Medium.
     */
    fun button(context: Context, text: String, filled: Boolean = false, medium: Boolean = false, onClick: () -> Unit): Button {
        val colors = palette(context)
        val size = if (medium) MEDIUM else LARGE
        val radius = context.px(size.radius)
        val enabled = if (filled) colors.filledEnabled else colors.basicEnabled
        val pressed = if (filled) colors.filledPressed else colors.basicPressed
        val disabled = if (filled) colors.filledDisabled else colors.basicDisabled
        val textColors = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(colors.textQuaternary, if (filled) colors.textOnFilled else colors.textPrimary),
        )
        // 눌림은 물결(ripple) 대신 색만 바꾼다. 주행 중 화면에 움직임을 두지 않는다 (Pleos 주행 중 UI 원칙)
        val background = StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                // Disabled: 바탕 + divider/secondary 안쪽 테두리 (.fig 에서 테두리 두께는 2 고정)
                insideStroke(context, disabled, colors.dividerSecondary, context.px(DISABLED_BORDER_DP), radius),
            )
            addState(intArrayOf(android.R.attr.state_pressed), rounded(context, pressed, radius))
            addState(intArrayOf(), rounded(context, enabled, radius))
        }
        return pleosButton(context, text, size, textColors, background, onClick)
    }

    /**
     * 토글 버튼 (ivi_toggle_button Size=Large). 여러 선택지 중 고른 것을 켜짐으로 보여 준다. 켜짐은 View.isSelected 로 준다.
     * 꺼짐: button/basic 바탕 + text/dark/secondary. 켜짐: button/switch 바탕 + 그림자 Level 2 + text/dark/primary.
     */
    fun toggle(context: Context, text: String, onClick: () -> Unit): Button {
        val colors = palette(context)
        val radius = context.px(TOGGLE.radius)
        val border = context.px(DISABLED_BORDER_DP)
        val enabled = android.R.attr.state_enabled
        val selected = android.R.attr.state_selected
        val pressed = android.R.attr.state_pressed
        val textColors = ColorStateList(
            arrayOf(intArrayOf(-enabled), intArrayOf(selected), intArrayOf()),
            intArrayOf(colors.textQuaternary, colors.textPrimary, colors.textSecondary),
        )
        val background = StateListDrawable().apply {
            addState(intArrayOf(-enabled, selected), insideStroke(context, colors.switchDisabled, colors.dividerSecondary, border, radius))
            addState(intArrayOf(-enabled), insideStroke(context, colors.basicDisabled, colors.dividerSecondary, border, radius))
            addState(intArrayOf(selected, pressed), raised(context, colors.switchPressed, radius))
            addState(intArrayOf(selected), raised(context, colors.switchEnabled, radius))
            addState(intArrayOf(pressed), rounded(context, colors.basicPressed, radius))
            addState(intArrayOf(), rounded(context, colors.basicEnabled, radius))
        }
        return pleosButton(context, text, TOGGLE, textColors, background, onClick)
    }

    /** 바탕 + 그림자 Level 2 (토글 켜짐). 그림자는 경계 밖으로 그려지므로 부모가 자식을 자르지 않아야 보인다 (column). */
    private fun raised(context: Context, fill: Int, radiusPx: Int) = LayerDrawable(
        arrayOf(
            ShadowDrawable(
                radiusPx.toFloat(),
                listOf(
                    ShadowDrawable.Shadow(
                        context.px(T.SHADOW_L2_FAR_Y).toFloat(),
                        context.px(T.SHADOW_L2_FAR_BLUR).toFloat(),
                        alpha("#000000", SHADOW_FAR_ALPHA_PERCENT),
                    ),
                    ShadowDrawable.Shadow(
                        context.px(T.SHADOW_L2_NEAR_Y).toFloat(),
                        context.px(T.SHADOW_L2_NEAR_BLUR).toFloat(),
                        alpha("#000000", SHADOW_NEAR_ALPHA_PERCENT),
                    ),
                ),
            ),
            rounded(context, fill, radiusPx),
        ),
    )

    /** 기본·토글 버튼 공통: 크기·글자색·바탕을 받아 Button 을 만든다. */
    private fun pleosButton(
        context: Context,
        text: String,
        size: ButtonSize,
        textColors: ColorStateList,
        backgroundDrawable: StateListDrawable,
        onClick: () -> Unit,
    ): Button = Button(context).apply {
        this.text = text
        style(size.text)
        isAllCaps = false
        // 기본 버튼 스타일은 한 줄만 보여 줘서 여러 줄 라벨이 잘린다
        isSingleLine = false
        maxLines = BUTTON_MAX_LINES
        minHeight = context.px(size.height)
        minimumHeight = context.px(size.height)
        minWidth = context.px(size.minWidth)
        minimumWidth = context.px(size.minWidth)
        gravity = Gravity.CENTER
        // 그림자·눌림 높이 변화를 없앤다 (Pleos 버튼은 평면). 토글 켜짐 그림자는 바탕 drawable 로 그린다
        stateListAnimator = null
        elevation = 0f
        setTextColor(textColors)
        background = backgroundDrawable
        // 배경 drawable 이 뷰 패딩을 바꿀 수 있어 배경 뒤에 준다
        val padH = context.px(size.paddingH)
        val padV = context.px(size.paddingV)
        setPadding(padH, padV, padH, padV)
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
            minimumHeight = context.px(T.ITEM_HEIGHT)
            val padH = context.px(T.ITEM_PADDING_H)
            val padV = context.px(T.ITEM_PADDING_V)
            setPadding(padH, padV, padH, padV)
            if (onClick != null) {
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(context, colors.itemPressed, context.px(T.ITEM_RADIUS)))
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
                setOnClickListener { onClick() }
            }
            val label = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val labelPad = context.px(T.ITEM_LABEL_PADDING_H)
                setPadding(labelPad, 0, labelPad, 0)
                addView(
                    TextView(context).apply {
                        text = title
                        style(BODY_LARGE_STRONG)
                        setTextColor(colors.textPrimary)
                    },
                )
                if (!subtitle.isNullOrEmpty()) {
                    addView(
                        TextView(context).apply {
                            text = subtitle
                            style(BODY_SMALL_NORMAL)
                            setTextColor(colors.textTertiary)
                        },
                        LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.px(T.ITEM_TEXT_GAP) },
                    )
                }
            }
            addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (suffix != null) addView(suffix, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

    /** 입력칸 (ivi_text_field Medium). password 면 입력 글자를 가린다. */
    fun input(context: Context, hint: String, value: String = "", caps: Boolean = false, password: Boolean = false): EditText =
        EditText(context).apply {
            val colors = palette(context)
            this.hint = hint
            setText(value)
            style(TITLE_MEDIUM_NORMAL)
            setTextColor(colors.textPrimary)
            setHintTextColor(colors.textTertiary)
            isSingleLine = true
            val font = typeface
            inputType = InputType.TYPE_CLASS_TEXT or
                (if (caps) InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS else 0) or
                (if (password) InputType.TYPE_TEXT_VARIATION_PASSWORD else 0)
            // 비밀번호 칸은 inputType 이 글꼴을 고정폭으로 바꾼다. Asta Sans 로 되돌린다
            typeface = font
            // 가로 화면에선 키보드가 앱 전체를 덮는 전체 화면 입력으로 뜬다. 입력칸이 보이게 끈다
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            minHeight = context.px(T.FIELD_HEIGHT)
            minimumHeight = context.px(T.FIELD_HEIGHT)
            val radius = context.px(T.FIELD_RADIUS)
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_focused),
                    insideStroke(context, colors.fieldBackground, colors.fieldFocused, context.px(T.FIELD_FOCUSED_BORDER), radius),
                )
                addState(intArrayOf(), insideStroke(context, colors.fieldBackground, colors.dividerSecondary, context.px(T.FIELD_BORDER), radius))
            }
            // 배경 drawable 이 뷰 패딩을 바꿀 수 있어 배경 뒤에 준다
            val padH = context.px(T.FIELD_PADDING_H)
            setPadding(padH, 0, padH, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = GradientDrawable().apply {
                    setColor(colors.textPrimary)
                    setSize(context.px(CURSOR_WIDTH_DP), context.px(CURSOR_HEIGHT_DP))
                }
            }
            layoutParams = spaced(context)
        }

    /**
     * 시스템 팝업 (ivi_system_popup Size=Medium). 제목·설명은 가운데, 버튼은 가로 두 개(왼쪽 filled = 주 동작).
     * 플랫폼 AlertDialog 는 밤에도 밝게 그려져 앱 디자인과 달라서 직접 그린다.
     * 뒤 화면을 어둡게 하는 값은 .fig 에 정의가 없어 플랫폼 대화상자 기본을 따른다.
     */
    fun popup(
        activity: Activity,
        title: String,
        message: String,
        confirm: String,
        cancel: String? = null,
        onConfirm: () -> Unit = {},
    ): Dialog {
        val colors = palette(activity)
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = insideStroke(activity, colors.popupBackground, colors.dividerPrimary, activity.px(T.POPUP_BORDER), activity.px(T.POPUP_RADIUS))
            val pad = activity.px(T.POPUP_PADDING)
            setPadding(pad, pad, pad, pad)
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, activity.px(T.POPUP_HEADER_PADDING_BOTTOM))
                addView(
                    TextView(activity).apply {
                        text = title
                        style(TITLE_LARGE_EXTRA_STRONG)
                        setTextColor(colors.textPrimary)
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
                )
                addView(
                    TextView(activity).apply {
                        text = message
                        style(BODY_LARGE_NORMAL)
                        setTextColor(colors.textSecondary)
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = activity.px(T.POPUP_GAP) },
                )
            }
            addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            val buttons = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                val confirmButton = button(activity, confirm, filled = true) {
                    dialog.dismiss()
                    onConfirm()
                }
                addView(confirmButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                if (cancel != null) {
                    val cancelButton = button(activity, cancel) { dialog.dismiss() }
                    addView(cancelButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = activity.px(T.POPUP_GAP) })
                }
            }
            addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = activity.px(T.POPUP_SECTION_GAP) })
        }
        dialog.setContentView(content)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(activity.px(T.POPUP_WIDTH), WRAP_CONTENT)
        }
        return dialog
    }

    private fun rounded(context: Context, color: Int, radiusPx: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
    }

    /**
     * .fig 의 안쪽(INSIDE) 테두리: 바탕은 경계 끝까지 채우고 그 위에 선을 경계 안쪽으로 겹친다.
     * GradientDrawable 하나에 선을 주면 선 두께 반만큼 바탕을 들여 그려서, 반투명 선 아래 바깥 1px 에 바탕이 없다.
     * 그래서 바탕과 선을 두 층으로 나눈다. 선 층은 반 두께만큼 들인 사각형에 그려지므로 반지름도 그만큼 줄여 바깥 곡선을 맞춘다.
     */
    private fun insideStroke(context: Context, fill: Int, stroke: Int, widthPx: Int, radiusPx: Int) = LayerDrawable(
        arrayOf(
            rounded(context, fill, radiusPx),
            rounded(context, Color.TRANSPARENT, (radiusPx - widthPx / 2).coerceAtLeast(0)).apply { setStroke(widthPx, stroke) },
        ),
    )

    /**
     * .fig 텍스트 스타일을 입힌다. Figma 는 글줄 하나를 줄높이 상자로 잡고 글자를 가운데 두지만,
     * 안드로이드는 마지막 줄에 줄높이 여분을 주지 않아 한 줄 글자가 줄높이보다 낮아진다.
     * 글꼴 여백을 끄고 남는 만큼을 위아래로 나눠 패딩으로 줘서 n 줄 = n x 줄높이 가 되게 한다.
     * 버튼·입력칸은 뒤에서 자기 패딩을 다시 주고 높이가 고정이라 영향 없다.
     */
    private fun TextView.style(textStyle: TextStyle) {
        val formFactor = context.formFactor()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textStyle.size.of(formFactor).toFloat())
        // res/font/asta_sans.xml 에 묶어 둔 굵기(400·600·700·800) 중 가장 가까운 것을 고른다
        typeface = Typeface.create(resources.getFont(R.font.asta_sans), textStyle.weight, false)
        includeFontPadding = false
        val lineHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textStyle.line.of(formFactor).toFloat(), resources.displayMetrics)
            .roundToInt()
        lineHeight = lineHeightPx
        val metrics = paint.fontMetricsInt
        val leading = (lineHeightPx - (metrics.descent - metrics.ascent)).coerceAtLeast(0)
        setPadding(paddingLeft, leading / 2, paddingRight, leading - leading / 2)
    }

    private fun spaced(context: Context) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        val gap = context.px(T.BUTTON_LARGE_GAP)
        setMargins(0, gap / 2, 0, gap / 2)
    }
}
