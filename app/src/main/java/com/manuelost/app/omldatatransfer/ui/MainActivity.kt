/*
 * Created by nphau on 11/19/22, 4:16 PM
 * Copyright (c) 2022 . All rights reserved.
 * Last modified 11/19/22, 3:58 PM
 */

package com.manuelost.app.omldatatransfer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.masewsg.app.ui.ComposeApp
import com.masewsg.app.ui.components.ThemePreviews
import com.masewsg.app.ui.components.button.ComposeButton
import com.masewsg.app.ui.components.button.ComposeOutlinedButton
import com.masewsg.app.ui.components.icon.ComposeIcons
import com.masewsg.app.ui.components.theme.ComposeTheme
import com.manuelost.app.omldatatransfer.EmbeddedServer
import com.manuelost.app.omldatatransfer.data.VideoEncoder
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.datacenter.camera.StreamInfo
import dji.v5.manager.SDKManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val getRunningServerInfo = { ticks: Int ->
    "The server is running on: ${Build.MODEL} at ${EmbeddedServer.host} -> (${ticks}s ....)"
}

class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_STORAGE = 123

    private lateinit var encoder: VideoEncoder
    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN
    private var cameraStreamManagerInstance: ICameraStreamManager? = null

    /** Listener that delivers every NAL unit coming from the aircraft */
    private val streamListener = object : ICameraStreamManager.ReceiveStreamListener {
        override fun onReceiveStream(
            data: ByteArray, offset: Int, length: Int,
            info: StreamInfo
        ) = encoder.handleFrame(data, offset, length, info)
    }

    override fun onStart() {
        super.onStart()
        val outputPath = "/storage/emulated/0/DCIM/dji_record.mp4"
        encoder = VideoEncoder(outputPath = outputPath)
        cameraStreamManagerInstance?.addReceiveStreamListener(cameraIndex, streamListener)
    }

    override fun onStop() {
        super.onStop()

        cameraStreamManagerInstance?.removeReceiveStreamListener(streamListener)

        if (encoder != null) {
            encoder?.release()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash screen on-screen until the UI state is loaded. This condition is
        // evaluated each time the app needs to be redrawn so it should be fast to avoid blocking
        // the UI.
        splashScreen.setKeepOnScreenCondition {
            false
        }
        // Turn off the decor fitting system windows, which allows us to handle insets,
        // including IME animations, and go edge-to-edge
        // This also sets up the initial system bar style based on the platform theme
        // enableEdgeToEdge()
        EmbeddedServer.init(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                REQUEST_CODE_STORAGE
            )
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE
            )
        }
        setContent {
            ComposeTheme {
                ComposeApp {
                    BoxWithConstraints {
                        val dynamicPadding = if (maxWidth < 400.dp) 8.dp else 32.dp
                        MainScreen(
                            modifier = Modifier
                                .padding(dynamicPadding)
                                .fillMaxWidth()
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScreen(modifier: Modifier = Modifier) {

    var ticks by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1.seconds)
            ticks++
        }
    }

    var hasStarted by remember { mutableStateOf(false) }

    val value by rememberInfiniteTransition(label = "")
        .animateFloat(
            initialValue = 0.8f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Reverse
            ), label = ""
        )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        val reusedModifier = Modifier.weight(1f)

        Spacer(modifier = reusedModifier)
        AnimatedLogo()
        Column(
            verticalArrangement = Arrangement.spacedBy(
                space = 20.dp,
                alignment = Alignment.CenterVertically
            )
        ) {
            Row {
                Icon(imageVector = ComposeIcons.PlayArrow, contentDescription = null)
                Text(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Start,
                    text = String.format("GET: %s", EmbeddedServer.host),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Row {
                Icon(imageVector = ComposeIcons.PlayArrow, contentDescription = null)
                Text(
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Start,
                    text = String.format("GET: %s/explorer", EmbeddedServer.host),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dp(36f))
        ) {
            ComposeButton(
                enabled = !hasStarted,
                modifier = reusedModifier,
                onClick = {
                    hasStarted = true
                    EmbeddedServer.start()
                },
                text = { Text("Start") }
            )
            Spacer(modifier = Modifier.weight(0.1f))
            ComposeOutlinedButton(
                enabled = hasStarted,
                modifier = reusedModifier,
                onClick = {
                    ticks = 0
                    hasStarted = false
                    EmbeddedServer.stop()
                },
                text = { Text("Stop") }
            )
        }

        Text(
            modifier = Modifier.graphicsLayer {
                if (hasStarted) {
                    scaleX = value
                    scaleY = value
                }
            },
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            text = if (hasStarted) {
                getRunningServerInfo(ticks)
            } else {
                "Please click 'Start' to start the embedded server"
            },
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = reusedModifier)
    }
}

@Composable
@ThemePreviews
private fun MainScreenPreview() {
    ComposeTheme {
        ComposeApp {
            MainScreen()
        }
    }
}