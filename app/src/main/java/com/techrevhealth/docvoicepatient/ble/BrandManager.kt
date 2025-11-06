package com.techrevhealth.docvoicepatient.ble

import android.Manifest
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData

enum class Brand(val displayName: String) {
    FORA_CARE("FORA CARE"),
    GRX("GRX"),
    DR_TRUST("DrTrust"),
    SALYX("Salyx");

    companion object {
        fun fromDeviceName(deviceName: String): Brand? {
            return when {
                ForaCareHandler().shouldConnect(deviceName) -> FORA_CARE
                deviceName.contains("SAL-", ignoreCase = true) -> SALYX
                // Add other brand detection logic here
                else -> null
            }
        }
    }
}

class BrandManager {

    private val handlers = mapOf<Brand, DeviceHandler>(
        Brand.FORA_CARE to ForaCareHandler(),
        Brand.SALYX to SalyxHandler(),
        // Add other brand handlers here as they are implemented
        // Brand.GRX to GrxHandler(),
        // Brand.DR_TRUST to DrTrustHandler(),
    )

    fun getHandlerForBrand(brand: Brand): DeviceHandler? {
        return handlers[brand]
    }

    fun getHandlerForDevice(deviceName: String): DeviceHandler? {
        val brand = Brand.fromDeviceName(deviceName)
        return brand?.let { getHandlerForBrand(it) }
    }

    fun shouldConnect(deviceName: String): Boolean {
        return getHandlerForDevice(deviceName)?.shouldConnect(deviceName) ?: false
    }

    fun buildReadCommand(deviceName: String): ByteArray? {
        return getHandlerForDevice(deviceName)?.buildReadCommand(deviceName)
    }

    fun buildStopCommand(deviceName: String): ByteArray? {
        return getHandlerForDevice(deviceName)?.buildStopCommand(deviceName)
    }

    fun parseData(data: ByteArray, gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, deviceName: String, context: Context): RpmDeviceData? {
        return getHandlerForDevice(deviceName)?.parseData(data, gatt, characteristic, deviceName, context)
    }
}
