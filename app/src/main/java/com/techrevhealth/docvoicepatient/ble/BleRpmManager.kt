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

    fun getCurrentDeviceData(): RpmDeviceData? = currentDeviceData


    val command = byteArrayOf(
        0x26.toByte(), // Command ID
        0x00.toByte(), // Index (high byte)
        0x00.toByte(), // Index (low byte)
        0x00.toByte(), // filler
        0x00.toByte(), // filler
        0x00.toByte(), // filler
        0x00.toByte(), // filler
        0x00.toByte()  // filler
    )

    private val readCommand = byteArrayOf(0x49, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

    private fun buildReadCommand(): ByteArray {
        Log.d(TAG, "buildReadCommand: ")
        val command = byteArrayOf(
            0x51.toByte(), // Start
            0x49.toByte(), // Command: Read device status
            0x00.toByte(), // Data 0
            0x00.toByte(), // Data 1
            0x00.toByte(), // Data 2
            0x00.toByte(), // Data 3
            0xA3.toByte(), // Stop
            0x00.toByte()  // Placeholder for checksum
        )
        command[7] = calculateChecksum(command)
        return command
    }

    private fun buildReadWeightMachineCommand(): ByteArray {
        Log.d(TAG, "buildReadWeightMachineCommand: ")
        val command = byteArrayOf(
            0x51.toByte(), // Start byte
            0x71.toByte(), // Command for reading weight data
            0x02.toByte(), // Data for device type, etc.
            0x01.toByte(), // Additional data byte
            0x00.toByte(), // Placeholder data byte
            0xA3.toByte(), // Stop byte
            0x00.toByte()  // Placeholder for checksum
        )
        Log.d(TAG,"Sending Read Command: ${command.joinToString(" ") { "%02X".format(it) }}")
        // Calculate checksum
        command[6] = calculateDynamicChecksum(command)  // Modify for correct index if needed
        Log.d(TAG, "Sending Read Command: ${command.joinToString(" ") { "%02X".format(it.toInt()) }}")
        return command
    }

    private fun calculateDynamicChecksum(data: ByteArray): Byte {
        var sum = 0
        for (i in 0 until data.size-1) {
            sum += data[i].toInt() and 0xFF
        }
        Log.d(TAG, "calculateDynamicChecksum: $sum")
        return (sum and 0xFF).toByte() // Ensures the checksum is within the byte range
    }

    // Function to calculate the checksum
    private fun calculateWeightChecksum(data: ByteArray): Byte {
        var sum = 0
        for (i in 0 until 34) { // Calculate sum up to the second-last byte
            sum += data[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte() // Return the checksum byte
    }


    private fun buildReadGlucoseTimeCommand(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),         // Start
            0x23.toByte(),         // CMD: Read time part
            0x00.toByte(), 0x00.toByte(),   // Index (0 = latest)
            0x00.toByte(), 0x00.toByte(),   // Unused
            0xA3.toByte(),         // Stop
            0x00.toByte()          // Checksum
        )
        command[7] = calculateChecksum(command)
        return command
    }

    private fun buildReadGlucoseResultCommand(): ByteArray {
        Log.d(TAG, "buildReadGlucoseResultCommand: ")
        val command = byteArrayOf(
            0x51.toByte(),
            0x26.toByte(),         // CMD: Read result part
            0x00.toByte(),
            0x00.toByte(),   // Index (0 = latest)
            0x00.toByte(),
            0x00.toByte(),
            0xA3.toByte(),
            0x00.toByte()
        )
        command[7] = calculateChecksum(command)
        return command
    }

    fun buildReadBpResultCommand(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x26.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00.toByte()
        )
        command[7] = calculateChecksum(command)
        return command
    }

    fun buildStopBpCommand(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x50.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00.toByte()
        )
        command[7] = calculateChecksum(command)
        return command
    }

    fun buildStopWeightMachineCommand(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x50.toByte(),
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00.toByte()
        )
        command[7] = calculateChecksum(command)
        return command
    }

    private fun buildGlucoseStopDeviceCommand(): ByteArray {
        Log.d(TAG, "buildGlucoseStopDeviceCommand: ")
        val command = byteArrayOf(
            0x51.toByte(),         // Start byte
            0x50.toByte(),         // CMD: Stop Device (Power Off)
            0x00.toByte(),
            0x00.toByte(),   // Reserved
            0x00.toByte(),
            0x00.toByte(),   // Reserved
            0xA3.toByte(),         // Stop byte
            0x00.toByte()          // Checksum (to be calculated)
        )
        command[7] = calculateChecksum(command)
        return command
    }

    private fun clearMemoryCommand(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(), // Start
            0x52.toByte(), // Command: Read device status
            0x00.toByte(), // Data 0
            0x00.toByte(), // Data 1
            0x00.toByte(), // Data 2
            0x00.toByte(), // Data 3
            0xA3.toByte(), // Stop
            0x00.toByte()  // Placeholder for checksum
        )
        command[7] = calculateChecksum(command)
        return command
    }

    // Battery + System Info
    private fun stopDeviceCommand(): ByteArray {
        Log.d(TAG, "stopDeviceCommand: ")
        val command = byteArrayOf(
            0x51.toByte(),
            0x50.toByte(), // Read device status
            0x00,
            0x00,
            0x00,
            0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }

    // Battery + System Info
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


    // Scan every 10 minutes
    private val scanIntervalMs = 2 * 1000L
    private val scanDurationMs = 15_000L // Scan for 10 seconds

    private val deviceNameKeywords = listOf(
        "FORA P20",
        "TNG SPO2",
        "TNG SCALE",
        "FORA PREMIUM V10"
    )

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

            if (deviceNameKeywords.any { keyword -> name.contains(keyword, ignoreCase = true) }) {
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

                if (deviceNameKeywords.any { keyword -> name.contains(keyword, ignoreCase = true) }) {
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
        connectedGatt = device.connectGatt(context, false, gattCallback)
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

                    gatt.requestMtu(50);



                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BleRpmManager", "Disconnected from $connectedDeviceName")
                    listener.onDisconnected()
                    connectedGatt?.close()
                    connectedGatt = null
                    connectedDeviceName = null
                    scheduleNextScan()
                }

            }

        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG,"onMtuChanged: $mtu")
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


                        if (characteristic.properties == 24) {

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
                                gatt.readCharacteristic(characteristic)

                                val descriptor = characteristic.getDescriptor(
                                    //characteristic.uuid
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                    //00002902-0000-1000-8000-00805f9b34fb
                                    //  UUID.fromString("00001524-1212-efde-1523-785feabcd123")
                                    // UUID.fromString("e3a219e1-7b22-4257-a9eb-281f4fffcd83")
                                )

                                if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                                    descriptor.value =
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                } else if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                                    descriptor.value =
                                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                }

                                descriptor?.let {
                                    gatt.writeDescriptor(it)
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

            when (deviceName.uppercase()) {
                "TNG SPO2", "FORA_SPO2" -> {
                    parseForaSpo2Data(data,gatt,characteristic)
                }
                "FORA P20" -> {
                    parseBpMonitorData(data,gatt,characteristic)
                }
                "FORA PREMIUM V10" -> {
                    parseGlucoseData(data,gatt,characteristic)
                }
                "TNG SCALE" -> {
                    parseWeightData(data,gatt,characteristic)
                }
                else -> Log.w("BLE", "Unknown device: $deviceName, Raw data: ${data.joinToString()}")
            }
            stop()
        }



        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicRead: value: $value"+"status: "+status)
            Log.d(TAG, "onCharacteristicRead: "+characteristic.value)
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
                characteristic?.setValue(chooseReadCommand())
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

    private fun stopDeviceStatus(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val chooseStopCommad = chooseStopCommand()
        characteristic.value = chooseStopCommad
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
        Log.d("BLE", "Stop Device command write success: $success")
    }

    private fun parseWeightData(data: ByteArray,gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // Log full byte array safely
        Log.d(TAG, "parseWeightData: ${data.joinToString(" ") { String.format("%02X", it) }}")
        Log.d(TAG, "Received Weight Data (${data.size} bytes):")
        for ((index, byte) in data.withIndex()) {
            Log.d(TAG, "Byte[$index]: ${byte.toInt() and 0xFF}")
        }

        if (data.isNotEmpty()) {
            if (data.size < 14) {
                Log.w(TAG, "Invalid weight data size: ${data.size}")
             //   listener.onUserCancelled()
                requestReadCommand(gatt, characteristic)
                return
            }

            when  (data[1].toInt() and 0xFF) {
                0x52 -> {
                    Log.d(TAG, "parseWeightData: Clear Memory Response")
                    // Wait and send read command
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestReadCommand(gatt, characteristic)
                    }, 700)
                }
                0x71 -> {
                    Log.d(TAG, "parseWeightData: Weight Data")
                    val recordIndex = ((data[4].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
                    val year =  (data[5].toInt() and 0xFF)
                    val month = data[6].toInt() and 0xFF
                    val day = data[7].toInt() and 0xFF
                    val hour = data[8].toInt() and 0xFF
                    val minute = data[9].toInt() and 0xFF

                    val weightRaw = ((data[17].toInt() and 0xFF) shl 8) or (data[18].toInt() and
                            0xFF)
                    val weightKg = weightRaw / 10.0

                    val bmi = data[21].toInt() and 0xFF

                    Log.d("FORA_WEIGHT", "Record #$recordIndex")
                    Log.d("FORA_WEIGHT", "Timestamp: $year-$month-$day $hour:$minute")
                    Log.d("FORA_WEIGHT", "Weight: $weightKg kg")
                    Log.d("FORA_WEIGHT", "BMI: $bmi")

                    currentDeviceData = RpmDeviceData(
                        deviceName = connectedDeviceName ?: "Unknown",
                        weight = weightKg,
                        bmi = bmi.toDouble())
                    stopDeviceStatus(gatt, characteristic)
                }
            }

        }

        currentDeviceData?.let { listener.onDataReceived(connectedDeviceName ?: "Unknown", it) }
    }

    private fun parseGlucoseData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "parseGlucoseData: 0: "+data[0].toInt()+" 1: "+data[1].toInt()+" " +
                "2: " + " "+data[2].toInt()+" 5: "+data[5].toInt()+" 6: "+data[6].toInt()+" 3: " +
                ""+data[3].toInt()+" 4: "+data[4].toInt())

        if (data.isNotEmpty()) {
           when (data[1].toInt() and 0xFF) {
               0x52 -> {
                   Log.d(TAG, "parseGlucoseData: Clear Memory Response")
                   // Wait and send read command
                   Handler(Looper.getMainLooper()).postDelayed({
                       requestReadCommand(gatt, characteristic)
                   }, 700) // delay must be >= 600ms to be safe
               }
               0x23 -> {
                   Log.d(TAG, "parseGlucoseData: TimeStamp: ")
                   val year = (data[1].toInt() shr 1) + 2000
                   val month = ((data[1].toInt() and 0x01) shl 3) or (data[0].toInt() shr 5)
                   val day = data[0].toInt() and 0x1F
                   val minute = data[2].toInt() and 0x3F
                   val hour = data[3].toInt() and 0x1F

                   Log.d(TAG, "parseGlucoseData: Date: $day/$month/$year")
                   Log.d(TAG, "parseGlucoseData: Time: $hour:$minute")

                   // Wait and send read command
                   Handler(Looper.getMainLooper()).postDelayed({
                       requestReadCommand(gatt, characteristic)
                   }, 700) // delay must be >= 600ms to be safe

               }
               0x26 -> {
                   Log.d(TAG, "parseGlucoseData: Data: ")
                   val spo2 = data[2].toInt() and 0xFF
                   val pulse = data[5].toInt() and 0xFF
                   Log.d("FORA PREMIUM V10", "Glucose: $spo2 mg/dl, Values 5: $pulse ")
                   currentDeviceData = RpmDeviceData(
                       deviceName = connectedDeviceName ?: "Unknown",
                       glucose = spo2.toDouble(),
                       pulse = pulse)
                   stopDeviceStatus(gatt, characteristic)
               }
           }
        }

        currentDeviceData?.let { listener.onDataReceived(connectedDeviceName ?: "Unknown", it) }
    }

    private fun parseBpMonitorData(data: ByteArray,gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "onCharacteristicChanged: 0: "+data[0].toInt() +" 1: "+data[1].toInt()+" " +
                "2: " + " "+data[2].toInt()+" 5: "+data[5].toInt()+" 6: "+data[6].toInt())

        if (data.isNotEmpty()) {
            when(data[1].toInt() and 0xFF){
                0x52 -> {
                    Log.d(TAG, "parseBpMonitorData: Clear Memory Response")
                    // Wait and send read command
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestReadCommand(gatt, characteristic)
                    }, 700) // delay must be >= 600ms to be safe
                }
                0x25 -> {
                        Log.d(TAG, "parseGlucoseData: Data: ")
                        val sys = data[2].toInt() and 0xFF       // Systolic
                        val dia = data[4].toInt() and 0xFF       // Diastolic
                        val pulse = data[5].toInt() and 0xFF     // Pulse

                    Log.d("FORA_BP", "SYS: $sys mmHg, DIA: $dia mmHg, Pulse: $pulse bpm")
                        currentDeviceData = RpmDeviceData(
                            deviceName = connectedDeviceName ?: "Unknown",
                            systolic = sys.toDouble(),
                            diastolic = dia.toDouble(),
                            pulse = pulse)
                        stopDeviceStatus(gatt, characteristic)

                }
                0x26 -> {
                    Log.d(TAG, "parseGlucoseData: Data: ")
                    val sys = data[2].toInt() and 0xFF       // Systolic
                    val dia = data[4].toInt() and 0xFF       // Diastolic
                    val pulse = data[5].toInt() and 0xFF     // Pulse

                    Log.d("FORA_BP", "SYS: $sys mmHg, DIA: $dia mmHg, Pulse: $pulse bpm")
                    currentDeviceData = RpmDeviceData(
                        deviceName = connectedDeviceName ?: "Unknown",
                        systolic = sys.toDouble(),
                        diastolic = dia.toDouble(),
                        pulse = pulse)
                    stopDeviceStatus(gatt, characteristic)
                }
            }
        }

        currentDeviceData?.let { listener.onDataReceived(connectedDeviceName ?: "Unknown", it) }
    }

    private fun parseForaSpo2Data(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "parseForaSpo2Data: 0: "+data[0].toInt()+" 1: "+data[1].toInt()+" " +
                "2: " + " "+data[2].toInt()+" 5: "+data[5].toInt()+" 6: "+data[6].toInt()+" 3: " +
                ""+data[3].toInt()+" 4: "+data[4].toInt())

        if (data.isNotEmpty()) {
            when (data[1].toInt() and 0xFF) {
                0x52 -> {
                    Log.d(TAG, "parseForaSpo2Data: Clear Memory Response")
                    // Wait and send read command
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestReadCommand(gatt, characteristic)
                    }, 700) // delay must be >= 600ms to be safe
                }
                0x27 -> {
                    Log.d(TAG, "parseForaSpo2Data: SerialNumber: ")
                    val serial = String.format(
                        "%02X%02X%02X%02X",
                        data[0],
                        data[1],
                        data[2],
                        data[3]
                    )
                    currentDeviceData = RpmDeviceData(
                        deviceName = connectedDeviceName ?: "Unknown",
                        serialNumber = serial)
                    Log.d("FORA_SPO2", "serial: $serial ")
                    // Wait and send read command
                    Handler(Looper.getMainLooper()).postDelayed({
                        requestReadCommand(gatt, characteristic)
                    }, 2000) // delay must be >= 600ms to be safe
                }
                0x49 -> {
                    Log.d(TAG, "parseForaSpo2Data: Data: ")
                    val spo2 = data[2].toInt() and 0xFF
                    val pulse = data[5].toInt() and 0xFF
                    Log.d("FORA_SPO2", "SpO2: $spo2%, Heart Rate: $pulse bpm")
                    currentDeviceData = RpmDeviceData(
                        deviceName = connectedDeviceName ?: "Unknown",
                        spo2 = spo2,
                        pulse = pulse)
                    stopDeviceStatus(gatt, characteristic) // Only if you're done
                }
                0x4F -> {
                    Log.d(TAG, "parseForaSpo2Data: battery")
                    val battery = data[2].toInt() and 0xFF
                    val firmware = data[4].toInt() and 0xFF
                    currentDeviceData = RpmDeviceData(
                        deviceName = connectedDeviceName ?: "Unknown",
                        battery = battery,
                        firmware = firmware.toString())
                    Log.d("FORA_SPO2", "Battery: $battery%, Firmware: $firmware")
                }


            }
        }


        currentDeviceData?.let { listener.onDataReceived(connectedDeviceName ?: "Unknown", it) }
    }

    private fun requestSerialStatus(gatt: BluetoothGatt, characteristic:
    BluetoothGattCharacteristic) {
        val batteryCommand = buildSerialCommand()
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

    private fun requestReadCommand(gatt: BluetoothGatt, characteristic:
    BluetoothGattCharacteristic) {
        val chooseReadCommand = chooseReadCommand()
        characteristic.setValue(chooseReadCommand)
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

    private fun chooseReadCommand(): ByteArray {
        if (connectedDeviceName.equals(RpmDeviceType.TNG_SPO2.displayName, true)) {
            return buildReadCommand()
        }else if (connectedDeviceName.equals(RpmDeviceType.FORA_PREMIUM_V10.displayName,true)){
            return buildReadGlucoseResultCommand()
        }else if (connectedDeviceName.equals(RpmDeviceType.FORA_P20.displayName,true)){
            return buildReadBpResultCommand()
        }else if (connectedDeviceName.equals(RpmDeviceType.TNG_SCALE.displayName,true)){
            Log.d(TAG, "chooseReadCommand: ")
            return buildReadWeightMachineCommand()
        }
        return buildReadWeightMachineCommand()
    }

    private fun chooseStopCommand():ByteArray{
        if (connectedDeviceName.equals(RpmDeviceType.TNG_SPO2.displayName, true)) {
            return stopDeviceCommand()
        }else if (connectedDeviceName.equals(RpmDeviceType.FORA_PREMIUM_V10.displayName,true)){
            return buildGlucoseStopDeviceCommand()
        }else if (connectedDeviceName.equals(RpmDeviceType.FORA_P20.displayName,true)){
            return buildStopBpCommand()
        }else if (connectedDeviceName.equals(RpmDeviceType.TNG_SCALE.displayName,true)){
            return buildStopWeightMachineCommand()
        }
        return stopDeviceCommand()
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
