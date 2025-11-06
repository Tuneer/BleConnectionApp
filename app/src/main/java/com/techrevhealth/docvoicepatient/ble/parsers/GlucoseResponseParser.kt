package com.techrevhealth.docvoicepatient.ble.parsers

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.techrevhealth.docvoicepatient.ble.commands.GlucoseCommands
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

/**
 * Glucose Meter Response Parser
 * Handles parsing of responses from FORA PREMIUM V10 glucose meter
 */
class GlucoseResponseParser(
    private val deviceName: String,
    private val onDataComplete: (RpmDeviceData) -> Unit,
    private val writeCharacteristic: (ByteArray, BluetoothGatt, BluetoothGattCharacteristic) -> Unit
) {
    
    private var accumulatedData: RpmDeviceData? = null
    
    companion object {
        private const val TAG = "GlucoseResponseParser"
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
            0x26 -> handle0x26GlucoseValue(data)
        }
    }
    
    private fun handle0x52ClearMemory(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x52: Memory cleared")
        accumulatedData = RpmDeviceData(deviceName = deviceName)
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(GlucoseCommands.readClockTime(), gatt, characteristic)
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
            Log.d(TAG, "Device Clock: $timestamp")
            
            accumulatedData = RpmDeviceData(deviceName = deviceName, measureTime = timestamp)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse clock time: ${e.message}")
            accumulatedData = RpmDeviceData(deviceName = deviceName)
        }
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(GlucoseCommands.readDeviceModel(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x24DeviceModel(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x24: Device Model")
        val modelCode = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        
        accumulatedData = accumulatedData?.copy(deviceModel = modelCode.toString())
            ?: RpmDeviceData(deviceName = deviceName, deviceModel = modelCode.toString())
        
        Log.d(TAG, "Model: $modelCode")
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(GlucoseCommands.readSerialPart1(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x27SerialPart1(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x27: Serial Part 1 (SN_0~3)")
        val serial1 = String.format("%02X%02X%02X%02X", data[0], data[1], data[2], data[3])
        
        accumulatedData = accumulatedData?.copy(firmware = serial1)
            ?: RpmDeviceData(deviceName = deviceName, firmware = serial1)
        
        Log.d(TAG, "Serial Part 1: $serial1")
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(GlucoseCommands.readSerialPart2(), gatt, characteristic)
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
            writeCharacteristic(GlucoseCommands.readStoredMeasurementTime(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x25MeasurementTime(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "0x25: Stored Measurement Time")
        try {
            val data0 = data[0].toInt() and 0xFF
            val data1 = data[1].toInt() and 0xFF
            val minute = data[2].toInt() and 0x3F
            val hour = data[3].toInt() and 0x1F
            
            val day = data0 and 0x1F
            val month = ((data0 shr 5) and 0x07) or ((data1 and 0x01) shl 3)
            val year = ((data1 shr 1) and 0x7F) + 2000
            
            val measurementTime = String.format("%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
            Log.d(TAG, "Measurement Time: $measurementTime")
            
            accumulatedData = accumulatedData?.copy(measureTime = measurementTime)
                ?: RpmDeviceData(deviceName = deviceName, measureTime = measurementTime)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse measurement time: ${e.message}")
        }
        
        Handler(Looper.getMainLooper()).postDelayed({
            writeCharacteristic(GlucoseCommands.readStoredGlucoseValue(), gatt, characteristic)
        }, 700)
    }
    
    private fun handle0x26GlucoseValue(data: ByteArray) {
        Log.d(TAG, "0x26: Glucose Value")
        val hexString = data.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Raw bytes: $hexString")
        
        // Per PDF: Value is 2-byte Word (Data_1 + Data_0)
        val glucose = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
        Log.d(TAG, "Glucose: $glucose mg/dL")
        
        accumulatedData = accumulatedData?.copy(glucose = glucose.toDouble())
            ?: RpmDeviceData(deviceName = deviceName, glucose = glucose.toDouble())
        
        // Log final data
        Log.d(TAG, "=== FINAL DATA ===")
        Log.d(TAG, "Measure Time: ${accumulatedData?.measureTime}")
        Log.d(TAG, "Model: ${accumulatedData?.deviceModel}")
        Log.d(TAG, "Serial: ${accumulatedData?.serialNumber}")
        Log.d(TAG, "Glucose: ${accumulatedData?.glucose} mg/dL")
        Log.d(TAG, "==================")
        
        // Report complete data
        accumulatedData?.let { onDataComplete(it) }
    }
}
