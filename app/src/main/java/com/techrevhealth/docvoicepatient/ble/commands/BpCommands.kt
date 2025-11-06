package com.techrevhealth.docvoicepatient.ble.commands

/**
 * Blood Pressure Monitor Commands (FORA P20)
 * Based on FORA P-series BP Monitor specification
 * 
 * Command sequence: 0x52 → 0x23 → 0x24 → 0x27 → 0x28 → 0x25 → 0x26
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
     * NOTE: This command is NOT documented in official BP meter PDF
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
     * 0x25 - Read BP measurement data (time)
     * Returns: Stored measurement date and time when BP reading was taken
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
     * Returns: Systolic (data[2]), Diastolic (data[4]), Pulse (data[5])
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
