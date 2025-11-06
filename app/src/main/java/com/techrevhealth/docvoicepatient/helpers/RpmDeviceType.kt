package com.techrevhealth.docvoicepatient.helpers

import com.techrevhealth.docvoicepatient.ble.Brand

enum class RpmDeviceType(
    val id: Int,
    val displayName: String,
    val userFriendlyName: String,
    val brand: Brand
) {
    FORA_P20(1, "FORA P20", "Blood Pressure Monitor", Brand.FORA_CARE),
    TNG_SPO2(2, "TNG SPO2", "Pulse Oximeter", Brand.FORA_CARE),
    TNG_SCALE(3, "TNG SCALE", "Weight Machine", Brand.FORA_CARE),
    FORA_PREMIUM_V10(4, "FORA PREMIUM V10", "Glucose Meter", Brand.FORA_CARE);

    companion object {
        fun fromName(name: String): RpmDeviceType? {
            return entries.find { name.contains(it.displayName, ignoreCase = true) }
        }

        fun fromId(id: Int): RpmDeviceType? {
            return entries.find { it.id == id }
        }

        fun match(input: String): RpmDeviceType? {
            return entries.find {
                input.equals(it.displayName, ignoreCase = true) ||
                        input.contains(it.displayName, ignoreCase = true) ||
                        input == it.id.toString()
            }
        }

        fun getDevicesByBrand(brand: Brand): List<RpmDeviceType> {
            return entries.filter { it.brand == brand }
        }
    }
}
