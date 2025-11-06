package com.techrevhealth.docvoicepatient.ble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData
import com.techrevhealth.docvoicepatient.helpers.RpmDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.Suppress


class BleRpmManager(
    private val context: Context,
    var listener: BleRpmListener
) {

    interface BleRpmListener {
        fun onScanStarted()
        fun onDeviceFound(deviceName: String)
        fun onConnecting(deviceName: String)
        fun onConnected(deviceName: String)
        fun onDataReceived(deviceName: String, data: RpmDeviceData)
        fun onDisconnected()
        fun onScanStopped()
        fun onUserCancelled()    // Called if user cancels Bluetooth or permission dialog
        fun onBluetoothEnableRequested()
        fun onPermissionRequestRequested()
    }

    private val TAG = "BleRpmManager"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    private var scanning = false
    private var connectedGatt: BluetoothGatt? = null
    private var connectedDeviceName: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentDeviceData: RpmDeviceData? = null
    private val brandManager = BrandManager()

    // GATT busy flag to prevent concurrent operations (matching JS implementation)
    private var gattBusy = false

    fun getCurrentDeviceData(): RpmDeviceData? = currentDeviceData





    // Scan every 10 minutes
    private val scanIntervalMs = 2 * 1000L
    private val scanDurationMs = 15_000L // Scan for 10 seconds

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            val name = device.name ?: return

            Log.d(TAG, "onScanResult: Found devices: $name")

            if (brandManager.shouldConnect(name)) {
                stopScan()
                listener.onDeviceFound(name)
                connectToDevice(device, name)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BleRpmManager", "Scan failed with error $errorCode")
            stopScan()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            for (result in results!!) {
                //result.getDevice() is scanned device
                val device = result.device
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                val name = device.name ?: return

                Log.d(TAG, "onScanResult: Found devices: $name")

                if (brandManager.shouldConnect(name)) {
                    stopScan()
                    listener.onDeviceFound(name)
                    connectToDevice(device, name)
                }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice, deviceName: String) {
        connectedDeviceName = deviceName
        listener.onConnecting(deviceName)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
            return
        }

        // For Salyx devices, try autoConnect=true to maintain connection
        val autoConnect = connectedDeviceName?.contains("SAL-", ignoreCase = true) == true
        connectedGatt = device.connectGatt(context, autoConnect, gattCallback)
        Log.d(TAG, "connectToDevice: Connecting to $deviceName with autoConnect=$autoConnect")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BleRpmManager", "Connected to $connectedDeviceName")
                    listener.onConnected(connectedDeviceName ?: "Unknown")
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.

                    }

                    // Try larger MTU for Salyx devices to prevent fragmentation
                    val mtuSize = if (connectedDeviceName?.contains("SAL-", ignoreCase = true) == true) 247 else 50
                    gatt.requestMtu(mtuSize);



                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BleRpmManager", "Disconnected from $connectedDeviceName, status: $status")

                    // For Salyx devices, try to reconnect if it was an unexpected disconnect
                    if (connectedDeviceName?.contains("SAL-", ignoreCase = true) == true &&
                        status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "Unexpected disconnect from Salyx device, attempting reconnect")
                        // Don't call listener.onDisconnected() immediately for Salyx
                        // Try to reconnect after a short delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (connectedGatt == null) { // Only if not already reconnected
                                Log.d(TAG, "Attempting to reconnect to Salyx device")
                                // The autoConnect flag should handle reconnection
                                scheduleNextScan() // Fallback to scanning if auto-reconnect fails
                            }
                        }, 2000)
                        return
                    }

                    listener.onDisconnected()
                    connectedGatt?.close()
                    connectedGatt = null
                    connectedDeviceName = null
                    scheduleNextScan()
                }

            }

        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG,"onMtuChanged: $mtu, status: $status")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "MTU change failed with status: $status")
                return
            }

             if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            val result = gatt?.discoverServices()
            if (result==true){
                Log.d("BleRpmManager", "GATT Services discovered")
            }else{
                Log.d("BleRpmManager", "GATT Services not discovered")
            }

            // For Salyx devices, use specification-compliant approach
            if (connectedDeviceName?.contains("SAL-", ignoreCase = true) == true) {
                CoroutineScope(Dispatchers.IO).launch {
                    delay(500) // Wait for services to be fully discovered
                    initializeSalyxDeviceNew(gatt)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt.services) {
                    Log.d("BLE", "Service UUID: ${service.uuid}")
                    for (characteristic in service.characteristics) {

                        val properties = characteristic.properties
                        Log.d("BLE", "Characteristic UUID: ${characteristic.uuid} (Properties: $properties)")

                        // Check if characteristic supports Notify or Indicate
                        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                            Log.d("BLE", "Supports notifications")
                        }

                        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0){
                            Log.d("BLE", "Supports indications")
                        }

                        // Check if characteristic supports READ
                        if ((properties and BluetoothGattCharacteristic.PROPERTY_READ != 0)) {
                            Log.d("BLE", "Supports read")
                        }

                        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
                            Log.d("BLE", "Supports write")
                        }

                        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
                            Log.d("BLE", "Supports write without response")
                        }

                        if (properties and BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT != 0) {
                            Log.d("BLE", "Supports default write type")
                        }


                        if (characteristic.properties == 24||characteristic.properties==18) {

                            if (characteristic.properties and
                                BluetoothGattCharacteristic
                                    .PROPERTY_NOTIFY != 0
                            ) {
                                Log.d(TAG, "onServicesDiscovered: INSIDE NOTIFY")
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    // TODO: Consider calling
                                    //    ActivityCompat#requestPermissions
                                    // here to request the missing permissions, and then overriding
                                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                    //                                          int[] grantResults)
                                    // to handle the case where the user grants the permission. See the documentation
                                    // for ActivityCompat#requestPermissions for more details.

                                }

                                gatt.setCharacteristicNotification(characteristic, true)
                                // Only read characteristic for Salyx devices, not for notifications
                                if (connectedDeviceName?.contains("SAL-", ignoreCase = true) == true) {
                                    characteristic?.let {
                                        Log.d(TAG, "onServicesDiscovered: " +
                                                "descripters:- "+characteristic.descriptors.toString())
                                        gatt.readCharacteristic(it)
                                    }
                                }

                                // Get the Client Characteristic Configuration Descriptor (CCCD)
                                val descriptor = characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                    //characteristic.uuid
                                )

                                if (descriptor != null) {
                                    if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                        descriptor.value =
                                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    } else if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                                        descriptor.value =
                                            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                    }

                                    gatt.writeDescriptor(descriptor)
                                } else {
                                    Log.w(TAG, "onServicesDiscovered: CCCD descriptor not found for characteristic ${characteristic.uuid}")
                                }


                            }

                        }

                    }
                }
            }
        }


        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "onCharacteristicChanged: Value "+characteristic.value)
            Log.d(TAG, "onCharacteristicChanged: Properties "+characteristic.properties.toString())
            val data = characteristic.value ?: return
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            val deviceName = gatt.device.name ?: "UNKNOWN"

                    val parsedData = brandManager.parseData(data, gatt, characteristic, deviceName, context)
                    if (parsedData != null) {
                        currentDeviceData = parsedData
                        listener.onDataReceived(deviceName, parsedData)
                        // Send stop command after successful data parsing
                        stopDeviceStatus(gatt, characteristic)
                    } else {
                        Log.w("BLE", "Failed to parse data for device: $deviceName, Raw data: ${data.joinToString()}")
                    }
        }



        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicRead: value: ${value.joinToString(" ") { String.format("%02X", it) }} status: $status")
            Log.d(TAG, "onCharacteristicRead: characteristic value: ${characteristic?.value?.joinToString(" ") { String.format("%02X", it) }}")

            // Handle read responses for Salyx devices
            if (connectedDeviceName?.contains("SAL-", ignoreCase = true) == true) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val parsedData = brandManager.parseData(value, gatt, characteristic, connectedDeviceName ?: "", context)
                    if (parsedData != null) {
                        currentDeviceData = parsedData
                        listener.onDataReceived(connectedDeviceName ?: "", parsedData)
                        // Send stop command after successful data parsing
                        stopDeviceStatus(gatt, characteristic)
                    }
                } else {
                    Log.w(TAG, "onCharacteristicRead: Read failed with status $status")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            Log.d("BLE", "Descriptor write status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BLE", "Descriptor write succeeded $connectedDeviceName")

                val characteristic = descriptor?.characteristic
                characteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

                // For Salyx devices, send start measurement command after descriptor write
                if (connectedDeviceName?.contains("SAL-", ignoreCase = true) == true) {
                    // Find the command characteristic for Salyx devices
                    val commandUUID = brandManager.getHandlerForDevice(connectedDeviceName ?: "")?.getCommandCharacteristicUUID()
                    val commandCharacteristic = if (commandUUID != null) {
                        gatt?.services?.flatMap { it.characteristics }?.find { it.uuid.toString() == commandUUID }
                    } else {
                        characteristic
                    }

                    // For Salyx devices: Send start measurement command (initialize was sent earlier)
                    val readCommand = brandManager.buildReadCommand(connectedDeviceName ?: "")
                    @Suppress("DEPRECATION")
                    readCommand?.let { commandCharacteristic?.setValue(it) }

                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        delay(100)
                        val measureSuccess = gatt?.writeCharacteristic(commandCharacteristic)
                        Log.d(TAG, "onDescriptorWrite: Start measurement command write success $measureSuccess")
                    }
                }
                else {
                    // For non-Salyx devices, use existing logic
                    val readCommand = brandManager.buildReadCommand(connectedDeviceName ?: "")
                    @Suppress("DEPRECATION")
                    readCommand?.let { characteristic?.setValue(it) }
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(100)
                    }
                    val success = gatt?.writeCharacteristic(characteristic)
                    Log.d(TAG, "onDescriptorWrite: Success Write $success")
                }

            } else {
                Log.e("BLE", "Descriptor write failed with status $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write status: $status")
            } else {
                Log.e(TAG, "Characteristic write failed with status $status")

            }
        }

    }

    private fun sendInitializeCommand(gatt: BluetoothGatt?) {
        if (gatt == null) return

        val commandUUID = brandManager.getHandlerForDevice(connectedDeviceName ?: "")?.getCommandCharacteristicUUID()
        val commandCharacteristic = if (commandUUID != null) {
            gatt.services?.flatMap { it.characteristics }?.find { it.uuid.toString() == commandUUID }
        } else {
            null
        }

        if (commandCharacteristic != null) {
            val salyxHandler = brandManager.getHandlerForDevice(connectedDeviceName ?: "") as? com.techrevhealth.docvoicepatient.ble.SalyxHandler
            val initCommand = salyxHandler?.buildInitializeCommand()
            @Suppress("DEPRECATION")
            initCommand?.let { commandCharacteristic.setValue(it) }

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val success = gatt.writeCharacteristic(commandCharacteristic)
            Log.d(TAG, "sendInitializeCommand: Initialize command write success $success")
        } else {
            Log.w(TAG, "sendInitializeCommand: Command characteristic not found")
        }
    }

    // NEW: Specification-compliant Salyx device initialization (matching JS implementation)
    private fun initializeSalyxDeviceNew(gatt: BluetoothGatt?) {
        if (gatt == null || connectedDeviceName?.contains("SAL-", ignoreCase = true) != true) return

        Log.d(TAG, "initializeSalyxDeviceNew: Starting specification-compliant initialization")

        val salyxHandler = brandManager.getHandlerForDevice(connectedDeviceName ?: "") as? com.techrevhealth.docvoicepatient.ble.SalyxHandler
        if (salyxHandler == null) {
            Log.e(TAG, "initializeSalyxDeviceNew: SalyxHandler not found")
            return
        }

        // Get the correct User Control characteristic for commands
        val userControlUUID = salyxHandler.getUserControlCharacteristicUUID()
        val userControlChar = gatt.services?.flatMap { it.characteristics }?.find { it.uuid.toString() == userControlUUID }

        if (userControlChar == null) {
            Log.e(TAG, "initializeSalyxDeviceNew: User Control characteristic not found")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Enable notifications for all data characteristics first (matching JS order)
                enableSalyxNotificationsNew(gatt)

                delay(500) // Wait for notifications to be set up

                // Step 2: Check device state by reading User Control characteristic
                Log.d(TAG, "initializeSalyxDeviceNew: Reading device state")
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val readSuccess = gatt.readCharacteristic(userControlChar)
                    Log.d(TAG, "initializeSalyxDeviceNew: Read device state success: $readSuccess")
                }

                delay(1000) // Wait for state response

                // Step 3: Handle passkey requirement if device is unverified (s2 state)
                // In the JS implementation, passkey is handled separately via user input
                // For now, we'll assume the device is already verified or handle it later

                // Step 4: Send start dataset command (c1) when device is in ready state (s3)
                // In JS: writeCommand('1') which sends "c1"
                Log.d(TAG, "initializeSalyxDeviceNew: Sending start dataset command")

                // Wait for GATT to not be busy (matching JS implementation)
                while (gattBusy) {
                    delay(10)
                }
                gattBusy = true

                val startCommand = salyxHandler.buildStartDatasetCommand()
                @Suppress("DEPRECATION")
                userControlChar.setValue(startCommand)

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val writeSuccess = gatt.writeCharacteristic(userControlChar)
                    Log.d(TAG, "initializeSalyxDeviceNew: Start dataset command write success: $writeSuccess")
                }

                gattBusy = false

                // Device will now start sending PPG data via notifications (matching JS flow)

            } catch (e: Exception) {
                Log.e(TAG, "initializeSalyxDeviceNew: Error during initialization", e)
                gattBusy = false
            }
        }
    }

    // NEW: Enable notifications for specification-compliant characteristics (matching JS order)
    private fun enableSalyxNotificationsNew(gatt: BluetoothGatt?) {
        if (gatt == null) return

        Log.d(TAG, "enableSalyxNotificationsNew: Setting up notifications for spec-compliant characteristics")

        val salyxHandler = brandManager.getHandlerForDevice(connectedDeviceName ?: "") as? com.techrevhealth.docvoicepatient.ble.SalyxHandler
        if (salyxHandler == null) return

        // Enable notifications in the same order as JS implementation
        val notificationUUIDs = listOf(
            salyxHandler.getUserControlCharacteristicUUID(), // Control (status updates) - first in JS
            salyxHandler.getVitalsDataCharacteristicUUID(),  // Data (PPG) - second in JS
            salyxHandler.getTemperatureCharacteristicUUID(), // Temperature - third in JS
            "00002a19-0000-1000-8000-00805f9b34fb"          // Battery Level (standard)
        )

        for (uuid in notificationUUIDs) {
            val characteristic = gatt.services?.flatMap { it.characteristics }?.find { it.uuid.toString() == uuid }
            if (characteristic != null && (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val notifySuccess = gatt.setCharacteristicNotification(characteristic, true)
                    Log.d(TAG, "enableSalyxNotificationsNew: Notification enabled for $uuid: $notifySuccess")

                    // Write CCCD descriptor to enable notifications (same as JS)
                    val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    if (cccd != null) {
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        val descriptorSuccess = gatt.writeDescriptor(cccd)
                        Log.d(TAG, "enableSalyxNotificationsNew: CCCD write for $uuid: $descriptorSuccess")
                    } else {
                        Log.w(TAG, "enableSalyxNotificationsNew: CCCD descriptor not found for $uuid")
                    }
                }
            } else {
                Log.w(TAG, "enableSalyxNotificationsNew: Characteristic not found or doesn't support notify: $uuid")
            }
        }

        Log.d(TAG, "enableSalyxNotificationsNew: Notification setup complete")
    }

    private fun stopDeviceStatus(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // For Salyx devices, use specification-compliant stop command
        if (connectedDeviceName?.contains("SAL-", ignoreCase = true) == true) {
            stopSalyxDeviceNew(gatt)
            return
        }

        // Legacy stop command for other devices
        val stopCommand = brandManager.buildStopCommand(connectedDeviceName ?: "")
        @Suppress("DEPRECATION")
        stopCommand?.let { characteristic.value = it }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val success = gatt.writeCharacteristic(characteristic)
            Log.d("BLE", "Stop Device command write success: $success")
        }

        // Give device time to process stop command
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Stop command processing complete")
            stop()
        }, 500)
    }

    // NEW: Specification-compliant Salyx device stop
    private fun stopSalyxDeviceNew(gatt: BluetoothGatt?) {
        if (gatt == null || connectedDeviceName?.contains("SAL-", ignoreCase = true) != true) return

        Log.d(TAG, "stopSalyxDeviceNew: Stopping Salyx device with spec-compliant command")

        val salyxHandler = brandManager.getHandlerForDevice(connectedDeviceName ?: "") as? com.techrevhealth.docvoicepatient.ble.SalyxHandler
        if (salyxHandler == null) return

        // Get User Control characteristic for stop command
        val userControlUUID = salyxHandler.getUserControlCharacteristicUUID()
        val userControlChar = gatt.services?.flatMap { it.characteristics }?.find { it.uuid.toString() == userControlUUID }

        if (userControlChar != null) {
            // Send stop dataset command (c2)
            // Wait for GATT to not be busy (matching JS implementation)
            CoroutineScope(Dispatchers.IO).launch {
                while (gattBusy) {
                    delay(10)
                }
                gattBusy = true

                val stopCommand = salyxHandler.buildStopDatasetCommand()
                @Suppress("DEPRECATION")
                userControlChar.setValue(stopCommand)

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    val writeSuccess = gatt.writeCharacteristic(userControlChar)
                    Log.d(TAG, "stopSalyxDeviceNew: Stop dataset command write success: $writeSuccess")
                }

                gattBusy = false
            }
        }

        // Give device time to process stop command
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "stopSalyxDeviceNew: Stop command processing complete")
            stop()
        }, 500)
    }



    private fun requestSerialStatus(gatt: BluetoothGatt, characteristic:
    BluetoothGattCharacteristic) {
        val batteryCommand = buildSerialCommand()
        @Suppress("DEPRECATION")
        characteristic.value = batteryCommand
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        val success = gatt.writeCharacteristic(characteristic)
        Log.d("BLE", "Serial Number command write success: $success")
    }

    private fun buildSerialCommand(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x27.toByte(), // Read device status
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }

    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (i in 0 until 7) {
            sum += data[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }

    private fun requestReadCommand(gatt: BluetoothGatt, characteristic:
    BluetoothGattCharacteristic) {
        val readCommand = brandManager.buildReadCommand(connectedDeviceName ?: "")
        @Suppress("DEPRECATION")
        readCommand?.let { characteristic.setValue(it) }
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        val success = gatt.writeCharacteristic(characteristic)
        Log.d("BLE", "Read command write success: $success")
    }



    fun start() {
        startScan()
    }

    fun stop() {
        stopScan()
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }

    private fun startScan() {
        if (scanning) return

        if (!checkBluetoothAndPermissions()) {
            Log.w(TAG, "Bluetooth not enabled or permissions missing. Skipping scan.")
            scheduleNextScan()
            return
        }

        Log.d("BleRpmManager", "Starting BLE scan...")
        listener.onScanStarted()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                }
            }
            .build()

        // No filters — filtering done in callback by device name
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothLeScanner.startScan(null, settings, scanCallback)
        scanning = true


    }

    private fun stopScan() {
        if (!scanning) return
        Log.d("BleRpmManager", "Stopping BLE scan...")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothLeScanner.stopScan(scanCallback)
        scanning = false
        listener.onScanStopped()
    }

    private fun scheduleNextScan() {
        handler.postDelayed({ startScan() }, scanIntervalMs)
    }

    private fun disconnect() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        connectedGatt?.disconnect()
        connectedGatt?.close()
        connectedGatt = null
        connectedDeviceName = null
    }

    private fun checkBluetoothAndPermissions(): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported.")
            return false
        }

        if (!bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled. Requesting user to enable.")
            if (context is Activity) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                context.startActivityForResult(enableBtIntent, 1001)
            }
            return false
        }

        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            Log.w(TAG, "Bluetooth or Location permissions missing. Requesting permissions.")
            if (context is Activity) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(
                        context,
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ),
                        2001
                    )
                } else {
                    ActivityCompat.requestPermissions(
                        context,
                        arrayOf(
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ),
                        2001
                    )
                }
            }
            return false
        }

        return true
    }

}
