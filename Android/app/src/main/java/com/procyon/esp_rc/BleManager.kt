package com.procyon.esp_rc

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.procyon.esp_rc.ui.ConnectionsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
@OptIn(FlowPreview::class)
class BleManager(private val context: Context, val status: (ConnectionsState) -> Unit, val telemetry:( Telemetry) -> Unit) {

    companion object{
        const val TRIM_NA = 128
    }
    data class Telemetry(val distanceMm: Int = 0, val carXTrim: Int = TRIM_NA);
    var localTelemetry = Telemetry()

    private val TAG = this::class.simpleName
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val posFlow = MutableStateFlow<Triple<Int, Int, Int>>(Triple(0,0, TRIM_NA))

    private val scope = CoroutineScope(Dispatchers.IO)
    private val timeoutScope = CoroutineScope(Dispatchers.Default)
    var timeoutJob : Job? = null
    var reconnectJob :  Job? = null
    var watchdogJob: Job? = null
    var heartbeatJob: Job? = null
    var lastActivityMillis: Long = 0



    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (bluetoothAdapter == null) {
            status(ConnectionsState.Error)
            return
        }
        status(ConnectionsState.Scanning)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()
        val setting = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        val scanner = bluetoothAdapter!!.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {

            @SuppressLint("MissingPermission")
            @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                scanner.stopScan(this)
                result?.also {
                    timeoutJob?.cancel()
                    connect(result.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                status(ConnectionsState.Error)
            }
        }

        timeoutJob = timeoutScope.launch {
            delay(10.seconds)
            scanner.stopScan(scanCallback)
            status(ConnectionsState.Disconnected)
        }


        scanner.startScan(listOf(filter), setting, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        status(ConnectionsState.Connecting)
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        gatt?.discoverServices()
                        reconnectJob?.cancel()
                        lastActivityMillis = System.currentTimeMillis()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        watchdogJob?.cancel()
                        heartbeatJob?.cancel()
                        lastActivityMillis = 0
                        attemptReconnect()

                    }
                }

            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    status(ConnectionsState.Connected)

                    val uplinkCharactaristic = gatt.getService(SERVICE_UUID).getCharacteristic(UPLINK_CHARACTARISTIC_UUID)
                    gatt.setCharacteristicNotification(uplinkCharactaristic, true)

                    val descriptor = uplinkCharactaristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

                    gatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

                    // reset activity timestamp and start watchdog + heartbeat
                    lastActivityMillis = System.currentTimeMillis()
                    watchdogJob?.cancel()
                    watchdogJob = scope.launch {
                        while (true) {
                            val now = System.currentTimeMillis()
                            if (now - lastActivityMillis > 500) {
                                // considered lost — force reconnect
                                attemptReconnect()
                                break
                            }
                            delay(100)
                        }
                    }

                    heartbeatJob?.cancel()
                    heartbeatJob = scope.launch {
                        while (true){
                            performWrite(posFlow.value)
                            delay(100)
                        }
                    }

                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                // successful write ack from server
                lastActivityMillis = System.currentTimeMillis()
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                Log.d(TAG, "onCharacteristicChanged: charactaristic uuid = ${characteristic.uuid}, values = $value")
                if(characteristic.uuid == UPLINK_CHARACTARISTIC_UUID){
                    val distanceMm = ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                    val car_x_trim = ((value[2].toInt()) and 0xFF) - 128
                    Log.d(TAG, "onCharacteristicChanged:  x_trim uplinked from car = $car_x_trim")
//                    val centiVolt = ((value[2].toInt() and 0xFF) shl 8) or (value[2].toInt() and 0xFF)
//                    val percent = value[4].toInt() and 0xFF

                    localTelemetry = localTelemetry.copy(distanceMm, car_x_trim)

                    telemetry(localTelemetry)
                    lastActivityMillis = System.currentTimeMillis()
                }
            }

        })
    }

    fun disconnect(){
        status(ConnectionsState.Disconnected)
        watchdogJob?.cancel()
        heartbeatJob?.cancel()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private val reconnectDuration = 60_000L

    fun attemptReconnect(){
        status(ConnectionsState.Reconnecting)
        watchdogJob?.cancel()
        heartbeatJob?.cancel()
        bluetoothGatt?.close()
        bluetoothGatt = null

        reconnectJob = scope.launch {
            val startReconnectTimeStamp = System.currentTimeMillis()
            while (System.currentTimeMillis() - startReconnectTimeStamp < reconnectDuration && bluetoothGatt == null){

                startScan()

                delay(5.seconds)
            }

            if(bluetoothGatt == null){
                status(ConnectionsState.Disconnected)
            }
        }


    }

    fun sendJoystickData(pos: Triple<Int, Int, Int>) {
        posFlow.update{
            pos
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun performWrite(pos: Triple<Int, Int, Int>) {
        val (x: Int, y: Int, xTrim: Int) = pos
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(DRIVE_CHARACTERISTIC_UUID)

        if (characteristic != null) {
            val payload = byteArrayOf(x.toByte(), y.toByte(), xTrim.toByte())
            Log.d(TAG, "performWrite: payload = ${payload.toString()}")
            // use default write to receive an acknowledgement from the server
            bluetoothGatt?.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            lastActivityMillis = System.currentTimeMillis()
        }
    }


}