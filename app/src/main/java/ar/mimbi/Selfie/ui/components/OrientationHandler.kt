package ar.mimbi.Selfie.ui.components

import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.mimbi.Selfie.data.AppOrientation
import ar.mimbi.Selfie.findActivity

@Composable
fun EnforceAppOrientation(appOrientation: AppOrientation?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val apply = {
        val activity = context.findActivity()
        if (activity != null && appOrientation != null) {
            val targetOrientation = when (appOrientation) {
                AppOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                AppOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                AppOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
            if (activity.requestedOrientation != targetOrientation) {
                android.util.Log.d("EnforceAppOrientation", "Setting requestedOrientation=$targetOrientation for $appOrientation")
                activity.requestedOrientation = targetOrientation
            }
        }
    }

    SideEffect {
        apply()
    }

    DisposableEffect(lifecycleOwner, appOrientation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                apply()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
