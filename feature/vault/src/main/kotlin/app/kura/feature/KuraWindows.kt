package app.kura.feature

import android.os.Build
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.*
import androidx.core.view.WindowCompat

@Suppress("DEPRECATION")
fun styleKuraWindow(window: Window, dark: Boolean, background: Int) {
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
    window.statusBarColor=background
    window.navigationBarColor=if(Build.VERSION.SDK_INT<26) android.graphics.Color.BLACK else background
    if(Build.VERSION.SDK_INT>=28) window.navigationBarDividerColor=background
    if(Build.VERSION.SDK_INT>=29) {
        window.isNavigationBarContrastEnforced=false
        window.isStatusBarContrastEnforced=false
    }
    WindowCompat.getInsetsController(window,window.decorView).apply {
        isAppearanceLightStatusBars=!dark
        isAppearanceLightNavigationBars=!dark
    }
}

@Composable
fun KuraDialog(dismiss: ()->Unit, content: @Composable ()->Unit) {
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.Inherit)) {
        val view=LocalView.current
        val window=(view.parent as? DialogWindowProvider)?.window
        val background=MaterialTheme.colorScheme.background
        SideEffect { window?.let { styleKuraWindow(it,background.luminance()<.5f,background.toArgb()) } }
        content()
    }
}
