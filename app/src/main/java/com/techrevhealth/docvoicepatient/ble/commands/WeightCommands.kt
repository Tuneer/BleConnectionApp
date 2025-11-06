package com.techrevhealth.docvoicepatient.ble.commands

/**
 * Weight Scale Commands (TNG SCALE / FORA W300 series)
 * Based on FORA WS series protocol v1.09
 * 
 * Command sequence: 0x23 → 0x24 → 0x27 → 0x28 → 0x71 (weight data)
 */
object WeightCommands {
    
    /**
     * 0x23 - Read clock time
     * Returns: Current device date/time
     */
    fun readClockTime(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x23.toByte(), // CMD: Read clock
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x24 - Read device model
     * Returns: Model code (2-byte Word)
     */
    fun readDeviceModel(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x24.toByte(), // CMD: Read model
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x27 - Read serial number part 1 (SN_0~SN_3)
     * Returns: Second half of serial number
     */
    fun readSerialPart1(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x27.toByte(), // CMD: Serial part 1
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x28 - Read serial number part 2 (SN_4~SN_7)
     * Returns: First half of serial number
     * Complete serial = 0x28 result + 0x27 result
     */
    fun readSerialPart2(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x28.toByte(), // CMD: Serial part 2
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x4F - Read battery status (EXPERIMENTAL)
     * NOTE: This command is NOT documented in official Weight Scale PDF
     * but may work since all FORA devices use similar protocol
     * Returns: Battery % and firmware version if supported
     */
    fun readBattery(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x4F.toByte(), // CMD: Battery (experimental)
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x2B - Read storage number of data
     * Returns: Number of stored readings
     */
    fun readStorageNumber(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x2B.toByte(), // CMD: Read storage number
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x71 - Read weight data with index
     * index: 0 = newest record, 1 = second newest, etc.
     * Returns: 29 bytes (0x1D) including time, weight, BMI, body composition
     */
    fun readWeightData(index: Int = 0): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x71.toByte(), // CMD: Read weight data
            0x02.toByte(), // Length
            (index and 0xFF).toByte(), // Idx_L
            ((index shr 8) and 0xFF).toByte(), // Idx_H
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
