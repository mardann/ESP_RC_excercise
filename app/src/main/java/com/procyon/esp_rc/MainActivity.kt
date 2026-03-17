package com.procyon.esp_rc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.procyon.esp_rc.ui.JoyStick
import com.procyon.esp_rc.ui.StatusLine
import com.procyon.esp_rc.ui.theme.ESPRCExcerciseTheme

class MainActivity : ComponentActivity() {

    val vm: MainViewModel by viewModels()
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

@Composable
fun Content(modifier: Modifier = Modifier, vm: MainViewModelInter) {
    ConstraintLayout(
        modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        val pos by vm.joyStickPos.collectAsState()

        val (title, connectionState, joystick, readout) = createRefs()

        Text(
            "ESP RC fun!",
            color = Color.White,
            modifier = Modifier.constrainAs(title) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
            }
        )

        val connectionStatus by vm.connectionState.collectAsState()

        StatusLine(
            state = connectionStatus,
            modifier = Modifier.constrainAs(connectionState) {
                top.linkTo(title.bottom)
                centerHorizontallyTo(parent)
            },
        )
        JoyStick(
            modifier = Modifier.constrainAs(joystick) {
                centerTo(parent)
            },
            xy = {
                vm.updateJoystickPos(it.first, it.second)
            },

            )
        //input read out:
        Text(
            "X: ${pos.first}; Y: ${pos.second}",
            color = Color.White,
            modifier = Modifier.constrainAs(readout) {
                top.linkTo(joystick.bottom, margin = 32.dp)
                centerHorizontallyTo(parent)
            },
        )
    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ESPRCExcerciseTheme {
        Content(vm = MockViewModel)
    }
}