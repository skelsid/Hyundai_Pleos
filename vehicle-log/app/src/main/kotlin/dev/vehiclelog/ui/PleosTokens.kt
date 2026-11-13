package dev.vehiclelog.ui

/**
 * Pleos 크기 토큰. Pleos_Connect_design_assets_215.fig 의 크기 변수를 그대로 옮겼다 (손으로 고치지 않는다).
 * 값은 dp (.fig 의 px 는 160dpi 기준). 주석은 .fig 변수 이름.
 * 화면 종류별 값은 form_factor 모드 connect-s / connect-w / connect-waterfall.
 * connect-l 모드는 쓰지 않는다. Pleos 레이아웃 문서: 기존의 Connect-L 은 Connect-S 로 재분류되었다.
 */
internal object PleosTokens {
    // 글자: 텍스트 스타일 (크기/줄높이)
    val TITLE_LARGE_SIZE = Dimen(s = 40, w = 33, wf = 42) // size_sp/level13 — Title/Large/* 크기
    val TITLE_LARGE_LINE = Dimen(s = 52, w = 43, wf = 55) // size_sp/level16 — Title/Large/* 줄높이
    val TITLE_MEDIUM_SIZE = Dimen(s = 36, w = 30, wf = 38) // size_sp/level11 — Title/Medium/* 크기
    val TITLE_MEDIUM_LINE = Dimen(s = 44, w = 37, wf = 47) // size_sp/level14 — Title/Medium/* 줄높이
    val TITLE_SMALL_SIZE = Dimen(s = 32, w = 27, wf = 35) // size_sp/level9 — Title/Small/Strong 크기
    val TITLE_SMALL_LINE = Dimen(s = 40, w = 33, wf = 42) // line_height_sp/level9 — Title/Small/Strong 줄높이
    val BODY_LARGE_SIZE = Dimen(s = 30, w = 25, wf = 34) // size_sp/level8 — Body/Large/* 크기
    val BODY_LARGE_LINE = Dimen(s = 38, w = 32, wf = 41) // line_height_sp/level8 — Body/Large/* 줄높이
    val BODY_SMALL_SIZE = Dimen(s = 26, w = 22, wf = 28) // size_sp/level6 — Body/Small/* 크기
    val BODY_SMALL_LINE = Dimen(s = 34, w = 28, wf = 36) // line_height_sp/level6 — Body/Small/* 줄높이

    // 버튼 (ivi_basic_button)
    val BUTTON_LARGE_HEIGHT = Dimen(s = 100, w = 84, wf = 106) // buttons/basic/large/height
    val BUTTON_LARGE_RADIUS = Dimen(s = 32, w = 27, wf = 35) // buttons/basic/large/radius_top_left
    val BUTTON_LARGE_PADDING_H = Dimen(s = 40, w = 34, wf = 42) // buttons/basic/large/padding_horizontal
    val BUTTON_LARGE_PADDING_V = Dimen(s = 16, w = 14, wf = 17) // buttons/basic/large/padding_vertical
    val BUTTON_LARGE_GAP = Dimen(s = 16, w = 14, wf = 17) // buttons/basic/large/gap_horizontal — 나란한 버튼 사이, 세로로 쌓인 칸 사이
    val BUTTON_LARGE_MIN_WIDTH = Dimen(s = 140, w = 116, wf = 148) // buttons/basic/large/min_width
    val BUTTON_MEDIUM_HEIGHT = Dimen(s = 80, w = 66, wf = 84) // buttons/basic/medium/height
    val BUTTON_MEDIUM_RADIUS = Dimen(s = 24, w = 20, wf = 26) // buttons/basic/medium/radius_top_left_medium
    val BUTTON_MEDIUM_PADDING_H = Dimen(s = 32, w = 27, wf = 35) // buttons/basic/medium/padding_horizontal
    val BUTTON_MEDIUM_PADDING_V = Dimen(s = 16, w = 14, wf = 17) // buttons/basic/medium/padding_vertical
    val BUTTON_MEDIUM_MIN_WIDTH = Dimen(s = 120, w = 100, wf = 128) // buttons/basic/medium/min_width

