package com.techrevhealth.docvoicepatient.ble.commands

/**
 * Glucose Meter Commands (FORA PREMIUM V10)
 * Based on FORA_TICD_BGMeter_V1.8_20180223 specification
 */
object GlucoseCommands {
    
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
     * 0x23 - Read device clock time (4 bytes)
     * Returns: Day+Month+Year, Minute, Hour
     */
    fun readClockTime(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x23.toByte(), // CMD: Read clock time
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x24 - Read device model
     * Returns: 2-byte model code
     */
    fun readDeviceModel(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x24.toByte(), // CMD: Read device model
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x27 - Read serial number part 1 (SN_0~SN_3)
     * Returns: Last 4 bytes of serial number
     */
    fun readSerialPart1(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x27.toByte(), // CMD: Read serial part 1
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x28 - Read serial number part 2 (SN_4~SN_7)
     * Returns: First 4 bytes of serial number
     */
    fun readSerialPart2(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x28.toByte(), // CMD: Read serial part 2
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x4F - Read battery status (EXPERIMENTAL)
     * NOTE: This command is NOT documented in official glucose meter PDF
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
     * 0x25 - Read stored measurement time (part 1)
     * Index: 0x00 = latest reading
     * Returns: M_Date (2 bytes), M_Time (2 bytes)
     */
    fun readStoredMeasurementTime(index: Int = 0): ByteArray {
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
     * 0x26 - Read stored glucose value (part 2)
     * Index: 0x00 = latest reading
     * Returns: Glucose value (2-byte Word), Type I/II, Code
     */
    fun readStoredGlucoseValue(index: Int = 0): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x26.toByte(), // CMD: Read glucose value
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
