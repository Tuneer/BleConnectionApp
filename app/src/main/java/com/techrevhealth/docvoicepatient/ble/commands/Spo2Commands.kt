package com.techrevhealth.docvoicepatient.ble.commands

/**
 * SPO2 Device Commands (FORA TNG SPO2)
 * Based on TICD_FORA_SPO2_V1.05_20151106 specification
 */
object Spo2Commands {
    
    /**
     * 0x52 - Clear/Delete all memory
     */
    fun clearMemory(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(), // Start
            0x52.toByte(), // CMD: Clear memory
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(), // Stop
            0x00           // Checksum
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
     * 0x24 - Read device model/project code
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
     * 0x4F - Read battery/firmware (EXPERIMENTAL - NOT DOCUMENTED)
     * This command is not in official PDF
     */
    fun readBattery(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x4F.toByte(), // CMD: Battery/firmware (undocumented)
            0x00, 0x00, 0x00, 0x00,
            0xA3.toByte(),
            0x00
        )
        command[7] = calculateChecksum(command)
        return command
    }
    
    /**
     * 0x49 - Read real-time SpO2 and pulse data
     * Returns: SpO2 %, Heart Rate bpm
     */
    fun readSpo2Data(): ByteArray {
        val command = byteArrayOf(
            0x51.toByte(),
            0x49.toByte(), // CMD: Read SpO2/Pulse
            0x00, 0x00, 0x00, 0x00,
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
