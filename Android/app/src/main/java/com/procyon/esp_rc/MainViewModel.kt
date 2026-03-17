package com.procyon.esp_rc

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.procyon.esp_rc.ui.ConnectionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application), MainViewModelInter {


    private val _joyStickPos = MutableStateFlow(Pair(0, 0))
    override val joyStickPos: StateFlow<Pair<Int, Int>> = _joyStickPos
    private val _connectionState = MutableStateFlow(ConnectionsState.Disconnected)
    override val connectionState: StateFlow<ConnectionsState> = _connectionState

    private val bleManager = BleManager(application) { state ->
        _connectionState.update { state }
    }

    override fun updateJoystickPos(x: Int, y: Int) {
        val pos = Pair(x, y)
        _joyStickPos.update { pos }
        if (_connectionState.value == ConnectionsState.Connected) {
            bleManager.sendJoystickData(pos)
        }
    }

    override fun startScan() {
        bleManager.startScan()
    }

}

class MockViewModel(private val scope: CoroutineScope) : MainViewModelInter {

    val TAG = this::class.simpleName

    private val _joyStickPos = MutableStateFlow(Pair(0, 0))
    override val joyStickPos: StateFlow<Pair<Int, Int>> = _joyStickPos

    private val _connectionState = MutableStateFlow(ConnectionsState.Disconnected)
    override val connectionState: StateFlow<ConnectionsState> = _connectionState

    override fun updateJoystickPos(x: Int, y: Int) {
        _joyStickPos.update { Pair(x, y) }
    }

    override fun startScan() {
        Log.d(TAG, "startScan: mock click")
        scope.launch {
            _connectionState.update { ConnectionsState.Scanning }
            delay(1000)
            _connectionState.update { ConnectionsState.Connecting }
            delay(2000)
            _connectionState.update { ConnectionsState.Connected }
            delay(1000)
            _connectionState.update { ConnectionsState.Disconnected }

        }
    }
}

interface MainViewModelInter {
    val joyStickPos: StateFlow<Pair<Int, Int>>
    fun updateJoystickPos(x: Int, y: Int)
    val connectionState: StateFlow<ConnectionsState>

    fun startScan()

}