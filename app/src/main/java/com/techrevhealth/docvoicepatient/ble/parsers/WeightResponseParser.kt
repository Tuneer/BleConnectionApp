package com.techrevhealth.docvoicepatient.ble.parsers

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.techrevhealth.docvoicepatient.ble.commands.WeightCommands
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

/**
 * Weight Scale Response Parser
 * Handles parsing of responses from TNG Scale / FORA W300 series
 * 
 * Command flow: 0x23 → 0x24 → 0x27 → 0x28 → 0x71
 */
class WeightResponseParser(
    private val deviceName: String,
    private val onDataComplete: (RpmDeviceData) -> Unit,
    private val writeCharacteristic: (ByteArray, BluetoothGatt, BluetoothGattCharacteristic) -> Unit
) {
    
    private var accumulatedData: RpmDeviceData? = null
    
    companion object {
        private const val TAG = "WeightResponseParser"
    }
    
    fun parseResponse(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "parseResponse: CMD=0x${String.format("%02X", data[1])}")
        
        if (data.isEmpty()) return
        
        when (data[1].toInt() and 0xFF) {
            0x23 -> handle0x23ClockTime(data, gatt, characteristic)
            0x24 -> handle0x24DeviceModel(data, gatt, characteristic)
            0x27 -> handle0x27SerialPart1(data, gatt, characteristic)
            0x28 -> handle0x28SerialPart2(data, gatt, characteristic)
            0x71 -> handle0x71WeightData(data)
        }
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
            
            // Initialize accumulated data
            accumulatedData = RpmDeviceData(deviceName = deviceName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse clock time: ${e.message}")
            accumulatedData = RpmDeviceData(deviceName = deviceName)
        }
        
        // Send device model command
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(WeightCommands.readDeviceModel(), gatt, characteristic)
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
            writeCharacteristic(WeightCommands.readSerialPart1(), gatt, characteristic)
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
            writeCharacteristic(WeightCommands.readSerialPart2(), gatt, characteristic)
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
        
        // Send 0x71 to get weight data
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(WeightCommands.readWeightData(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x71WeightData(data: ByteArray) {
        Log.d(TAG, "0x71: Weight Data (29 bytes)")
        val hexString = data.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Raw bytes: $hexString")
        
        try {
            // Per PDF page 9-10: 0x71 response contains 29 bytes (0x1D)
            // Byte structure (from response, 0-indexed):
            // 0-1: Header (0x51, 0x71)
            // 2: Length (0x1D = 29)
            // 3: Stable time (for Test N'GO Scale 550)
            // 4: Year
            // 5: Month
            // 6: Day
            // 7: Hour
            // 8: Minute
            // 9: Code/User ID
            // 10: Gender (0=Female, 1=Male)
            // 11: Height(cm)
            // 12-13: Height(inch) x10 (Word, byte 12 is MSB)
            // 14: Age
            // 15: Cal unit (0=kg, 1=lb, 2=st)
            // 16-17: Weight(kg) x10 (Word, byte 16 is MSB)
            // 18-19: Weight(lb) x10 (Word, byte 18 is MSB)
            // 20-21: BMI x10 (Word, byte 20 is MSB)
            // 22-23: BMR (Kcal/day, Word, byte 22 is MSB)
            // 24-25: Body Fat % x10 (Word, byte 24 is MSB)
            // 26-27: Body Muscle % x10 (Word, byte 26 is MSB)
            // 28: Body Bone % x10
            // 29-30: Body Water % x10 (Word, byte 29 is MSB)
            // 31: Status (1=Severely Underweight, 2=Underweight, 3=Normal, 4=Overweight, 5=Obese)
            
            if (data.size < 33) {
                Log.w(TAG, "Insufficient data received: ${data.size} bytes")
                return
            }
            
            // Parse measurement time
            val year = (data[4].toInt() and 0xFF) + 2000
            val month = data[5].toInt() and 0xFF
            val day = data[6].toInt() and 0xFF
            val hour = data[7].toInt() and 0xFF
            val minute = data[8].toInt() and 0xFF
            
            val measureTime = String.format("%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
            
            // Parse weight (kg) - bytes 16-17 (MSB first)
            val weightRaw = ((data[17].toInt() and 0xFF) shl 8) or (data[18].toInt() and 0xFF)
            val weight = weightRaw / 10.0
            
            // Parse BMI - bytes 20-21 (MSB first)
            val bmiRaw = ((data[21].toInt() and 0xFF) shl 8) or (data[22].toInt() and 0xFF)
            val bmi = bmiRaw / 10.0
            
            Log.d(TAG, "Measurement Time: $measureTime")
            Log.d(TAG, "Weight: $weight kg")
            Log.d(TAG, "BMI: $bmi")
            
            // Accumulate weight and BMI values
            accumulatedData = accumulatedData?.copy(
                measureTime = measureTime,
                weight = weight,
                bmi = bmi
            )
            
            Log.d(TAG, "=== FINAL WEIGHT DATA ===")
            Log.d(TAG, "Measure Time: ${accumulatedData?.measureTime}")
            Log.d(TAG, "Model: ${accumulatedData?.deviceModel}")
            Log.d(TAG, "Serial: ${accumulatedData?.serialNumber}")
            Log.d(TAG, "Weight: ${accumulatedData?.weight} kg")
            Log.d(TAG, "BMI: ${accumulatedData?.bmi}")
            Log.d(TAG, "=========================")
            
            accumulatedData?.let { onDataComplete(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse weight data: ${e.message}")
            e.printStackTrace()
        }
    }
}
