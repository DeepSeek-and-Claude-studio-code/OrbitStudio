package dev.pocketstudio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pocketstudio.core.Tok

/**
 * Оформление: «синька» — тёмная тема цвета чертёжной кальки (берлинская лазурь) с янтарным акцентом,
 * светлая — холодная чертёжная бумага с кобальтовым акцентом. Углы почти прямые, как у листа чертежа.
 */
class EditorColors(
    val bg: Color,
    val gutterBg: Color,
    val gutterText: Color,
    val text: Color,
    val caret: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val annotation: Color,
    val tag: Color,
    val attribute: Color,
) {
    fun forToken(t: Tok): Color = when (t) {
        Tok.KEYWORD -> keyword
        Tok.STRING -> string
        Tok.COMMENT -> comment
        Tok.NUMBER -> number
        Tok.ANNOTATION -> annotation
        Tok.TAG -> tag
        Tok.ATTRIBUTE -> attribute
    }
}

val DarkEditor = EditorColors(
    bg = Color(0xFF0A1C2E), gutterBg = Color(0xFF0D2236), gutterText = Color(0xFF4F7391),
    text = Color(0xFFDCEBF7), caret = Color(0xFFFFB020),
    keyword = Color(0xFFFF9F43), string = Color(0xFFA5D6A7), comment = Color(0xFF6F8CA6),
    number = Color(0xFF7FD3F5), annotation = Color(0xFFCE93D8), tag = Color(0xFFFF9F43), attribute = Color(0xFF7FD3F5),
)

val LightEditor = EditorColors(
    bg = Color(0xFFFFFFFF), gutterBg = Color(0xFFEAF1F7), gutterText = Color(0xFF8AA1B5),
    text = Color(0xFF0E2438), caret = Color(0xFF1F5FBF),
    keyword = Color(0xFFB45309), string = Color(0xFF2E7D32), comment = Color(0xFF6B8296),
    number = Color(0xFF0369A1), annotation = Color(0xFF7B1FA2), tag = Color(0xFF1F5FBF), attribute = Color(0xFF0C7A9E),
)

val LocalEditorColors = staticCompositionLocalOf { DarkEditor }

private val BlueprintDark = darkColorScheme(
    primary = Color(0xFFFFB020), onPrimary = Color(0xFF2A1B00),
    primaryContainer = Color(0xFF4A3406), onPrimaryContainer = Color(0xFFFFE2A8),
    secondary = Color(0xFF6FC3E8), onSecondary = Color(0xFF00212E),
    secondaryContainer = Color(0xFF123B57), onSecondaryContainer = Color(0xFFCDEBFA),
    tertiary = Color(0xFFA5D6A7), onTertiary = Color(0xFF0B2A0D),
    background = Color(0xFF0C2237), onBackground = Color(0xFFDCEBF7),
    surface = Color(0xFF0C2237), onSurface = Color(0xFFDCEBF7),
    surfaceVariant = Color(0xFF17395A), onSurfaceVariant = Color(0xFF9DB9D0),
    outline = Color(0xFF3A6A94), outlineVariant = Color(0xFF244A6E),
    error = Color(0xFFFF8A80), onError = Color(0xFF3B0A06),
    errorContainer = Color(0xFF5A1A14), onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF081A2B), surfaceContainerLow = Color(0xFF0F2A43),
    surfaceContainer = Color(0xFF12304C), surfaceContainerHigh = Color(0xFF173858),
    surfaceContainerHighest = Color(0xFF1D4163),
)

private val DraftingPaper = lightColorScheme(
    primary = Color(0xFF1F5FBF), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FA), onPrimaryContainer = Color(0xFF0A2A5C),
    secondary = Color(0xFF0C7A9E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2EEF7), onSecondaryContainer = Color(0xFF053746),
    tertiary = Color(0xFF2E7D32), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F6FA), onBackground = Color(0xFF0E2438),
    surface = Color(0xFFF2F6FA), onSurface = Color(0xFF0E2438),
    surfaceVariant = Color(0xFFE3ECF4), onSurfaceVariant = Color(0xFF4A647A),
    outline = Color(0xFF8FA8BD), outlineVariant = Color(0xFFC5D5E2),
    error = Color(0xFFC62828), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7FAFC),
    surfaceContainer = Color(0xFFEAF1F7), surfaceContainerHigh = Color(0xFFE3ECF4),
    surfaceContainerHighest = Color(0xFFDCE7F0),
)

private val SheetShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalEditorColors provides (if (dark) DarkEditor else LightEditor)) {
        MaterialTheme(
            colorScheme = if (dark) BlueprintDark else DraftingPaper,
            shapes = SheetShapes,
            content = content,
        )
    }
}
