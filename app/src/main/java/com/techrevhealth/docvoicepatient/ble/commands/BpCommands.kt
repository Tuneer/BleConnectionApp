package com.techrevhealth.docvoicepatient.ble.commands

/**
 * Blood Pressure Monitor Commands (FORA P20)
 * Based on FORA P-series BP Monitor specification
 */
object BpCommands {
    
    /**
     * 0x52 - Clear/Delete all memory
     */
    fun clearMemory(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x52.toByte(), // CMD: Clear memory
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x25 - Read BP measurement data (time)
     * Returns: Measurement date and time
     */
    fun readMeasurementTime(index: Int = 0): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x25.toByte(), // CMD: Read measurement time
            (index and 0xFF).toByte(),
            ((index shr 8) and 0xFF).toByte(),
            0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x26 - Read BP measurement data (result)
     * Returns: Systolic, Diastolic, Pulse
     */
    fun readBpValue(index: Int = 0): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x26.toByte(), // CMD: Read BP value
            (index and 0xFF).toByte(),
            ((index shr 8) and 0xFF).toByte(),
            0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x50 - Turn off the device
     */
    fun stopDevice(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x50.toByte(), // CMD: Stop device
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * Calculate checksum for FORA device commands
     * Checksum = sum of bytes 0-6, truncated to 8 bits
     */
    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (i in 0 until 7) {
            sum += data[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }
}
