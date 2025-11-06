package com.techrevhealth.docvoicepatient.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData
import com.techrevhealth.docvoicepatient.helpers.RpmDeviceType
import java.io.ByteArrayOutputStream

class ForaCareHandler : DeviceHandler {

    private val TAG = "ForaCareHandler"

    // Weight data handling
    private var weightDataBuffer = ByteArrayOutputStream()
    private var isReceivingWeightData = false
    private var commandRetryCount = 0
    private val maxRetryCount = 3
    private val expectedWeightDataLength = 32

    override fun getSupportedDeviceNames(): List<String> {
        return listOf(
            "FORA P20",
            "TNG SPO2",
            "TNG SCALE",
            "FORA PREMIUM V10"
        )
    }

    override fun shouldConnect(deviceName: String): Boolean {
        return getSupportedDeviceNames().any { deviceName.contains(it, ignoreCase = true) }
    }

    override fun buildReadCommand(deviceName: String): ByteArray {
        return when {
            deviceName.equals(RpmDeviceType.TNG_SPO2.displayName, true) -> buildReadCommand()
            deviceName.equals(RpmDeviceType.FORA_PREMIUM_V10.displayName, true) -> buildReadGlucoseResultCommand()
            deviceName.equals(RpmDeviceType.FORA_P20.displayName, true) -> buildReadBpResultCommand()
            deviceName.equals(RpmDeviceType.TNG_SCALE.displayName, true) -> buildReadWeightMachineCommand()
            else -> buildReadWeightMachineCommand()
        }
    }

    override fun buildStopCommand(deviceName: String): ByteArray {
        return when {
            deviceName.equals(RpmDeviceType.TNG_SPO2.displayName, true) -> stopDeviceCommand()
            deviceName.equals(RpmDeviceType.FORA_PREMIUM_V10.displayName, true) -> buildGlucoseStopDeviceCommand()
            deviceName.equals(RpmDeviceType.FORA_P20.displayName, true) -> buildStopBpCommand()
            deviceName.equals(RpmDeviceType.TNG_SCALE.displayName, true) -> buildStopWeightMachineCommand()
            else -> stopDeviceCommand()
        }
    }

