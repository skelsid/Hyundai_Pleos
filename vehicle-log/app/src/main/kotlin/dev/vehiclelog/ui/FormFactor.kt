package dev.vehiclelog.ui

/**
 * Pleos Connect 디스플레이 종류 (Pleos 레이아웃 문서·디스플레이 가이드).
 * - S: Standard 16:9. 17인치 2560x1440, 14.6인치 2048x1152, 12.9인치 1920x1080 (기존 Connect-L)
 * - W: Wide 24.6인치. Standard 3840x850, Extended 3840x1440
 * - WF: Waterfall 18.5인치 3072x816
 */
internal enum class FormFactor {
    S,
    W,
    WF,
    ;

    companion object {
        private const val WIDE_LONG_SIDE_PX = 3840
        private const val WATERFALL_LONG_SIDE_PX = 3072

        /** 디스플레이 물리 해상도(px)로 고른다. 가로/세로 어느 방향이든 긴 변으로 본다. 모르는 해상도는 OS 기본인 S. */
        fun of(widthPx: Int, heightPx: Int): FormFactor = when (maxOf(widthPx, heightPx)) {
            WIDE_LONG_SIDE_PX -> W
            WATERFALL_LONG_SIDE_PX -> WF
            else -> S
        }
    }
}

/** 화면 종류마다 다른 크기 값(dp). */
internal class Dimen(private val s: Int, private val w: Int, private val wf: Int) {
    fun of(formFactor: FormFactor): Int = when (formFactor) {
        FormFactor.S -> s
        FormFactor.W -> w
        FormFactor.WF -> wf
    }
}