    // 입력칸 (ivi_text_field Medium)
    val FIELD_HEIGHT = Dimen(s = 100, w = 84, wf = 106) // fields/text/medium/height
    val FIELD_RADIUS = Dimen(s = 24, w = 20, wf = 26) // fields/text/medium/radius_top_left
    val FIELD_PADDING_H = Dimen(s = 32, w = 28, wf = 35) // fields/text/medium/padding_horizontal
    val FIELD_BORDER = Dimen(s = 2, w = 2, wf = 3) // fields/text/small/border — 평소(Enabled) 테두리
    val FIELD_FOCUSED_BORDER = Dimen(s = 3, w = 3, wf = 3) // fields/text/medium/border — 포커스 테두리

    // 상단바 (ivi_title, page_navigation)
    val BAR_HEIGHT = Dimen(s = 80, w = 66, wf = 84) // page_navigation/container_height
    val BAR_PADDING_START = Dimen(s = 40, w = 32, wf = 42) // page_navigation/padding_extralarge — 화면 좌우 여백으로도 쓴다
    val BAR_PADDING_END = Dimen(s = 24, w = 22, wf = 20) // page_navigation/padding_large
    val BAR_APP_ICON = Dimen(s = 80, w = 66, wf = 84) // page_navigation/title/app_icon/container_square
    val BAR_APP_ICON_RADIUS = Dimen(s = 20, w = 17, wf = 22) // page_navigation/title/app_icon/radius_top_left
    val BAR_APP_ICON_GAP = Dimen(s = 16, w = 14, wf = 17) // page_navigation/gap_medium — menu_group: 앱 아이콘과 제목 사이
    val BAR_TITLE_PADDING_H = Dimen(s = 16, w = 14, wf = 17) // page_navigation/padding_medium — Main 제목(primary_button) 좌우 패딩
    val BAR_BACK_ICON = Dimen(s = 56, w = 46, wf = 59) // page_navigation/title/depth_icon_square
    val BAR_BACK_GAP = Dimen(s = 24, w = 20, wf = 26) // page_navigation/title/depth_gap_horizontal
    val LINE = Dimen(s = 1, w = 1, wf = 1) // line/level1 — 앱 아이콘 테두리

    // 목록 항목 (vehicle_setting_popup_content/List/item)
    val ITEM_HEIGHT = Dimen(s = 112, w = 93, wf = 119) // vehicle_settings/list/item/height — 최소 높이
    val ITEM_RADIUS = Dimen(s = 24, w = 20, wf = 26) // vehicle_settings/list/item/container/radius_top_left
    val ITEM_PADDING_H = Dimen(s = 20, w = 17, wf = 22) // vehicle_settings/list/item/container/padding_horizontal
    val ITEM_PADDING_V = Dimen(s = 16, w = 13, wf = 17) // vehicle_settings/list/item/container/padding_vertical
    val ITEM_LABEL_PADDING_H = Dimen(s = 16, w = 12, wf = 12) // popup/system/padding_small — Label 프레임 좌우
    val ITEM_TEXT_GAP = Dimen(s = 4, w = 3, wf = 4) // popup/system/gap_extrasmall — 제목과 부제 사이

    // 시스템 팝업 (ivi_system_popup Medium)
    val POPUP_WIDTH = Dimen(s = 808, w = 720, wf = 860) // popup/system/medium_width
    val POPUP_RADIUS = Dimen(s = 48, w = 40, wf = 51) // popup/system/radius_top_left
    val POPUP_BORDER = Dimen(s = 2, w = 2, wf = 3) // popup/system/border
    val POPUP_PADDING = Dimen(s = 48, w = 40, wf = 40) // popup/system/padding_large
    val POPUP_SECTION_GAP = Dimen(s = 40, w = 33, wf = 42) // popup/system/gap_extralarge — 머리와 버튼 사이
    val POPUP_HEADER_PADDING_BOTTOM = Dimen(s = 16, w = 12, wf = 12) // popup/system/padding_small
    val POPUP_GAP = Dimen(s = 24, w = 24, wf = 24) // popup/system/gap_large — 제목과 설명 사이, 버튼과 버튼 사이
}
