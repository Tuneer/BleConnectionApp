package com.techrevhealth.docvoicepatient.dataclass

data class RpmDeviceData(
    val deviceName: String,
    val spo2: Int? = null,
    val pulse: Int? = null,
    val systolic: Double? = null,
    val diastolic: Double? = null,
    val weight: Double? = null,
    val bmi: Double? = null,
    val glucose: Double? = null,
    val battery: Int? = null,
    val firmware: String? = null,
    val serialNumber: String? = null
)
