package com.techrevhealth.docvoicepatient.ui

import android.Manifest
import android.app.Activity
import android.app.ComponentCaller
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.techrevhealth.docvoicepatient.ble.BleRpmManager
import com.techrevhealth.docvoicepatient.ble.SearchingDialogFragment
import com.techrevhealth.docvoicepatient.databinding.ActivityMainBinding
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData
import com.techrevhealth.docvoicepatient.helpers.PermissionHelper
import com.techrevhealth.docvoicepatient.helpers.RpmDeviceType

class MainActivity : AppCompatActivity(), SearchingDialogFragment.BleDialogListener {


    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleRpmManager
    private var searchingDialog: SearchingDialogFragment? = null
    private var permissionRationaleDialog: AlertDialog? = null
    private var permissionRequested = false
    private val REQUEST_ENABLE_BT = 1001
    private val REQUEST_PERMISSIONS = 1002


    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    //private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        // Bluetooth was turned OFF by user — ask again to enable
                        Log.d(TAG, "onReceive: Bluetooth is OFF.")
                        runOnUiThread { showBluetoothEnableRequiredDialog() }
                    }
                    BluetoothAdapter.STATE_ON -> {
                        // Bluetooth was turned ON by user — check permissions
                        Log.d(TAG, "onReceive: Bluetooth is ON.")
                        checkAndRequestPermissions()
                    }
                    BluetoothAdapter.STATE_CONNECTED -> {
                        Log.d(TAG, "onReceive: Bluetooth is Connected.")
                        checkAndRequestPermissions()
                    }
                    BluetoothAdapter.STATE_DISCONNECTED -> {
                        Log.d(TAG, "onReceive: Bluetooth is Disconnected.")
                        runOnUiThread { showBluetoothEnableRequiredDialog() }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "onCreate: ")

        registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )

        bleManager = BleRpmManager(this, object : BleRpmManager.BleRpmListener {
            override fun onScanStarted() {
                binding.statusTextView.text = "Scanning for devices..."
            }
            override fun onDeviceFound(deviceName: String) {
                binding.statusTextView.text = "Device found: $deviceName"
            }
            override fun onConnecting(deviceName: String) { }
            override fun onConnected(deviceName: String) {
                binding.statusTextView.text = "Connected to $deviceName"
            }
            override fun onDataReceived(deviceName: String, data: RpmDeviceData) {
                runOnUiThread {
                    // Parse and show data in UI here
                    val builder = StringBuilder()
                    builder.append("Device: ${data.deviceName}\n")
                    data.serialNumber?.let { builder.append("Serial No: $it\n") }
                    data.spo2?.let { builder.append("SpO2: $it%\n") }
                    data.pulse?.let { builder.append("Pulse: $it bpm\n") }
                    data.battery?.let { builder.append("Battery: $it%\n") }
                    data.firmware?.let { builder.append("Firmware: $it\n") }

                    binding.statusTextView.text = builder.toString()
                }
            }
            override fun onDisconnected() {
                binding.statusTextView.text = "Device disconnected"
            }
            override fun onScanStopped() {
                binding.statusTextView.text = "Scan stopped"
                checkBluetoothEnabled()
            }
            override fun onUserCancelled() {
            }

            override fun onBluetoothEnableRequested() {
            }

            override fun onPermissionRequestRequested() {
            }
        })

