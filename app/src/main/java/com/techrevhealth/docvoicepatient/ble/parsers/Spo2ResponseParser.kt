package com.techrevhealth.docvoicepatient.ble.parsers

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.techrevhealth.docvoicepatient.ble.commands.Spo2Commands
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

/**
 * SPO2 Device Response Parser
 * Handles parsing of responses from TNG SPO2 device
 */
class Spo2ResponseParser(
    private val deviceName: String,
    private val onDataComplete: (RpmDeviceData) -> Unit,
    private val writeCharacteristic: (ByteArray, BluetoothGatt, BluetoothGattCharacteristic) -> Unit
) {
    
    private var accumulatedData: RpmDeviceData? = null
    private var retryCount = 0
    private val maxRetries = 5
    
    companion object {
        private const val TAG = "Spo2ResponseParser"
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
            0x4F -> handle0x4FBattery(data, gatt, characteristic)
            0x49 -> handle0x49Spo2Data(data, gatt, characteristic)
        }
    }
    
    private fun handle0x52ClearMemory(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x52: Memory cleared")
        retryCount = 0
        accumulatedData = RpmDeviceData(deviceName = deviceName)
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(Spo2Commands.readClockTime(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x23ClockTime(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x23: Clock Time")
        try {
            val data0 = data[0].toInt() and 0xFF
            val data1 = data[1].toInt() and 0xFF
            val minute = data[2].toInt() and 0x3F
            val hour = data[3].toInt() and 0x1F
            
            val day = data0 and 0x1F
            val month = ((data0 shr 5) and 0x07) or ((data1 and 0x01) shl 3)
            val year = ((data1 shr 1) and 0x7F) + 2000
            
            val timestamp = String.format("%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
            Log.d(TAG, "Measurement Time: $timestamp")
            
            accumulatedData = RpmDeviceData(deviceName = deviceName, measureTime = timestamp)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse clock time: ${e.message}")
            accumulatedData = RpmDeviceData(deviceName = deviceName)
        }
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(Spo2Commands.readDeviceModel(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x24DeviceModel(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x24: Device Model")
        val modelCode = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        
        accumulatedData = accumulatedData?.copy(deviceModel = modelCode.toString())
            ?: RpmDeviceData(deviceName = deviceName, deviceModel = modelCode.toString())
        
        Log.d(TAG, "Model: $modelCode")
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(Spo2Commands.readSerialPart1(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x27SerialPart1(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x27: Serial Part 1 (SN_0~3)")
        val serial1 = String.format("%02X%02X%02X%02X", data[0], data[1], data[2], data[3])
        
        accumulatedData = accumulatedData?.copy(firmware = serial1)
            ?: RpmDeviceData(deviceName = deviceName, firmware = serial1)
        
        Log.d(TAG, "Serial Part 1: $serial1")
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(Spo2Commands.readSerialPart2(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x28SerialPart2(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x28: Serial Part 2 (SN_4~7)")
        val serial2 = String.format("%02X%02X%02X%02X", data[0], data[1], data[2], data[3])
        val serial1 = accumulatedData?.firmware ?: ""
        val completeSerial = serial2 + serial1
        
        accumulatedData = accumulatedData?.copy(serialNumber = completeSerial, firmware = null)
            ?: RpmDeviceData(deviceName = deviceName, serialNumber = completeSerial)
        
        Log.d(TAG, "Complete Serial: $completeSerial")
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(Spo2Commands.readBattery(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x4FBattery(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x4F: Battery (EXPERIMENTAL)")
        try {
            val battery = data[2].toInt() and 0xFF
            val firmware = data[4].toInt() and 0xFF
            
            accumulatedData = accumulatedData?.copy(battery = battery, firmware = firmware.toString())
                ?: accumulatedData
            
            Log.d(TAG, "Battery: $battery%, Firmware: $firmware")
        } catch (e: Exception) {
            Log.w(TAG, "0x4F failed: ${e.message}")
        }
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(Spo2Commands.readSpo2Data(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x49Spo2Data(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x49: SpO2/Pulse Data")
        
        // Log all bytes for debugging
        val hexString = data.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Raw bytes: $hexString")
        
        // Try standard positions
        var spo2 = if (data.size > 2) data[2].toInt() and 0xFF else 0
        var pulse = if (data.size > 5) data[5].toInt() and 0xFF else 0
        
        // Auto-detect if values are 0 or unrealistic
        if (spo2 == 0 || spo2 > 100) {
            for (i in data.indices) {
                val val_i = data[i].toInt() and 0xFF
                if (val_i in 70..100) {
                    Log.d(TAG, "*** FOUND SpO2 at byte[$i] = $val_i ***")
                    if (spo2 == 0 || spo2 > 100) spo2 = val_i
                }
            }
        }
        
        if (pulse == 0 || pulse > 200) {
            for (i in data.indices) {
                val val_i = data[i].toInt() and 0xFF
                if (val_i in 40..200 && val_i != spo2) {
                    Log.d(TAG, "*** FOUND Pulse at byte[$i] = $val_i ***")
                    if (pulse == 0 || pulse > 200) pulse = val_i
                }
            }
        }
        
        Log.d(TAG, "FINAL VALUES - SpO2: $spo2%, Pulse: $pulse bpm")
        
        // Check if need to retry
        if ((spo2 == 0 || pulse == 0) && retryCount < maxRetries) {
            retryCount++
            Log.w(TAG, "⚠️ No valid reading yet. Retry $retryCount/$maxRetries")
            
            Handler(Looper.getMainLooper()).postDelayed({
                writeCharacteristic(Spo2Commands.readSpo2Data(), gatt, characteristic)
            }, 2000)
            return
        }
        
        retryCount = 0
        
        // Accumulate final data
        accumulatedData = accumulatedData?.copy(spo2 = spo2, pulse = pulse)
            ?: RpmDeviceData(deviceName = deviceName, spo2 = spo2, pulse = pulse)
        
        // Log final data
        Log.d(TAG, "=== FINAL ACCUMULATED DATA ===")
        Log.d(TAG, "Measure Time: ${accumulatedData?.measureTime}")
        Log.d(TAG, "Model: ${accumulatedData?.deviceModel}")
        Log.d(TAG, "Serial: ${accumulatedData?.serialNumber}")
        Log.d(TAG, "Battery: ${accumulatedData?.battery}%")
        Log.d(TAG, "SpO2: ${accumulatedData?.spo2}%")
        Log.d(TAG, "Pulse: ${accumulatedData?.pulse} bpm")
        Log.d(TAG, "=============================")
        
        // Report complete data
        accumulatedData?.let { onDataComplete(it) }
    }
}
