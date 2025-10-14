# DocVoicePatient

A Kotlin-based Android application for connecting to Bluetooth Low Energy (BLE) medical devices from manufacturers like Foracare, GRX, and Dr. Trust. The app scans for specific medical devices, establishes connections, and retrieves health data such as SpO2 levels, pulse rate, blood pressure, glucose levels, and weight measurements.

## Features

### Core Functionality
- **BLE Device Scanning**: Automatically scans for supported BLE medical devices
- **Device Connection**: Establishes secure connections to medical devices
- **Data Retrieval**: Fetches real-time health data from connected devices
- **Data Display**: Presents health metrics in a user-friendly interface
- **Automatic Scanning**: Periodic scanning for device availability

### Supported Devices
- **Foracare FORA P20**: Blood pressure monitor (systolic/diastolic pressure, pulse)
- **Foracare FORA PREMIUM V10**: Glucose meter (glucose levels, battery, firmware)
- **TNG SPO2**: Pulse oximeter (SpO2 levels, pulse rate, battery, firmware)
- **TNG SCALE**: Weight scale (weight, BMI)

### Technical Features
- **Permission Management**: Handles Bluetooth and location permissions for Android 6.0+ and 12+
- **Bluetooth State Monitoring**: Monitors Bluetooth adapter state changes
- **Error Handling**: Robust error handling for connection failures and timeouts
- **Background Processing**: Asynchronous BLE operations with proper threading

## Architecture

### Project Structure
```
app/
├── src/main/java/com/techrevhealth/docvoicepatient/
│   ├── ble/                    # BLE connection and data management
│   │   ├── BleRpmManager.kt    # Core BLE logic and device communication
│   │   └── SearchingDialogFragment.kt  # Device scanning UI
│   ├── dataclass/              # Data models
│   │   └── RpmDeviceData.kt    # Device data structure
│   ├── helpers/                # Utility classes
│   │   ├── PermissionHelper.kt # Permission management
│   │   └── RpmDeviceType.kt    # Device type definitions
│   └── ui/                     # User interface
│       └── MainActivity.kt     # Main application activity
├── src/main/res/               # Android resources
└── build.gradle.kts            # Module build configuration
```

### Key Components

#### BleRpmManager
- Manages BLE scanning, connection, and data retrieval
- Implements device-specific communication protocols
- Handles GATT operations and characteristic notifications
- Parses device-specific data formats with checksum validation

#### MainActivity
- Application entry point and UI management
- Handles permission requests and Bluetooth state monitoring
- Displays device data and connection status
- Manages dialog fragments for device scanning

#### PermissionHelper
- Centralized permission checking and requesting
- Supports different permission requirements for Android versions
- Provides utility methods for permission validation

## Requirements

### Android Version
- Minimum SDK: API 22 (Android 5.1)
- Target SDK: API 35 (Android 15)
- Compile SDK: API 35

### Permissions
- `BLUETOOTH`: Basic Bluetooth communication
- `BLUETOOTH_ADMIN`: Bluetooth device management
- `ACCESS_FINE_LOCATION`: BLE scanning (Android 6.0+)
- `ACCESS_COARSE_LOCATION`: Location access for BLE
- `BLUETOOTH_SCAN`: BLE scanning (Android 12+)
- `BLUETOOTH_CONNECT`: BLE connection (Android 12+)
- `BLUETOOTH_ADVERTISE`: BLE advertising (Android 12+)

### Dependencies
- AndroidX Core KTX: Core Android extensions
- AndroidX AppCompat: Backward compatibility
- Material Components: UI components
- AndroidX ConstraintLayout: Flexible layouts
- AndroidX Navigation: Fragment navigation
- AndroidX CoordinatorLayout: Coordinator layouts

## Build Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd BleConnectionApp
   ```

2. **Open in Android Studio**
   - Import the project into Android Studio
   - Ensure Android SDK 35 is installed

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run on device/emulator**
   - Connect an Android device or start an emulator
   - Run the app from Android Studio

## Usage

1. **Launch the App**: Open DocVoicePatient on your Android device
2. **Enable Bluetooth**: Grant Bluetooth permissions and enable Bluetooth if prompted
3. **Grant Permissions**: Allow location and Bluetooth permissions for device scanning
4. **Scan Devices**: The app will automatically scan for supported medical devices
5. **Connect & View Data**: Once connected, health data will be displayed in real-time

## Device Communication Protocol

The app implements manufacturer-specific BLE communication protocols:

### Foracare Devices
- Uses custom command structures with checksum validation
- Supports multiple data types (glucose, blood pressure, SpO2)
- Implements device-specific parsing for accurate data extraction

### TNG Devices
- Compatible with TNG SPO2 and weight scale protocols
- Handles real-time data streaming and battery status
- Supports firmware version reporting

## Development Notes

### BLE Implementation
- Uses Android's Bluetooth LE API for device communication
- Implements proper GATT callback handling
- Manages connection states and error recovery

### Permission Handling
- Runtime permission requests for Android 6.0+
- Separate permission flows for Android 12+ (BLUETOOTH_SCAN/CONNECT)
- Graceful handling of permission denials

### Error Handling
- Connection timeouts and retry mechanisms
- User-friendly error messages for common issues
- Logging for debugging and troubleshooting

## Testing

The project includes basic unit and instrumentation tests:
- Unit tests for utility functions
- Instrumentation tests for UI components
- BLE-specific testing requires physical devices

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project contains proprietary information and is intended for authorized use only. Please refer to the license file for detailed terms.

## Support

For technical support or questions about device compatibility, please contact the development team.
