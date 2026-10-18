package com.shivarya.locationspeedtracker.core.hr

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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class HrMeasurement(val bpm: Int, val contact: Boolean?, val rrMs: List<Double>, val energyKj: Int?)

/** Bluetooth SIG Heart Rate Measurement (0x2A37) — pure, unit-tested. */
object HrParser {
    fun parse(v: ByteArray): HrMeasurement? {
        if (v.isEmpty()) return null
        val flags = v[0].toInt() and 0xFF
        var i = 1
        val u16 = flags and 0x01 != 0
        if (v.size < i + if (u16) 2 else 1) return null
        val bpm = if (u16) (v[i].toInt() and 0xFF) or ((v[i + 1].toInt() and 0xFF) shl 8) else v[i].toInt() and 0xFF
        i += if (u16) 2 else 1
        val contact = if (flags and 0x04 != 0) flags and 0x02 != 0 else null
        var energy: Int? = null
        if (flags and 0x08 != 0 && v.size >= i + 2) {
            energy = (v[i].toInt() and 0xFF) or ((v[i + 1].toInt() and 0xFF) shl 8)
            i += 2
        }
        val rr = mutableListOf<Double>()
        if (flags and 0x10 != 0) {
            while (i + 1 < v.size) {
                rr += ((v[i].toInt() and 0xFF) or ((v[i + 1].toInt() and 0xFF) shl 8)) * 1000.0 / 1024.0
                i += 2
            }
        }
        return HrMeasurement(bpm, contact, rr, energy)
    }
}

data class HrDevice(val address: String, val name: String, val rssi: Int)

enum class HrConnection { Idle, Connecting, Connected, Reconnecting }

data class HrState(
    val connection: HrConnection = HrConnection.Idle,
    val deviceName: String? = null,
    val bpm: Int? = null,
    val contact: Boolean? = null,
    val battery: Int? = null,
    val updatedAt: Long = 0,
    val scanning: Boolean = false,
    val found: List<HrDevice> = emptyList(),
    val error: String? = null,
) {
    /** A reading older than 5 s is stale (strap off, out of range). */
    fun liveBpm(now: Long = System.currentTimeMillis()) = bpm?.takeIf { now - updatedAt < 5_000 }
}

/**
 * Any standard Bluetooth LE heart-rate sensor (service 0x180D): chest straps (Polar, Garmin, Wahoo, Coospo…) and
 * watches with an HR-broadcast mode. Watches that only sync through their own app (e.g. boAt) reach SpeedTrack via
 * Health Connect instead. Reconnects automatically while a session wants it.
 */
@Singleton
class HeartRateMonitor @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val _state = MutableStateFlow(HrState())
    val state: StateFlow<HrState> = _state.asStateFlow()
    private var gatt: BluetoothGatt? = null
    private var wantedAddress: String? = null
    private var users = 0

    val supported: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter != null

    fun hasPermissions(): Boolean = permissions().all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun permissions(): List<String> = if (Build.VERSION.SDK_INT >= 31) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val bluetoothOn: Boolean get() = runCatching { adapter?.isEnabled == true }.getOrDefault(false)

    /** [bluetoothOn], live: users usually switch Bluetooth on from Quick Settings with this app still on screen. */
    fun bluetoothOnFlow(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(bluetoothOn)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(bluetoothOn)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    // ---- Scanning ------------------------------------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = runCatching { d.name }.getOrNull() ?: result.scanRecord?.deviceName ?: "Heart-rate sensor"
            _state.update { s ->
                val others = s.found.filterNot { it.address == d.address }
                s.copy(found = (others + HrDevice(d.address, name, result.rssi)).sortedByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(scanning = false, error = "Scan failed ($errorCode)") }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (!hasPermissions()) return
        _state.update { it.copy(scanning = true, found = emptyList(), error = null) }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }
            .onFailure { e -> _state.update { it.copy(scanning = false, error = e.message) } }
        main.postDelayed({ stopScan() }, 15_000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_state.value.scanning) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _state.update { it.copy(scanning = false) }
    }

    // ---- Connection ----------------------------------------------------------------------------------------------

    /** Ref-counted: the HR screen and a running session can both hold the connection. */
    fun acquire(address: String, name: String?) = main.post {
        users++
        if (wantedAddress == address && gatt != null) return@post
        connect(address, name)
    }

    fun release() = main.post {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) disconnect()
    }

    @SuppressLint("MissingPermission")
    private fun connect(address: String, name: String?) {
        if (!hasPermissions()) {
            _state.update { it.copy(error = "Bluetooth permission needed") }
            return
        }
        disconnectGatt()
        wantedAddress = address
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        _state.update { it.copy(connection = HrConnection.Connecting, deviceName = name ?: it.deviceName, error = null) }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun disconnect() {
        wantedAddress = null
        disconnectGatt()
        _state.update { it.copy(connection = HrConnection.Idle, bpm = null) }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _state.update { it.copy(connection = HrConnection.Connected) }
                    g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runCatching { g.close() }
                    if (gatt == g) gatt = null
                    val address = wantedAddress
                    if (address != null) {
                        // Out of range / strap taken off: keep trying while someone still wants it.
                        _state.update { it.copy(connection = HrConnection.Reconnecting) }
                        main.postDelayed({ if (wantedAddress == address && gatt == null) connect(address, null) }, 3_000)
                    } else {
                        _state.update { it.copy(connection = HrConnection.Idle) }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val hr = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (hr == null) {
                _state.update { it.copy(error = "Not a heart-rate sensor") }
                return
            }
            g.setCharacteristicNotification(hr, true)
            val cccd = hr.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // After notifications are on, read the battery level once (GATT allows one operation at a time).
            g.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)?.let { runCatching { g.readCharacteristic(it) } }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = onValue(c, value)

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            c.value?.let { onValue(c, it) }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) = onValue(c, value)

        @Deprecated("API < 33")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            c.value?.let { onValue(c, it) }
        }
    }

    private fun onValue(c: BluetoothGattCharacteristic, value: ByteArray) {
        when (c.uuid) {
            HR_MEASUREMENT -> HrParser.parse(value)?.let { m ->
                if (m.bpm in 25..250) {
                    _state.update { it.copy(bpm = m.bpm, contact = m.contact, updatedAt = System.currentTimeMillis(), connection = HrConnection.Connected) }
                }
            }
            BATTERY_LEVEL -> value.firstOrNull()?.let { b -> _state.update { it.copy(battery = b.toInt() and 0xFF) } }
        }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
