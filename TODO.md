# TODO List for BLE Connection App Fixes

## Screen Lock Management
- [x] Add BroadcastReceiver in MainActivity for ACTION_SCREEN_OFF and ACTION_SCREEN_ON to manage BLE on screen state changes.
- [x] Stop BLE manager when screen is off.
- [x] Restart BLE manager when screen is on.

## Weight Machine Success Response Issue
- [x] In BleRpmManager.onCharacteristicChanged, after parsing data successfully, call stopDeviceStatus to send stop command to device.

## Dialog State Loss Fix
- [x] In MainActivity.showSearchingDialog, add check if (!isFinishing && !isDestroyed) before showing dialog.
- [x] In MainActivity.onBleDataReceived, wrap delayed handler in if (!isFinishing && !isDestroyed) check.

## Testing
- [ ] Test BLE reconnection on screen unlock without crashes.
- [ ] Verify weight machine responds "successful" after data gathering.
