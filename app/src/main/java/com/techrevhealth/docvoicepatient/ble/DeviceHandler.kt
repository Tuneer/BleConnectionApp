package com.techrevhealth.docvoicepatient.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

interface DeviceHandler {
    fun getSupportedDeviceNames(): List<String>
    fun buildReadCommand(deviceName: String): ByteArray
    fun buildStopCommand(deviceName: String): ByteArray
    fun parseData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData?
    fun shouldConnect(deviceName: String): Boolean
    fun getCommandCharacteristicUUID(): String? = null
}