    override fun parseData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        return when (deviceName.uppercase()) {
            "TNG SPO2", "FORA_SPO2" -> parseForaSpo2Data(data, gatt, characteristic, deviceName, context)
            "FORA P20" -> parseBpMonitorData(data, gatt, characteristic, deviceName, context)
            "FORA PREMIUM V10" -> parseGlucoseData(data, gatt, characteristic, deviceName, context)
            "TNG SCALE" -> parseWeightData(data, gatt, characteristic, deviceName, context)
            else -> null
        }
    }

    // Command builders
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
            0x51.toByte(), 0x71.toByte(), 0x02.toByte(), 0x00.toByte(),
            0x00.toByte(), 0xA3.toByte(), 0x00.toByte()
        )
        var sum = 0
        for (i in 0 until 6) {
            sum += command[i].toInt() and 0xFF
        }
        command[6] = (sum and 0xFF).toByte()
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

    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (i in 0 until 7) {
            sum += data[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }

    // Data parsers
    private fun parseWeightData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        Log.d(TAG, "═══════════════════════════════════════════════════════")
        Log.d(TAG, "📦 RECEIVED WEIGHT DATA FROM DEVICE")
        Log.d(TAG, "═══════════════════════════════════════════════════════")
        // Log full byte array safely
        Log.d(TAG, "Raw Hex: ${data.joinToString(" ") { String.format("%02X", it) }}")
        Log.d(TAG, "Data Size: ${data.size} bytes")
        Log.d(TAG, "Byte-by-Byte Breakdown:")
        for ((index, byte) in data.withIndex()) {
            Log.d(TAG, "  Byte[$index]: 0x${String.format("%02X", byte)} (decimal: ${byte.toInt() and 0xFF})")
        }
        Log.d(TAG, "───────────────────────────────────────────────────────")

        if (data.isNotEmpty()) {
            if (data.size < 2) {
                Log.w(TAG, "⚠️ Invalid weight data size: ${data.size} bytes (minimum 2 bytes required)")
                return null
            }

            // Check for 2-byte responses (could be end marker or error)
            if (data.size == 2) {
                val status1 = data[0].toInt() and 0xFF
                val status2 = data[1].toInt() and 0xFF
                Log.w(TAG, "")
                Log.w(TAG, "⚠️ 2-BYTE RESPONSE DETECTED")
                Log.w(TAG, "Status Byte 1: 0x${String.format("%02X", status1)} (decimal: $status1)")
                Log.w(TAG, "Status Byte 2: 0x${String.format("%02X", status2)} (decimal: $status2)")
                Log.w(TAG, "Current Retry Count: $commandRetryCount/$maxRetryCount")
                Log.w(TAG, "Buffer Size: ${weightDataBuffer.size()} bytes")
                Log.w(TAG, "")

                // Check if this is an end marker after receiving data
                if (weightDataBuffer.size() >= 32 && isReceivingWeightData && (status1 == 0xA5 || status1 == 0xA3)) {
                    Log.d(TAG, "✅ END MARKER DETECTED: 0x${String.format("%02X", status1)} 0x${String.format("%02X", status2)}")
                    Log.d(TAG, "Total data collected: ${weightDataBuffer.size()} bytes")
                    Log.d(TAG, "End marker bytes: 0x${String.format("%02X", status1)} 0x${String.format("%02X", status2)}")
                    Log.d(TAG, "Processing complete weight data...")

                    val completeData = weightDataBuffer.toByteArray()
                    return parseCompleteWeightData(completeData, gatt, characteristic, deviceName, context)

                }

                when {
                    status1 == 0x01 && status2 == 0x00 -> {
                        Log.w(TAG, "🔴 Response: 0x01 0x00 (No data or communication issue)")
                        weightDataBuffer.reset()
                        isReceivingWeightData = false

                        if (commandRetryCount < maxRetryCount) {
                            commandRetryCount++
                            Log.w(TAG, "🔁 RETRY LOGIC TRIGGERED")
                            Log.w(TAG, "Retry attempt: $commandRetryCount/$maxRetryCount")
                            Log.w(TAG, "Action: Resending 0x71 command after 1 second delay")

                            Handler(Looper.getMainLooper()).postDelayed({
                                Log.d(TAG, "Executing retry #$commandRetryCount...")
                                // Retry logic would be handled by caller
                            }, 1000)
                        } else {
                            Log.e(TAG, "")
                            Log.e(TAG, "❌ MAX RETRIES REACHED ($maxRetryCount attempts)")
                            Log.e(TAG, "Final conclusion: Device has no data available or empty memory")
                            Log.e(TAG, "User action required: Please step on the scale to take a measurement first!")
                            Log.e(TAG, "")
                        }
                    }
                    else -> {
                        Log.e(TAG, "❓ UNKNOWN 2-BYTE RESPONSE: 0x${String.format("%02X", status1)} 0x${String.format("%02X", status2)}")
                        Log.e(TAG, "This response is not documented in protocol specification")

                        weightDataBuffer.reset()
                        isReceivingWeightData = false

                        if (commandRetryCount < maxRetryCount) {
                            commandRetryCount++
                            Log.w(TAG, "🔁 Attempting retry for unknown response")
                            Log.w(TAG, "Retry attempt: $commandRetryCount/$maxRetryCount")

                            Handler(Looper.getMainLooper()).postDelayed({
                                Log.d(TAG, "Executing retry #$commandRetryCount for unknown response...")
                                // Retry logic would be handled by caller
                            }, 1000)
                        }
                    }
                }
                Log.d(TAG, "═══════════════════════════════════════════════════════")
                return null
            }

            // Handle multi-byte responses
            when (data[1].toInt() and 0xFF) {
                0x52 -> {
                    Log.d(TAG, "parseWeightData: Clear Memory Response")
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Request new read command - handled by caller
                    }, 700)
                }
                0x54 -> {
                    Log.d(TAG, "parseWeightData: ✅ Received optional 0x54 notification from device")
                    Log.d(TAG, "Device entered communication mode (optional notification - jar doesn't wait for this)")
                }
                0x2B -> {
                    Log.d(TAG, "parseWeightData: Storage Count Response (0x2B) - Optional diagnostic")
                    if (data.size >= 8) {
                        val countLow = data[2].toInt() and 0xFF
                        val countHigh = data[3].toInt() and 0xFF
                        val count = (countHigh shl 8) or countLow
                        Log.d(TAG, "Storage count: $count readings available (diagnostic only)")
                    }
                }
                0x71 -> {
                    Log.d(TAG, "")
                    Log.d(TAG, "✅ WEIGHT DATA RESPONSE (0x71) DETECTED")
                    Log.d(TAG, "")

                    if (data[0].toInt() and 0xFF == 0x51 && data[1].toInt() and 0xFF == 0x71) {
                        Log.d(TAG, "📥 START OF WEIGHT DATA PACKET (0x51 0x71)")
                        Log.d(TAG, "Resetting buffer for new data reception")
                        weightDataBuffer.reset()
                        isReceivingWeightData = true
                    }

                    Log.d(TAG, "📌 Adding ${data.size} bytes to buffer")
                    weightDataBuffer.write(data)
                    Log.d(TAG, "Current buffer size: ${weightDataBuffer.size()} / $expectedWeightDataLength bytes")

                    if (weightDataBuffer.size() >= expectedWeightDataLength) {
                        Log.d(TAG, "")
                        Log.d(TAG, "✅ COMPLETE WEIGHT DATA RECEIVED (${weightDataBuffer.size()} bytes)")
                        Log.d(TAG, "")

                        val completeData = weightDataBuffer.toByteArray()
                        return parseCompleteWeightData(completeData, gatt, characteristic, deviceName, context)
                    } else {
                        Log.d(TAG, "⏳ Waiting for more data packets...")
                        Log.d(TAG, "Still need: ${expectedWeightDataLength - weightDataBuffer.size()} bytes")
                    }
                }
                else -> {
                    if (isReceivingWeightData) {
                        Log.d(TAG, "📌 CONTINUATION PACKET (during weight data reception)")
                        Log.d(TAG, "Adding ${data.size} bytes to buffer")
                        weightDataBuffer.write(data)
                        Log.d(TAG, "Current buffer size: ${weightDataBuffer.size()} / $expectedWeightDataLength bytes")

                        if (weightDataBuffer.size() >= expectedWeightDataLength) {
                            Log.d(TAG, "")
                            Log.d(TAG, "✅ COMPLETE WEIGHT DATA RECEIVED (${weightDataBuffer.size()} bytes)")
                            Log.d(TAG, "")

                            val completeData = weightDataBuffer.toByteArray()
                            return parseCompleteWeightData(completeData, gatt, characteristic, deviceName, context)
                        } else {
                            Log.d(TAG, "⏳ Waiting for more data packets...")
                            Log.d(TAG, "Still need: ${expectedWeightDataLength - weightDataBuffer.size()} bytes")
                        }
                    } else {
                        Log.w(TAG, "⚠️ Unexpected command byte: 0x${String.format("%02X", data[1].toInt() and 0xFF)}")
                    }
                }
            }
        }

        return null
    }

    private fun parseCompleteWeightData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        Log.d(TAG, "═══════════════════════════════════════════════════════")
        Log.d(TAG, "🔍 PARSING COMPLETE WEIGHT DATA")
        Log.d(TAG, "═══════════════════════════════════════════════════════")
        Log.d(TAG, "Total bytes: ${data.size}")
        Log.d(TAG, "Complete Hex: ${data.joinToString(" ") { String.format("%02X", it) }}")

        if (data.size < 32) {
            Log.e(TAG, "❌ ERROR: Data too short (${data.size} bytes, expected at least 32)")
            Log.e(TAG, "Cannot parse incomplete data")
            return null
        }

        // Reset retry counter on successful response
        val previousRetryCount = commandRetryCount
        commandRetryCount = 0

        if (previousRetryCount > 0) {
            Log.d(TAG, "✅ Success after $previousRetryCount retry attempts!")
        }
        Log.d(TAG, "Retry counter reset to 0")

        // Verify frame markers
        if (data[0].toInt() and 0xFF != 0x51) {
            Log.w(TAG, "⚠️ Warning: Start byte is 0x${String.format("%02X", data[0].toInt() and 0xFF)} (expected 0x51)")
        }
        if (data[1].toInt() and 0xFF != 0x71) {
            Log.w(TAG, "⚠️ Warning: Command byte is 0x${String.format("%02X", data[1].toInt() and 0xFF)} (expected 0x71)")
        }

        // Verify data length byte
        val dataLength = data[2].toInt() and 0xFF
        Log.d(TAG, "Data length field: 0x${String.format("%02X", dataLength)} (expected: 0x1D = 29)")
        if (dataLength != 0x1D) {
            Log.w(TAG, "⚠️ Unexpected data length: 0x${String.format("%02X", dataLength)}")
        }

        val stableTime = data[3].toInt() and 0xFF
        val year = data[4].toInt() and 0xFF
        val month = data[5].toInt() and 0xFF
        val day = data[6].toInt() and 0xFF
        val hour = data[7].toInt() and 0xFF
        val minute = data[8].toInt() and 0xFF
        val code = data[9].toInt() and 0xFF
        val gender = data[10].toInt() and 0xFF
        val heightCm = data[11].toInt() and 0xFF
        val heightIn = ((data[12].toInt() and 0xFF) shl 8) or (data[13].toInt() and 0xFF)
        val age = data[14].toInt() and 0xFF
        val calUnit = data[15].toInt() and 0xFF
        val weightRaw = ((data[16].toInt() and 0xFF) shl 8) or (data[17].toInt() and 0xFF)
        val weightKg = weightRaw / 10.0
        val weightLb = ((data[18].toInt() and 0xFF) shl 8) or (data[19].toInt() and 0xFF)
        val bmiRaw = ((data[20].toInt() and 0xFF) shl 8) or (data[21].toInt() and 0xFF)
        val bmiTemp = if (bmiRaw == 0) {
            weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
        } else {
            bmiRaw / 10.0
        }
        val heightInMeters = heightCm / 100.0
        val bmi = if (bmiRaw == 0) weightKg / (heightInMeters * heightInMeters) else bmiRaw / 10.0
        val bmr = ((data[22].toInt() and 0xFF) shl 8) or (data[23].toInt() and 0xFF)
        val bf = ((data[24].toInt() and 0xFF) shl 8) or (data[25].toInt() and 0xFF)
        val bm = ((data[26].toInt() and 0xFF) shl 8) or (data[27].toInt() and 0xFF)
        val bn = data[28].toInt() and 0xFF
        val bw = ((data[29].toInt() and 0xFF) shl 8) or (data[30].toInt() and 0xFF)
        val status = data[31].toInt() and 0xFF

        Log.d(TAG, "")
        Log.d(TAG, "📊 PARSED WEIGHT DATA:")
        Log.d(TAG, "  Date: $year-$month-$day $hour:$minute")
        Log.d(TAG, "  Stable Time: $stableTime")
        Log.d(TAG, "  Code: $code")
        Log.d(TAG, "  Gender: $gender (0=Male, 1=Female)")
        Log.d(TAG, "  Height: $heightCm cm")
        Log.d(TAG, "  Age: $age years")
        Log.d(TAG, "  Cal Unit: $calUnit")
        Log.d(TAG, "  ⭐ Weight (Raw): $weightRaw -> ${weightKg} kg")
        Log.d(TAG, "  ⭐ Weight (Lb): ${weightLb / 10.0} lb")
        Log.d(TAG, "  ⭐ BMI (Raw): $bmiRaw -> $bmi")
        Log.d(TAG, "  BMR: $bmr")
        Log.d(TAG, "  Body Fat: $bf")
        Log.d(TAG, "  Body Mass: $bm")
        Log.d(TAG, "  Status: 0x${String.format("%02X", status)}")
        Log.d(TAG, "")

        val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            gatt.device.name ?: "Unknown"
        } else {
            "Unknown"
        }

        val deviceData = RpmDeviceData(
            deviceName = deviceNameSafe,
            weight = weightKg,
            bmi = bmi,
            bodyFat = bf.toDouble()
        )

        Log.d(TAG, "✅ Weight data successfully parsed and stored")
        Log.d(TAG, "Sending stop command to device...")
        // Stop command would be handled by caller

        Log.d(TAG, "═══════════════════════════════════════════════════════")
        return deviceData
    }

    private fun parseGlucoseData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        Log.d(TAG, "parseGlucoseData: 0: " + data[0].toInt() + " 1: " + data[1].toInt() + " " +
                "2: " + " " + data[2].toInt() + " 5: " + data[5].toInt() + " 6: " + data[6].toInt() + " 3: " +
                "" + data[3].toInt() + " 4: " + data[4].toInt())

        if (data.isNotEmpty()) {
            when (data[1].toInt() and 0xFF) {
                0x52 -> {
                    Log.d(TAG, "parseGlucoseData: Clear Memory Response")
                    // Wait and send read command
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Request new read command - handled by caller
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
                        // Request new read command - handled by caller
                    }, 700) // delay must be >= 600ms to be safe

                }
                0x26 -> {
                    Log.d(TAG, "parseGlucoseData: Data: ")
                    val spo2 = data[2].toInt() and 0xFF
                    val pulse = data[5].toInt() and 0xFF
                    Log.d("FORA PREMIUM V10", "Glucose: $spo2 mg/dl, Values 5: $pulse ")
                    val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.device.name ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    return RpmDeviceData(
                        deviceName = deviceNameSafe,
                        glucose = spo2.toDouble(),
                        pulse = pulse
                    )
                }
            }
        }

        return null
    }

    private fun parseBpMonitorData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        Log.d(TAG, "onCharacteristicChanged: 0: " + data[0].toInt() + " 1: " + data[1].toInt() + " " +
                "2: " + " " + data[2].toInt() + " 5: " + data[5].toInt() + " 6: " + data[6].toInt())

        if (data.isNotEmpty()) {
            when (data[1].toInt() and 0xFF) {
                0x52 -> {
                    Log.d(TAG, "parseBpMonitorData: Clear Memory Response")
                    // Wait and send read command
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Request new read command - handled by caller
                    }, 700) // delay must be >= 600ms to be safe
                }
                0x25 -> {
                    Log.d(TAG, "parseGlucoseData: Data: ")
                    val sys = data[2].toInt() and 0xFF       // Systolic
                    val dia = data[4].toInt() and 0xFF       // Diastolic
                    val pulse = data[5].toInt() and 0xFF     // Pulse

                    Log.d("FORA_BP", "SYS: $sys mmHg, DIA: $dia mmHg, Pulse: $pulse bpm")
                    val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.device.name ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    return RpmDeviceData(
                        deviceName = deviceNameSafe,
                        systolic = sys.toDouble(),
                        diastolic = dia.toDouble(),
                        pulse = pulse
                    )

                }
                0x26 -> {
                    Log.d(TAG, "parseGlucoseData: Data: ")
                    val sys = data[2].toInt() and 0xFF       // Systolic
                    val dia = data[4].toInt() and 0xFF       // Diastolic
                    val pulse = data[5].toInt() and 0xFF     // Pulse

                    Log.d("FORA_BP", "SYS: $sys mmHg, DIA: $dia mmHg, Pulse: $pulse bpm")
                    val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.device.name ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    return RpmDeviceData(
                        deviceName = deviceNameSafe,
                        systolic = sys.toDouble(),
                        diastolic = dia.toDouble(),
                        pulse = pulse
                    )
                }
            }
        }

        return null
    }

    private fun parseForaSpo2Data(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        Log.d(TAG, "parseForaSpo2Data: 0: " + data[0].toInt() + " 1: " + data[1].toInt() + " " +
                "2: " + " " + data[2].toInt() + " 5: " + data[5].toInt() + " 6: " + data[6].toInt() + " 3: " +
                "" + data[3].toInt() + " 4: " + data[4].toInt())

        if (data.isNotEmpty()) {
            when (data[1].toInt() and 0xFF) {
                0x52 -> {
                    Log.d(TAG, "parseForaSpo2Data: Clear Memory Response")
                    // Wait and send read command
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Request new read command - handled by caller
                    }, 2000) // delay must be >= 600ms to be safe
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
                    val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.device.name ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    return RpmDeviceData(
                        deviceName = deviceNameSafe,
                        serialNumber = serial
                    )
                }
                0x49 -> {
                    Log.d(TAG, "parseForaSpo2Data: Data: ")
                    val spo2 = data[2].toInt() and 0xFF
                    val pulse = data[5].toInt() and 0xFF
                    Log.d("FORA_SPO2", "SpO2: $spo2%, Heart Rate: $pulse bpm")
                    val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.device.name ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    return RpmDeviceData(
                        deviceName = deviceNameSafe,
                        spo2 = spo2,
                        pulse = pulse
                    )
                }
                0x4F -> {
                    Log.d(TAG, "parseForaSpo2Data: battery")
                    val battery = data[2].toInt() and 0xFF
                    val firmware = data[4].toInt() and 0xFF
                    val deviceNameSafe = if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        gatt.device.name ?: "Unknown"
                    } else {
                        "Unknown"
                    }

                    return RpmDeviceData(
                        deviceName = deviceNameSafe,
                        battery = battery,
                        firmware = firmware.toString()
                    )
                }


            }
        }

        return null
    }
}
