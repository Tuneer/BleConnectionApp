package com.techrevhealth.docvoicepatient.ble.parsers

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

/**
 * Blood Pressure Monitor Response Parser
 * Handles parsing of responses from FORA P20 BP Monitor
 */
class BpResponseParser(
    private val deviceName: String,
    private val onDataComplete: (RpmDeviceData) -> Unit
) {
    
    private var accumulatedData: RpmDeviceData? = null
    
    companion object {
        private const val TAG = "BpResponseParser"
    }
    
    fun parseResponse(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        Log.d(TAG, "parseResponse: CMD=0x${String.format("%02X", data[1])}")
        
        if (data.isEmpty()) return
        
        when (data[1].toInt() and 0xFF) {
            0x26 -> handle0x26BpValue(data)
        }
    }
    
    private fun handle0x26BpValue(data: ByteArray) {
        Log.d(TAG, "0x26: BP Value")
        val hexString = data.joinToString(" ") { String.format("%02X", it) }
        Log.d(TAG, "Raw bytes: $hexString")
        
        try {
            val systolic = data[0].toInt() and 0xFF
            val diastolic = data[1].toInt() and 0xFF
            val pulse = data[2].toInt() and 0xFF
            
            Log.d(TAG, "Systolic: $systolic mmHg")
            Log.d(TAG, "Diastolic: $diastolic mmHg")
            Log.d(TAG, "Pulse: $pulse bpm")
            
            val bpData = RpmDeviceData(
                deviceName = deviceName,
                systolic = systolic.toDouble(),
                diastolic = diastolic.toDouble(),
                pulse = pulse
            )
            
            Log.d(TAG, "=== FINAL DATA ===")
            Log.d(TAG, "Systolic: ${bpData.systolic} mmHg")
            Log.d(TAG, "Diastolic: ${bpData.diastolic} mmHg")
            Log.d(TAG, "Pulse: ${bpData.pulse} bpm")
            Log.d(TAG, "==================")
            
            onDataComplete(bpData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse BP data: ${e.message}")
        }
    }
}
