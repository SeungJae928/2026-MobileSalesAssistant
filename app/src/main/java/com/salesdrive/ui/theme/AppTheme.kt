package com.salesdrive.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) {
    LIGHT("라이트"),
    DARK("다크"),
    ;

    companion object {
        /** 저장값이 없거나 읽을 수 없으면 라이트 */
        fun parse(saved: String?): ThemeMode = entries.firstOrNull { it.name == saved } ?: LIGHT
    }
}

/** 고른 테마. 차량 프로필마다 따로 저장된다 */
@Singleton
class ThemeStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(ThemeMode.parse(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        _mode.value = mode
    }

    private companion object {
        const val KEY_MODE = "mode"
    }
}

/*
 * 화이트·그레이·블랙만 쓴다. 빨강은 오류 표시에만 남긴다.
 * 역할: surface 본문 / surfaceContainer 사이드바 / surfaceContainerHighest 카드 / surfaceVariant 달력 칸
 * 달력 표시: primaryContainer 일정 / tertiaryContainer 후속 조치 / secondaryContainer 고른 날짜
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF18181B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFFD4D4D8),
    secondary = Color(0xFF52525B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4E7),
    onSecondaryContainer = Color(0xFF18181B),
    tertiary = Color(0xFF71717A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA1A1AA),
    onTertiaryContainer = Color(0xFF09090B),
    background = Color(0xFFF4F4F5),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFF4F4F5),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF52525B),
    surfaceTint = Color(0xFF18181B),
    inverseSurface = Color(0xFF27272A),
    inverseOnSurface = Color(0xFFF4F4F5),
    outline = Color(0xFFA1A1AA),
    outlineVariant = Color(0xFFD4D4D8),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD4D4D8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F4F5),
    surfaceContainer = Color(0xFFE4E4E7),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF18181B),
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color(0xFF18181B),
    inversePrimary = Color(0xFF3F3F46),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF3F3F46),
    onSecondaryContainer = Color(0xFFF4F4F5),
    tertiary = Color(0xFF71717A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF71717A),
    onTertiaryContainer = Color.White,
    background = Color(0xFF131316),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF131316),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1F1F23),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceTint = Color(0xFFF4F4F5),
    inverseSurface = Color(0xFFE4E4E7),
    inverseOnSurface = Color(0xFF18181B),
    outline = Color(0xFF71717A),
    outlineVariant = Color(0xFF3F3F46),
    surfaceBright = Color(0xFF3F3F46),
    surfaceDim = Color(0xFF09090B),
    surfaceContainerLowest = Color(0xFF09090B),
    surfaceContainerLow = Color(0xFF131316),
    surfaceContainer = Color(0xFF09090B),
    surfaceContainerHigh = Color(0xFF27272A),
    surfaceContainerHighest = Color(0xFF1F1F23),
)

@Composable
fun SalesDriveTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (mode == ThemeMode.DARK) DarkColors else LightColors, content = content)
}
