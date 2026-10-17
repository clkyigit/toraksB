package com.clkyigit.toraksb

import androidx.compose.ui.window.ComposeUIViewController
import com.clkyigit.toraksb.ui.App

/** iOS uygulamasının giriş noktası (Swift tarafından çağrılır). */
fun MainViewController() = ComposeUIViewController { App() }
