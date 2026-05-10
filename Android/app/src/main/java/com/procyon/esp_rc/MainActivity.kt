package com.procyon.esp_rc

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.procyon.esp_rc.ui.ConnectionsState
import com.procyon.esp_rc.ui.JoyStick
import com.procyon.esp_rc.ui.StatusLine
import com.procyon.esp_rc.ui.XTrim
import com.procyon.esp_rc.ui.theme.ESPRCExcerciseTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContent {
            ESPRCExcerciseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Content(

                        modifier = Modifier.padding(innerPadding),
                        vm
                    )
                }
            }
        }
    }

}



@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Content(modifier: Modifier = Modifier, vm: MainViewModelInter) {

    var hasBtPermission by remember { mutableStateOf(false) }

    val btPermission = rememberMultiplePermissionsState(permissions = listOf( Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) { gotPermission ->
        if (gotPermission.values.all { true }) {
            hasBtPermission = true
        }
    }

    LaunchedEffect(Unit) {
        if(btPermission.permissions.any{it.status is PermissionStatus.Denied}){
            btPermission.launchMultiplePermissionRequest()
        } else {
            hasBtPermission = true
        }
    }

    ConstraintLayout(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        val pos by vm.joyStickPos.collectAsState()

        val (title,
            connectionState,
            joystick,
            readout,
            scanButton,
            xTrimSlider) = createRefs()

        Text(
            "ESP RC fun!",
            color = Color.White,
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(Color(0xAA013869))
                .padding(8.dp)
                .constrainAs(title) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                }
                .fillMaxWidth()
        )

        val connectionStatus by vm.connectionState.collectAsState()
        val telemetry by vm.telemetryState.collectAsState()

        StatusLine(
            state = connectionStatus,
            telemetry = telemetry,
            modifier = Modifier.constrainAs(connectionState) {
                top.linkTo(title.bottom, margin = 8.dp)
                centerHorizontallyTo(parent)
            },
        )
        JoyStick(
            modifier = Modifier.constrainAs(joystick) {
                centerTo(parent)
            },
            size = 260.dp,
            xy = {
                vm.updateJoystickPos(it.first, it.second)
            },
            )
        //input read out:
        Text(
            "X: ${pos.first}; Y: ${pos.second}",
            color = Color.White,
            modifier = Modifier.constrainAs(readout) {
                bottom.linkTo(joystick.top, margin = 32.dp)
                centerHorizontallyTo(parent)
            },
        )

        val xTrim by vm.xTrim.collectAsState()

        XTrim(modifier = Modifier.constrainAs(xTrimSlider){
            top.linkTo(joystick.bottom, margin = 30.dp)
            centerHorizontallyTo(parent)
        },trim = xTrim) { newTrim ->
            vm.updateXTrim(newTrim)
        }

        Button(modifier = Modifier.constrainAs(scanButton){
            bottom.linkTo(parent.bottom, margin = 16.dp)
            end.linkTo(parent.end, margin = 16.dp)
        }, onClick = vm::startScan,
            enabled = (connectionStatus == ConnectionsState.Disconnected || connectionStatus == ConnectionsState.Error) && hasBtPermission
        ) {
            Text("Connect", style = MaterialTheme.typography.bodyLarge)
        }
    }

}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    ESPRCExcerciseTheme {
        val scope = rememberCoroutineScope()
        Content(vm = MockViewModel(scope))
    }
}