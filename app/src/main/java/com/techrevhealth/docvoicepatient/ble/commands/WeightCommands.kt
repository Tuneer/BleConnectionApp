package com.techrevhealth.docvoicepatient.ble.commands

/**
 * Weight Scale Commands (TNG SCALE)
 * Based on TNG Scale protocol
 */
object WeightCommands {
    
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
        command[6] = calculateDynamicChecksum(command)
        return command
    }
    
    /**
     * 0x71 - Read weight data
     * Returns: Weight in kg, BMI, timestamp
     */
    fun readWeightData(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x71.toByte(), // CMD: Read weight data
            0x02,
            0x01,
            0x00,
            0xA3.toByte(),
            0x00  // Checksum
        )
        command[6] = calculateDynamicChecksum(command)
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
     * Calculate checksum for weight scale commands (7-byte format)
     */
    private fun calculateDynamicChecksum(data: ByteArray): Byte {
        var sum = 0
        for (i in 0 until data.size - 1) {
            sum += data[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }
    
    /**
     * Calculate checksum for standard 8-byte commands
     */
    private fun calculateChecksum(data: ByteArray): Byte {
        var sum = 0
        for (i in 0 until 7) {
            sum += data[i].toInt() and 0xFF
        }
        return (sum and 0xFF).toByte()
    }
}
