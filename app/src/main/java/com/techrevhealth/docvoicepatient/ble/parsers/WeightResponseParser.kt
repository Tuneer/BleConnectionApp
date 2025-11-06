package com.techrevhealth.docvoicepatient.ble.parsers

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

/**
 * Weight Scale Response Parser
 * Handles parsing of responses from TNG Scale
 */
class WeightResponseParser(
    private val deviceName: String,
    private val onDataComplete: (RpmDeviceData) -> Unit
) {
    
    companion object {
        private const val TAG = "WeightResponseParser"
    }
    
    fun parseResponse(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "parseResponse: CMD=0x${String.format("%02X", data[1])}")
        
        if (data.isEmpty()) return
        
        when (data[1].toInt() and 0xFF) {
            0x71 -> handle0x71WeightData(data)
        }
    }
    
    private fun handle0x71WeightData(data: ByteArray) {
        Log.d(TAG, "0x71: Weight Data")
        val hexString = data.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Raw bytes: $hexString")
        
        try {
            // Weight parsing logic based on TNG Scale protocol
            val weightInt = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            val weight = weightInt / 10.0 // Convert to kg with decimal
            
            val bmiInt = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
            val bmi = bmiInt / 10.0 // Convert to BMI with decimal
            
            Log.d(TAG, "Weight: $weight kg")
            Log.d(TAG, "BMI: $bmi")
            
            val weightData = RpmDeviceData(
                deviceName = deviceName,
                weight = weight,
                bmi = bmi
            )
            
            Log.d(TAG, "=== FINAL DATA ===")
            Log.d(TAG, "Weight: ${weightData.weight} kg")
            Log.d(TAG, "BMI: ${weightData.bmi}")
            Log.d(TAG, "==================")
            
            onDataComplete(weightData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse weight data: ${e.message}")
        }
    }
}
