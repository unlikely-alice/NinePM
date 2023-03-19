package unlikely.alice.ninepm.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import unlikely.alice.ninepm.R

val VazirFontFamily = FontFamily(
    Font(R.font.vazirmatn_ui_thin, FontWeight.Thin),
    Font(R.font.vazirmatn_ui_extra_light, FontWeight.ExtraLight),
    Font(R.font.vazirmatn_ui_light, FontWeight.Light),
    Font(R.font.vazirmatn_ui_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_ui_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_ui_semi_bold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_ui_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_ui_extra_bold, FontWeight.ExtraBold),
    Font(R.font.vazirmatn_ui_black, FontWeight.Black),
)

val HandwrittenFontFamily = FontFamily(
    Font(R.font.paeez, FontWeight.Normal),
)

// Set of Material typography styles to start with
val Typography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = VazirFontFamily),
    displayMedium = Typography().displayMedium.copy(fontFamily = VazirFontFamily),
    displaySmall = Typography().displaySmall.copy(fontFamily = VazirFontFamily),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = VazirFontFamily),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = VazirFontFamily),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = VazirFontFamily),
    titleLarge = Typography().titleLarge.copy(fontFamily = VazirFontFamily),
    titleMedium = Typography().titleMedium.copy(fontFamily = VazirFontFamily),
    titleSmall = Typography().titleSmall.copy(fontFamily = VazirFontFamily),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = VazirFontFamily, lineHeight = 26.sp),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = VazirFontFamily, lineHeight = 21.sp),
    bodySmall = Typography().bodySmall.copy(fontFamily = VazirFontFamily, lineHeight = 18.sp),
    labelLarge = Typography().labelLarge.copy(fontFamily = VazirFontFamily),
    labelMedium = Typography().labelMedium.copy(fontFamily = VazirFontFamily),
    labelSmall = Typography().labelSmall.copy(fontFamily = VazirFontFamily),
)