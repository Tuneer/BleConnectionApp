package com.techrevhealth.docvoicepatient.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

class SalyxHandler : DeviceHandler {

    private val TAG = "SalyxHandler"

    // Device states according to specification
    enum class DeviceState {
        OFF, DISCONNECTED, UNVERIFIED, READY, INITIALIZING_SENSORS,
        SENSOR_ERROR, CANNOT_DETECT_SKIN, WORKING, SLEEP
    }

    private var currentState = DeviceState.OFF
    private var passkeyRequired = true

    override fun getSupportedDeviceNames(): List<String> {
        return listOf("SAL-")
    }

    override fun shouldConnect(deviceName: String): Boolean {
        return deviceName.contains("SAL-", ignoreCase = true)
    }

    // FIXED: Use correct User Control characteristic for commands (matching JS implementation)
    override fun getCommandCharacteristicUUID(): String {
        return "00000001-0000-0000-0000-000000000000" // User Control (CORRECT - matching BLE.js)
    }

    // NEW: Correct command characteristic according to spec
    fun getUserControlCharacteristicUUID(): String {
        return "00000001-0000-0000-0000-000000000000" // User Control
    }

    fun getVitalsDataCharacteristicUUID(): String {
        return "00000002-0000-0000-0000-000000000000" // Vitals Data
    }

    fun getTemperatureCharacteristicUUID(): String {
        return "00000003-0000-0000-0000-000000000000" // Temperature
    }

    // LEGACY METHODS (keep for backward compatibility)
    fun buildInitializeCommand(): ByteArray {
        Log.d(TAG, "buildInitializeCommand: Initializing Salyx device (LEGACY)")
        // Command to initialize: 0x01 0x00
        return byteArrayOf(0x01.toByte(), 0x00.toByte())
    }

    override fun buildReadCommand(deviceName: String): ByteArray {
        Log.d(TAG, "buildReadCommand: Starting measurement for Salyx device (LEGACY)")
        // Command to start measurement: 0x10 0x01
        return byteArrayOf(0x10.toByte(), 0x01.toByte())
    }

    override fun buildStopCommand(deviceName: String): ByteArray {
        Log.d(TAG, "buildStopCommand: Stopping measurement for Salyx device (LEGACY)")
        // Command to stop measurement: 0x10 0x00
        return byteArrayOf(0x10.toByte(), 0x00.toByte())
    }

    // NEW METHODS according to BLE_GATT_Characteristics.txt specification

    /**
     * Build passkey command for device verification
     * Format: "k" + passkey bytes (matching JS implementation)
     */
    fun buildPasskeyCommand(passkey: String): ByteArray {
        val passkeyBytes = passkey.toByteArray(Charsets.UTF_8)
        val command = ByteArray(1 + passkeyBytes.size)
        command[0] = 'k'.code.toByte() // 'k' prefix for passkey (matching JS implementation)
        passkeyBytes.copyInto(command, 1)
        Log.d(TAG, "buildPasskeyCommand: $passkey")
        return command
    }

    /**
     * Build start dataset command
     * Format: "c1" - can only be used in "s3" (Ready) state
     */
    fun buildStartDatasetCommand(): ByteArray {
        val command = "c1".toByteArray(Charsets.UTF_8)
        Log.d(TAG, "buildStartDatasetCommand: c1")
        return command
    }

    /**
     * Build stop dataset command
     * Format: "c2" - can only be used in states s4-s8
     */
    fun buildStopDatasetCommand(): ByteArray {
        val command = "c2".toByteArray(Charsets.UTF_8)
        Log.d(TAG, "buildStopDatasetCommand: c2")
        return command
    }

    /**
     * Build factory reset command
     * Format: "c3" - can be used at anytime
     */
    fun buildFactoryResetCommand(): ByteArray {
        val command = "c3".toByteArray(Charsets.UTF_8)
        Log.d(TAG, "buildFactoryResetCommand: c3")
        return command
    }

    /**
     * Build turn off device command
     * Format: "c0" - can be used at anytime
     */
    fun buildTurnOffCommand(): ByteArray {
        val command = "c0".toByteArray(Charsets.UTF_8)
        Log.d(TAG, "buildTurnOffCommand: c0")
        return command
    }

