package com.procyon.esp_rc

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.procyon.esp_rc.ui.ConnectionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class BleManager(private val context: Context, val status: (ConnectionsState) -> Unit) {
    private val TAG = this::class.simpleName
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val posFlow = MutableSharedFlow<Triple<Int, Int, Int>>(extraBufferCapacity = 1)

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch {
            posFlow
                .sample(100.milliseconds)
                .collect {
                    performWrite(it)
                }
        }
    }

    fun startScan() {
        if (bluetoothAdapter == null) {
            status(ConnectionsState.Error)
            return
        }
        status(ConnectionsState.Scanning)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val setting = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val scanner = bluetoothAdapter!!.bluetoothLeScanner
        scanner.startScan(listOf(filter), setting, object : ScanCallback() {


            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                scanner.stopScan(this)
                result?.also {
                    connect(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                status(ConnectionsState.Error)
            }
        })
    }

    fun connect(device: BluetoothDevice) {
        status(ConnectionsState.Connecting)
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        gatt?.discoverServices()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        status(ConnectionsState.Disconnected)
                        bluetoothGatt?.close()
                        bluetoothGatt = null
                    }
                }

            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    status(ConnectionsState.Connected)
                }
            }

        })
    }

    fun sendJoystickData(pos: Triple<Int, Int, Int>) {
        posFlow.tryEmit(pos)
    }

    private fun performWrite(pos: Triple<Int, Int, Int>) {
        val (x: Int, y: Int, xTrim: Int) = pos
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

        if (characteristic != null) {
            val payload = byteArrayOf(x.toByte(), y.toByte(), xTrim.toByte())
            Log.d(TAG, "performWrite: payload = $payload")
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            )
        }
    }


}