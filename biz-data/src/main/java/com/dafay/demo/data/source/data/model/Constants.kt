package com.example.demo.meetsplash.data.model

/**
 * 配置参数
 */
object ConfigC {
    val JAMENDO_CLIENT_ID: String = "4a7a268d"
}

// sp key
object PrefC {
    val COLOR_THEME: String = "color_theme"
    val LANGUAGE: String = "language"
    val DARK_MODE: String = "dark_mode"


    val PHOTO_QUALITY_PREVIEW: String = "photo_quality_preview"
    val PHOTO_QUALITY_DOWNLAOD: String = "photo_quality_downlaod"
    val PHOTO_QUALITY_WALLPAPER: String = "photo_quality_wallpaper"
}

// sp 默认值
object DefC {
    val THEME: String = ""
}

object PAGE {
    val PAGE_SIZE_TEN = 10
    val PAGE_SIZE_TWENTY = 20
    val PAGE_SIZE_THIRTY = 30
}


/**
 * intent extra
 */
object ExtraC {
    const val SETTINGS_INSTANCE_BOUND = "settings_instance_bound"
    const val SETTINGS_COLOR_THEME_SCROLL_X = "settings_color_theme_scroll_x"
    const val SETTINGS_SCROLL_Y = "settings_scroll_y"
}
