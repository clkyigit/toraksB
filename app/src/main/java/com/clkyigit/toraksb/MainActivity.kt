package com.clkyigit.toraksb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.TableCalibration
import com.clkyigit.toraksb.ui.CalibrationScreen
import com.clkyigit.toraksb.ui.GuideScreen
import com.clkyigit.toraksb.ui.ImagePlanScreen
import com.clkyigit.toraksb.ui.ImagePlanState
import com.clkyigit.toraksb.ui.PlanFormState
import com.clkyigit.toraksb.ui.PlanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ToraksApp() }
            }
        }
    }
}

private enum class Screen { IMAGE, MANUAL, CALIBRATION, GUIDE }

@Composable
private fun ToraksApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.IMAGE) }
    // Kalibrasyon ve yönlendirmeden dönülecek planlama ekranı
    var planScreen by rememberSaveable { mutableStateOf(Screen.IMAGE) }
    val form = rememberSaveable(saver = PlanFormState.Saver) { PlanFormState() }
    val imageState = rememberSaveable(saver = ImagePlanState.Saver) { ImagePlanState() }
    var calibration by rememberSaveable { mutableStateOf(TableCalibration.NONE) }
    var calibrated by rememberSaveable { mutableStateOf(false) }
    var plan by rememberSaveable { mutableStateOf<NeedlePlan?>(null) }

    val toCalibration = { screen = Screen.CALIBRATION }
    val toGuide = { p: NeedlePlan -> plan = p; screen = Screen.GUIDE }

    CrashReportDialog()

    BackHandler(enabled = screen != Screen.IMAGE) {
        if (screen == Screen.MANUAL) planScreen = Screen.IMAGE
        screen = planScreen
    }

    Scaffold { padding ->
        val m = Modifier.padding(padding)
        when (screen) {
            Screen.IMAGE -> {
                ImagePlanScreen(
                    state = imageState,
                    calibrated = calibrated,
                    calibration = calibration,
                    onCalibrate = toCalibration,
                    onManual = { planScreen = Screen.MANUAL; screen = Screen.MANUAL },
                    onStart = toGuide,
                    modifier = m,
                )
            }
            Screen.MANUAL -> {
                PlanScreen(
                    form = form,
                    calibrated = calibrated,
                    calibration = calibration,
                    onCalibrate = toCalibration,
                    onStart = toGuide,
                    modifier = m,
                )
            }
            Screen.CALIBRATION -> CalibrationScreen(
                onDone = { c ->
                    calibration = c
                    calibrated = true
                    screen = planScreen
                },
                onCancel = { screen = planScreen },
                modifier = m,
            )
            Screen.GUIDE -> {
                val p = plan
                if (p != null) {
                    GuideScreen(
                        plan = p,
                        calibration = calibration,
                        calibrated = calibrated,
                        onBack = { screen = planScreen },
                        modifier = m,
                    )
                } else {
                    LaunchedEffect(Unit) { screen = planScreen }
                }
            }
        }
    }
}

@Composable
private fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val text = report ?: return
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = { CrashLog.clear(context); report = null },
        title = { Text("Uygulama önceki açılışta kapandı") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Bu hata kaydını kopyalayıp geliştiriciye gönderin:")
                Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Kopyala") }
        },
        dismissButton = {
            TextButton(onClick = { CrashLog.clear(context); report = null }) { Text("Kapat") }
        },
    )
}