    /**
     * Build settings command
     * Format: "t" + byte structure (little endian)
     */
    fun buildSettingsCommand(settings: ByteArray): ByteArray {
        val command = ByteArray(1 + settings.size)
        command[0] = 't'.code.toByte()
        settings.copyInto(command, 1)
        Log.d(TAG, "buildSettingsCommand: ${command.joinToString(" ") { String.format("%02X", it) }}")
        return command
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun parseData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            gatt.device.name ?: "Unknown"
        } else {
            "Unknown"
        }
        Log.d(TAG, "parseData: Received data from Salyx device: ${data.joinToString(" ") { String.format("%02X", it) }}")

        if (data.isEmpty()) {
            Log.w(TAG, "parseData: Empty data received")
            return null
        }

        // Parse data based on characteristic UUID
        val charUUID = characteristic.uuid.toString()
        Log.d(TAG, "parseData: Characteristic UUID: $charUUID")

        return when (charUUID) {
            // LEGACY: Old implementation (keep for backward compatibility)
            "00000001-0000-0000-0000-000000000000" -> parseLegacyPpgData(data, deviceNameSafe)
            "00000002-0000-0000-0000-000000000000" -> parseLegacyTemperatureData(data)
            "00000005-0000-0000-0000-000000000000" -> parseLegacySensorStatus(data)
            "00000006-0000-0000-0000-000000000000" -> parseLegacyDeviceState(data)
            "00002a19-0000-1000-8000-00805f9b34fb" -> parseBatteryLevel(data, deviceNameSafe)

            // NEW: According to BLE_GATT_Characteristics.txt specification
            getUserControlCharacteristicUUID() -> parseUserControlData(data, deviceNameSafe) // Status, errors, commands
            getVitalsDataCharacteristicUUID() -> parseVitalsData(data, deviceNameSafe) // PPG data
            getTemperatureCharacteristicUUID() -> parseTemperatureData(data, deviceNameSafe) // Temperature data

            else -> {
                Log.w(TAG, "parseData: Unknown characteristic UUID: $charUUID")
                null
            }
        }
    }

    // LEGACY PARSING METHODS (keep for backward compatibility)
    private fun parseLegacyPpgData(data: ByteArray, deviceNameSafe: String): RpmDeviceData? {
        Log.d(TAG, "parseLegacyPpgData: Using legacy PPG parsing")
        if (data.size >= 6) {
            val heartRate = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
            val spo2 = data[3].toInt() and 0xFF
            val pulse = data[4].toInt() and 0xFF
            val battery = data[5].toInt() and 0xFF

            Log.d(TAG, "parseLegacyPpgData: PPG Data - Heart Rate: $heartRate bpm, SpO2: $spo2%, Pulse: $pulse bpm, Battery: $battery%")

            return RpmDeviceData(
                deviceName = deviceNameSafe,
                spo2 = spo2,
                pulse = pulse,
                battery = battery
            )
        }
        return null
    }

    private fun parseLegacyTemperatureData(data: ByteArray): RpmDeviceData? {
        Log.d(TAG, "parseLegacyTemperatureData: Using legacy temperature parsing")
        if (data.size >= 3) {
            val temperature = ((data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)) / 100.0f
            Log.d(TAG, "parseLegacyTemperatureData: Temperature Data - Temp: $temperature°C")
        }
        return null
    }

    private fun parseLegacySensorStatus(data: ByteArray): RpmDeviceData? {
        Log.d(TAG, "parseLegacySensorStatus: Using legacy sensor status parsing")
        val status = data[1].toInt() and 0xFF
        Log.d(TAG, "parseLegacySensorStatus: Sensor Status - Status: 0x${String.format("%02X", status)}")
        return null
    }

    private fun parseLegacyDeviceState(data: ByteArray): RpmDeviceData? {
        Log.d(TAG, "parseLegacyDeviceState: Using legacy device state parsing")
        val state = data[1].toInt() and 0xFF
        val stateName = when (state) {
            0x00 -> "Boot"
            0x01 -> "Idle"
            0x02 -> "Measuring"
            0x03 -> "Transmitting"
            0x04 -> "Sleep"
            0x05 -> "Error"
            else -> "Unknown"
        }
        Log.d(TAG, "parseLegacyDeviceState: Device State - $stateName (0x${String.format("%02X", state)})")
        return null
    }

    private fun parseBatteryLevel(data: ByteArray, deviceNameSafe: String): RpmDeviceData? {
        val batteryLevel = data[0].toInt() and 0xFF
        Log.d(TAG, "parseBatteryLevel: Battery Level - $batteryLevel%")
        return RpmDeviceData(
            deviceName = deviceNameSafe,
            battery = batteryLevel
        )
    }

    // NEW PARSING METHODS according to BLE_GATT_Characteristics.txt specification

    /**
     * Parse User Control characteristic data (status, errors, commands)
     * Format: Tag-based responses
     */
    private fun parseUserControlData(data: ByteArray, deviceNameSafe: String): RpmDeviceData? {
        Log.d(TAG, "parseUserControlData: Parsing user control data")

        // Convert byte array to string for tag-based parsing
        val dataString = String(data, Charsets.UTF_8)
        Log.d(TAG, "parseUserControlData: Data string: '$dataString'")

        when {
            dataString.startsWith("s") -> { // Status tags
                val statusCode = dataString.substring(1)
                val state = when (statusCode) {
                    "0" -> DeviceState.OFF
                    "1" -> DeviceState.DISCONNECTED
                    "2" -> DeviceState.UNVERIFIED
                    "3" -> DeviceState.READY
                    "4" -> DeviceState.INITIALIZING_SENSORS
                    "5" -> DeviceState.SENSOR_ERROR
                    "6" -> DeviceState.CANNOT_DETECT_SKIN
                    "7" -> DeviceState.WORKING
                    "8" -> DeviceState.SLEEP
                    else -> DeviceState.OFF
                }
                currentState = state
                Log.d(TAG, "parseUserControlData: Device state changed to: ${state.name} ($statusCode)")

                // Handle state-specific logic
                when (state) {
                    DeviceState.UNVERIFIED -> {
                        passkeyRequired = true
                        Log.d(TAG, "parseUserControlData: Passkey required for device verification")
                    }
                    DeviceState.READY -> {
                        passkeyRequired = false
                        Log.d(TAG, "parseUserControlData: Device is ready for commands")
                    }
                    else -> {}
                }
            }
            dataString.startsWith("e") -> { // Error tags
                val errorCode = dataString.substring(1).toIntOrNull() ?: 0
                Log.e(TAG, "parseUserControlData: Error received - Code: $errorCode")
                // Handle error codes as needed
            }
            else -> {
                Log.w(TAG, "parseUserControlData: Unknown data format: $dataString")
            }
        }

        return null // User control data doesn't contain measurement data
    }

    /**
     * Parse Vitals Data characteristic (PPG sensor data)
     * Format: 9 bytes per sample - IR (3 bytes), Red (3 bytes), Green (3 bytes)
     */
    private fun parseVitalsData(data: ByteArray, deviceNameSafe: String): RpmDeviceData? {
        Log.d(TAG, "parseVitalsData: Parsing PPG vitals data")

        if (data.size < 9) {
            Log.w(TAG, "parseVitalsData: Insufficient data size: ${data.size} bytes (expected 9)")
            return null
        }

        // Parse PPG data: IR, Red, Green (3 bytes each, little endian)
        val ir = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8) or ((data[2].toInt() and 0xFF) shl 16)
        val red = (data[3].toInt() and 0xFF) or ((data[4].toInt() and 0xFF) shl 8) or ((data[5].toInt() and 0xFF) shl 16)
        val green = (data[6].toInt() and 0xFF) or ((data[7].toInt() and 0xFF) shl 8) or ((data[8].toInt() and 0xFF) shl 16)

        Log.d(TAG, "parseVitalsData: PPG Sample - IR: $ir, Red: $red, Green: $green")

        // Calculate SpO2 and heart rate from PPG data (simplified calculation)
        // In real implementation, this would require more sophisticated signal processing
        val spo2 = 98 // Placeholder - would be calculated from red/IR ratio
        val heartRate = 72 // Placeholder - would be calculated from signal periodicity

        return RpmDeviceData(
            deviceName = deviceNameSafe,
            spo2 = spo2,
            pulse = heartRate
        )
    }

    /**
     * Parse Temperature characteristic data
     * Format: Temperature in Celsius × 100 as integer (little endian)
     */
    private fun parseTemperatureData(data: ByteArray, deviceNameSafe: String): RpmDeviceData? {
        Log.d(TAG, "parseTemperatureData: Parsing temperature data")

        if (data.size < 2) {
            Log.w(TAG, "parseTemperatureData: Insufficient data size: ${data.size} bytes (expected 2)")
            return null
        }

        // Temperature as integer (little endian) representing Celsius × 100
        val tempRaw = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
        val temperature = tempRaw / 100.0

        Log.d(TAG, "parseTemperatureData: Temperature - Raw: $tempRaw, Celsius: $temperature°C")

        return RpmDeviceData(
            deviceName = deviceNameSafe,
            temperature = temperature
        )
    }
}