//        // Initialize the permission launcher
//        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
//            val allGranted = permissions.entries.all { it.value }
//            if (allGranted) {
//                // Permissions granted
//                Log.d(TAG, "onCreate: Permissions granted")
//                showSearchingDialog()
//            } else {
//                // Permissions denied, show rationale dialog
//                Log.d(TAG, "onCreate: Permissions not granted")
//                showPermissionRationale()
//            }
//        }

        checkBluetoothEnabled()

    }

    private fun checkBluetoothEnabled() {
        if (bluetoothAdapter == null) {
            showNoBluetoothSupportDialog()
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            Log.d(TAG, "checkBluetoothEnabled: bluetooth Already Enabled.")
            checkAndRequestPermissions()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth enabled, check permissions next
                Log.d(TAG,"Bluetooth enabled")
                checkAndRequestPermissions()
            } else {
                showBluetoothEnableRequiredDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()

    }

    private fun showBluetoothEnableRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth Required")
            .setMessage("Bluetooth must be enabled for this app to work.")
            .setPositiveButton("Enable") { _, _ -> checkBluetoothEnabled() }
            .setNegativeButton("Exit") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .show()
    }

    private fun showNoBluetoothSupportDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth Not Supported")
            .setMessage("This device does not support Bluetooth, so this app cannot function.")
            .setPositiveButton("Exit") { _, _ -> finishAffinity() }
            .setCancelable(false)
            .show()
    }

    private fun checkAndRequestPermissions() {
        if (PermissionHelper.hasAllPermissions(this)) {
            Log.d(TAG, "checkAndRequestPermissions: All Permission Given")
            showSearchingDialog()
        } else {
            Log.d(TAG, "checkAndRequestPermissions: All Permission NotGiven")
            requestPermissions()
        }
    }


    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            PermissionHelper.getRequiredPermissions(),
            REQUEST_PERMISSIONS
        )
    }

    private fun showPermissionRationale() {
        if (permissionRationaleDialog?.isShowing == true) return

        if (PermissionHelper.hasAllPermissions(this)) {
            showSearchingDialog() // Skip dialog if permissions are already granted
            return
        }

        permissionRationaleDialog = AlertDialog.Builder(this)
            .setTitle("Permissions required")
            .setMessage("This app needs Bluetooth and Location permissions to scan and connect to medical devices. Without these, the app cannot function.")
            .setCancelable(false)
            .setPositiveButton("Grant Permissions") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Exit App") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun showSearchingDialog() {
        Log.d(TAG, "showSearchingDialog: searching started")
        if (searchingDialog?.isAdded == true) return

        searchingDialog = SearchingDialogFragment().apply {
            setBleManager(bleManager)
            setBleListener(this@MainActivity)
        }
        if (!isFinishing && !isDestroyed) {
            Log.d(TAG, "showSearchingDialog: searching Dialog Opens")
            searchingDialog?.show(supportFragmentManager, "SearchingDialog")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)

        permissionRationaleDialog?.dismiss()
        permissionRationaleDialog = null

        searchingDialog?.dismissAllowingStateLoss()
        searchingDialog = null

        bleManager.stop()
    }

    override fun onBleStatusUpdated(status: String) {
        runOnUiThread {
            binding.statusTextView.text = status
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSIONS) {
            val allPermissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allPermissionsGranted) {
                Log.d(TAG, "All permissions granted!")
                showSearchingDialog()
            } else {
                showPermissionRationale() // Show the rationale dialog
            }
        }
    }


    override fun onBleDataReceived(data: RpmDeviceData) {
        runOnUiThread {
            // Parse and show data in UI here
            val builder = StringBuilder()
            if (data.deviceName.equals(RpmDeviceType.TNG_SPO2.displayName,true)){
                builder.append("Device: ${RpmDeviceType.fromName(data.deviceName)?.userFriendlyName}\n")
                data.serialNumber?.let { builder.append("Serial No: $it\n") }
                data.spo2?.let { builder.append("SpO2: $it%\n") }
                data.pulse?.let { builder.append("Pulse: $it bpm\n") }
                data.battery?.let { builder.append("Battery: $it%\n") }
                data.firmware?.let { builder.append("Firmware: $it\n") }
            }else if (data.deviceName.equals(RpmDeviceType.FORA_PREMIUM_V10.displayName,true)){
                builder.append("Device: ${RpmDeviceType.fromName(data.deviceName)?.userFriendlyName}\n")
                data.serialNumber?.let { builder.append("Serial No: $it\n") }
                data.glucose?.let { builder.append("Glucose: $it Mg/Dl\n") }
              //  data.pulse?.let { builder.append("Pulse: $it bpm\n") }
                data.battery?.let { builder.append("Battery: $it%\n") }
                data.firmware?.let { builder.append("Firmware: $it\n") }
            }else if (data.deviceName.equals(RpmDeviceType.FORA_P20.displayName,true)){
                builder.append("Device: ${RpmDeviceType.fromName(data.deviceName)?.userFriendlyName}\n")
                data.serialNumber?.let { builder.append("Serial No: $it") }
                data.systolic?.let { builder.append("Systolic: $it mm/Hg\n") }
                data.diastolic?.let { builder.append("Diastolic: $it mm/Hg\n") }
                data.pulse?.let { builder.append("Pulse: $it bpm") }
            }else if (data.deviceName.equals(RpmDeviceType.TNG_SCALE.displayName,true)){
                builder.append("Device: ${RpmDeviceType.fromName(data.deviceName)?.userFriendlyName}")
                data.serialNumber?.let { builder.append("Serial No: $it") }
                data.weight?.let { builder.append("Weight: $it kg") }
                data.bmi?.let { builder.append("Bmi: $it bmi") }
            }

            binding.statusTextView.text = builder.toString()
            searchingDialog?.dismissAllowingStateLoss()
            searchingDialog = null

            Handler(Looper.getMainLooper()).postDelayed({
                checkBluetoothEnabled()
            }, 10000) // delay must be >= 600ms to be safe
        }
    }

    override fun onBleError(error: String) {
        binding.statusTextView.text = ""
        searchingDialog?.dismissAllowingStateLoss()
        searchingDialog = null

        Handler(Looper.getMainLooper()).postDelayed({
            checkBluetoothEnabled()
        }, 15000) // delay must be >= 600ms to be safe
    }

}