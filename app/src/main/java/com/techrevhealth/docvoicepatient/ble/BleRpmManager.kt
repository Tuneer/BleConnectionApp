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
import com.techrevhealth.docvoicepatient.ble.commands.*
import com.techrevhealth.docvoicepatient.ble.parsers.*
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
    
    // Device-specific parsers
    private var spo2Parser: Spo2ResponseParser? = null
    private var glucoseParser: GlucoseResponseParser? = null
    private var bpParser: BpResponseParser? = null
    private var weightParser: WeightResponseParser? = null

    fun getCurrentDeviceData(): RpmDeviceData? = currentDeviceData

    /**
     * Helper function to write characteristic
     * Used by parsers to send commands
     */
    private fun writeCharacteristic(command: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        characteristic.value = command
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val success = gatt.writeCharacteristic(characteristic)
        Log.d(TAG, "writeCharacteristic: Success=$success")
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
        
        // Initialize appropriate parser based on device type
        when {
            deviceName.contains("TNG SPO2", ignoreCase = true) || 
            deviceName.contains("FORA_SPO2", ignoreCase = true) -> {
                Log.d(TAG, "Initializing SPO2 Parser")
                spo2Parser = Spo2ResponseParser(
                    deviceName = deviceName,
                    onDataComplete = { data -> 
                        currentDeviceData = data
                        listener.onDataReceived(deviceName, data) 
                    },
                    writeCharacteristic = ::writeCharacteristic
                )
            }
            deviceName.contains("FORA PREMIUM V10", ignoreCase = true) -> {
                Log.d(TAG, "Initializing Glucose Parser")
                glucoseParser = GlucoseResponseParser(
                    deviceName = deviceName,
                    onDataComplete = { data ->
                        currentDeviceData = data
                        listener.onDataReceived(deviceName, data)
                    },
                    writeCharacteristic = ::writeCharacteristic
                )
            }
            deviceName.contains("FORA P20", ignoreCase = true) -> {
                Log.d(TAG, "Initializing BP Parser")
                bpParser = BpResponseParser(
                    deviceName = deviceName,
                    onDataComplete = { data ->
                        currentDeviceData = data
                        listener.onDataReceived(deviceName, data)
                    },
                    writeCharacteristic = ::writeCharacteristic
                )
            }
            deviceName.contains("TNG SCALE", ignoreCase = true) -> {
                Log.d(TAG, "Initializing Weight Parser")
                weightParser = WeightResponseParser(
                    deviceName = deviceName,
                    onDataComplete = { data ->
                        currentDeviceData = data
                        listener.onDataReceived(deviceName, data)
                    },
                    writeCharacteristic = ::writeCharacteristic
                )
            }
        }
        
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
            val data = characteristic.value ?: return
            
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            
            val deviceName = gatt.device.name ?: "UNKNOWN"
            Log.d(TAG, "onCharacteristicChanged: Device=$deviceName, CMD=0x${String.format("%02X", data.getOrNull(1) ?: 0)}")

            // Route to appropriate parser
            when {
                deviceName.contains("TNG SPO2", ignoreCase = true) ||
                deviceName.contains("FORA_SPO2", ignoreCase = true) -> {
                    spo2Parser?.parseResponse(data, gatt, characteristic)
                }
                deviceName.contains("FORA PREMIUM V10", ignoreCase = true) -> {
                    glucoseParser?.parseResponse(data, gatt, characteristic)
                }
                deviceName.contains("FORA P20", ignoreCase = true) -> {
                    bpParser?.parseResponse(data, gatt, characteristic)
                }
                deviceName.contains("TNG SCALE", ignoreCase = true) -> {
                    weightParser?.parseResponse(data, gatt, characteristic)
                }
                else -> Log.w(TAG, "Unknown device: $deviceName, Raw data: ${data.joinToString()}")
            }
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
            Log.d(TAG, "Descriptor write status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor write succeeded for $connectedDeviceName")

                val characteristic = descriptor?.characteristic
                characteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                
                // Send initial command based on device type
                val initialCommand = when {
                    connectedDeviceName?.contains("TNG SPO2", ignoreCase = true) == true ||
                    connectedDeviceName?.contains("FORA_SPO2", ignoreCase = true) == true -> {
                        Spo2Commands.clearMemory()
                    }
                    connectedDeviceName?.contains("FORA PREMIUM V10", ignoreCase = true) == true -> {
                        GlucoseCommands.clearMemory()
                    }
                    connectedDeviceName?.contains("FORA P20", ignoreCase = true) == true -> {
                        BpCommands.clearMemory()
                    }
                    connectedDeviceName?.contains("TNG SCALE", ignoreCase = true) == true -> {
                        WeightCommands.readClockTime()
                    }
                    else -> ByteArray(0)
                }
                
                characteristic?.value = initialCommand
                
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                
                CoroutineScope(Dispatchers.IO).launch {
                    delay(100)
                }
                val success = gatt?.writeCharacteristic(characteristic)
                Log.d(TAG, "Initial command write: Success=$success")

            } else {
                Log.e(TAG, "Descriptor write failed with status $status")
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
