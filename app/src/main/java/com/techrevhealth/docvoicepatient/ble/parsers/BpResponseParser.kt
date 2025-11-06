package com.techrevhealth.docvoicepatient.ble.parsers

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.techrevhealth.docvoicepatient.ble.commands.BpCommands
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

/**
 * Blood Pressure Monitor Response Parser
 * Handles parsing of responses from FORA P20 BP Monitor
 * 
 * Command flow: 0x52 → 0x23 → 0x24 → 0x27 → 0x28 → 0x25 → 0x26
 */
class BpResponseParser(
    private val deviceName: String,
    private val onDataComplete: (RpmDeviceData) -> Unit,
    private val writeCharacteristic: (ByteArray, BluetoothGatt, BluetoothGattCharacteristic) -> Unit
) {
    
    private var accumulatedData: RpmDeviceData? = null
    
    companion object {
        private const val TAG = "BpResponseParser"
    }
    
    fun parseResponse(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "parseResponse: CMD=0x${String.format("%02X", data[1])}")
        
        if (data.isEmpty()) return
        
        when (data[1].toInt() and 0xFF) {
            0x52 -> handle0x52ClearMemory(gatt, characteristic)
            0x23 -> handle0x23ClockTime(data, gatt, characteristic)
            0x24 -> handle0x24DeviceModel(data, gatt, characteristic)
            0x27 -> handle0x27SerialPart1(data, gatt, characteristic)
            0x28 -> handle0x28SerialPart2(data, gatt, characteristic)
            0x25 -> handle0x25MeasurementTime(data, gatt, characteristic)
            0x26 -> handle0x26BpValue(data)
        }
    }
    
    private fun handle0x52ClearMemory(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x52: Clear Memory Response")
        Log.d(TAG, "Memory cleared - initializing BP data collection")
        
        // Initialize accumulated data
        accumulatedData = RpmDeviceData(deviceName = deviceName)
        
        // Start command sequence with clock time
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(BpCommands.readClockTime(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x23ClockTime(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x23: Clock Time")
        Log.d(TAG, "Raw bytes: [0]=${data[0]} [1]=${data[1]} [2]=${data[2]} [3]=${data[3]}")
        
        try {
            // Parse date/time from device clock (Table A + Table B format)
            val data0 = data[0].toInt() and 0xFF
            val data1 = data[1].toInt() and 0xFF
            val minute = data[2].toInt() and 0x3F  // 6-bit
            val hour = data[3].toInt() and 0x1F    // 5-bit
            
            // Table A: Day+Month+Year encoding
            val day = data0 and 0x1F
            val month = ((data0 shr 5) and 0x07) or ((data1 and 0x01) shl 3)
            val year = ((data1 shr 1) and 0x7F) + 2000
            
            val clockTime = String.format("%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
            Log.d(TAG, "Device Clock Time: $clockTime")
            
            accumulatedData = accumulatedData?.copy(measureTime = clockTime)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse clock time: ${e.message}")
        }
        
        // Send device model command
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(BpCommands.readDeviceModel(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x24DeviceModel(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x24: Device Model")
        Log.d(TAG, "Raw bytes: [0]=${data[0]} [1]=${data[1]}")
        
        // Model is 2-byte Word (Data_1 << 8 | Data_0)
        val modelCode = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        
        accumulatedData = accumulatedData?.copy(deviceModel = modelCode.toString())
        Log.d(TAG, "Device Model: $modelCode")
        
        // Send serial part 1 (SN_0~3)
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(BpCommands.readSerialPart1(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x27SerialPart1(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x27: Serial Part 1 (SN_0~3)")
        
        val serial1 = String.format("%02X%02X%02X%02X", data[0], data[1], data[2], data[3])
        
        // Store in firmware field temporarily
        accumulatedData = accumulatedData?.copy(firmware = serial1)
        Log.d(TAG, "Serial Part 1: $serial1")
        
        // Send serial part 2 (SN_4~7)
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(BpCommands.readSerialPart2(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x28SerialPart2(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x28: Serial Part 2 (SN_4~7)")
        
        val serial2 = String.format("%02X%02X%02X%02X", data[0], data[1], data[2], data[3])
        val serial1 = accumulatedData?.firmware ?: ""
        
        // Complete serial = Part2 (SN_4~7) + Part1 (SN_0~3)
        val completeSerial = serial2 + serial1
        
        accumulatedData = accumulatedData?.copy(
            serialNumber = completeSerial,
            firmware = null  // Clear temporary storage
        )
        Log.d(TAG, "Complete Serial: $completeSerial")
        
        // Send 0x25 to get stored measurement time
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(BpCommands.readMeasurementTime(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x25MeasurementTime(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x25: Measurement Time")
        Log.d(TAG, "Raw bytes: [0]=${data[0]} [1]=${data[1]} [2]=${data[2]} [3]=${data[3]}")
        
        try {
            // Parse stored measurement time (when BP reading was taken)
            val data0 = data[0].toInt() and 0xFF
            val data1 = data[1].toInt() and 0xFF
            val minute = data[2].toInt() and 0x3F  // 6-bit
            val hour = data[3].toInt() and 0x1F    // 5-bit
            
            // Parse date from M_Date
            val day = data0 and 0x1F
            val month = ((data0 shr 5) and 0x07) or ((data1 and 0x01) shl 3)
            val year = ((data1 shr 1) and 0x7F) + 2000
            
            val measurementTime = String.format("%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
            Log.d(TAG, "Stored Measurement Time: $measurementTime")
            
            // Update with actual measurement time (overwrites device clock time)
            accumulatedData = accumulatedData?.copy(measureTime = measurementTime)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse measurement time: ${e.message}")
        }
        
        // Send 0x26 to get BP values
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(BpCommands.readBpValue(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x26BpValue(data: ByteArray) {
        Log.d(TAG, "0x26: BP Value")
        val hexString = data.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Raw bytes: $hexString")
        
        try {
            // Per old implementation: Systolic at data[2], Diastolic at data[4], Pulse at data[5]
            val systolic = data[2].toInt() and 0xFF
            val diastolic = data[4].toInt() and 0xFF
            val pulse = data[5].toInt() and 0xFF
            
            Log.d(TAG, "Systolic: $systolic mmHg")
            Log.d(TAG, "Diastolic: $diastolic mmHg")
            Log.d(TAG, "Pulse: $pulse bpm")
            
            // Accumulate BP values
            accumulatedData = accumulatedData?.copy(
                systolic = systolic.toDouble(),
                diastolic = diastolic.toDouble(),
                pulse = pulse
            )
            
            Log.d(TAG, "=== FINAL BP DATA ===")
            Log.d(TAG, "Measure Time: ${accumulatedData?.measureTime}")
            Log.d(TAG, "Model: ${accumulatedData?.deviceModel}")
            Log.d(TAG, "Serial: ${accumulatedData?.serialNumber}")
            Log.d(TAG, "Systolic: ${accumulatedData?.systolic} mmHg")
            Log.d(TAG, "Diastolic: ${accumulatedData?.diastolic} mmHg")
            Log.d(TAG, "Pulse: ${accumulatedData?.pulse} bpm")
            Log.d(TAG, "=====================")
            
            accumulatedData?.let { onDataComplete(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse BP data: ${e.message}")
        }
    }
}
