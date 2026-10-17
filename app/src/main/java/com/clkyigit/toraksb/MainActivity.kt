package com.clkyigit.toraksb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.TableCalibration
import com.clkyigit.toraksb.ui.CalibrationScreen
import com.clkyigit.toraksb.ui.GuideScreen
import com.clkyigit.toraksb.ui.PlanFormState
import com.clkyigit.toraksb.ui.PlanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ToraksApp() }
            }
        }
    }
}

private enum class Screen { PLAN, CALIBRATION, GUIDE }

@Composable
private fun ToraksApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.PLAN) }
    val form = rememberSaveable(saver = PlanFormState.Saver) { PlanFormState() }
    var calibration by rememberSaveable { mutableStateOf(TableCalibration.NONE) }
    var calibrated by rememberSaveable { mutableStateOf(false) }
    var plan by rememberSaveable { mutableStateOf<NeedlePlan?>(null) }

    BackHandler(enabled = screen != Screen.PLAN) { screen = Screen.PLAN }

    Scaffold { padding ->
        val m = Modifier.padding(padding)
        when (screen) {
            Screen.PLAN -> PlanScreen(
                form = form,
                calibrated = calibrated,
                calibration = calibration,
                onCalibrate = { screen = Screen.CALIBRATION },
                onStart = { p -> plan = p; screen = Screen.GUIDE },
                modifier = m,
            )
            Screen.CALIBRATION -> CalibrationScreen(
                onDone = { c ->
                    calibration = c
                    calibrated = true
                    screen = Screen.PLAN
                },
                onCancel = { screen = Screen.PLAN },
                modifier = m,
            )
            Screen.GUIDE -> {
                val p = plan
                if (p != null) {
                    GuideScreen(
                        plan = p,
                        calibration = calibration,
                        calibrated = calibrated,
                        onBack = { screen = Screen.PLAN },
                        modifier = m,
                    )
                } else {
                    PlanScreen(form, calibrated, calibration,
                        onCalibrate = { screen = Screen.CALIBRATION },
                        onStart = { np -> plan = np; screen = Screen.GUIDE },
                        modifier = m)
                }
            }
        }
    }
}
