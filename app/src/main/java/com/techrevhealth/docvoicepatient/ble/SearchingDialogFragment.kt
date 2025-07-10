package com.techrevhealth.docvoicepatient.ble

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.techrevhealth.docvoicepatient.R
import com.techrevhealth.docvoicepatient.dataclass.RpmDeviceData
import com.techrevhealth.docvoicepatient.helpers.RpmDeviceType


class SearchingDialogFragment : DialogFragment() {

    private lateinit var bleManager: BleRpmManager
    private lateinit var textStatus: TextView
    private lateinit var progressBar: ProgressBar
    private val TAG = "SearchingDialogFragment"

    fun setBleManager(manager: BleRpmManager) {
        bleManager = manager
    }


    interface BleDialogListener {
        fun onBleStatusUpdated(status: String)
        fun onBleDataReceived(data: RpmDeviceData)
        fun onBleError(error: String)
    }

    private var bleAppListener: BleDialogListener? = null

    fun setBleListener(listener: BleDialogListener) {
        this.bleAppListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
       // val view = LayoutInflater.from(context).inflate(R.layout.dialog_searching, null)
        val view = layoutInflater.inflate(R.layout.dialog_searching, null)
        textStatus = view.findViewById(R.id.textStatus)
        progressBar = view.findViewById(R.id.progressBar)
        builder.setView(view)
        isCancelable = false
        Log.d(TAG, "onCreateDialog: Dialog Opens")
        return builder.create()
    }


    private val bleListener = object : BleRpmManager.BleRpmListener {
        override fun onScanStarted() {
            updateStatus("Searching for devices...")
            startSearchingAnimation()
        }

        override fun onDeviceFound(deviceName: String) {
            updateStatus("Found device: ${RpmDeviceType.fromName(deviceName)?.userFriendlyName}\nConnecting...")
            startConnectingAnimation()
        }


        override fun onConnecting(deviceName: String) {
            updateStatus("Connecting to ${RpmDeviceType.fromName(deviceName)?.userFriendlyName}...")
            startConnectingAnimation()
        }

        override fun onConnected(deviceName: String) {
            updateStatus("Connected to ${RpmDeviceType.fromName(deviceName)?.userFriendlyName}")
            activity?.runOnUiThread {
                // update UI here
                stopAnimations()
            }

        }

        override fun onDataReceived(deviceName: String, data: RpmDeviceData) {
            if (deviceName.equals(RpmDeviceType.TNG_SPO2.displayName,true)){
                Log.d(TAG, "Data from ${RpmDeviceType.fromName(deviceName)?.userFriendlyName}: ${data.spo2}")
            }else if (deviceName.equals(RpmDeviceType.FORA_PREMIUM_V10.displayName,true)){
                Log.d(TAG, "Data from ${RpmDeviceType.fromName(deviceName)?.userFriendlyName}: ${data.glucose}")
            }else if (deviceName.equals(RpmDeviceType.FORA_P20.displayName,true)){
                Log.d(TAG, "Data from ${RpmDeviceType.fromName(deviceName)?.userFriendlyName}: " +
                        "Systolic: "+"${data.systolic}" + "Diastolic: "+"${data.diastolic}"
                        +"Pulse: "+"${data.pulse}")
            }else if (deviceName.equals(RpmDeviceType.TNG_SCALE.displayName,true)){
                Log.d(TAG, "Data from ${RpmDeviceType.fromName(deviceName)?.userFriendlyName}: " +
                       "Weight: " +"${data.weight}"+" BMI:- "+"${data.bmi}")
            }

            // Optionally forward data to activity/fragment via interface or LiveData
            bleAppListener?.onBleDataReceived(data)
            // Dismiss dialog
            dismiss()
        }

        override fun onDisconnected() {
            updateStatus("Disconnected. Restarting scan...")
            activity?.runOnUiThread {
                startSearchingAnimation()
            }
        }

        override fun onScanStopped() {
            // Optional UI updates
            updateStatus("Scan stopped")
            activity?.runOnUiThread {
                stopAnimations()
            }
        }

        override fun onUserCancelled() {
            updateStatus("Scan stopped")
            bleAppListener?.onBleError("")
        }

        override fun onBluetoothEnableRequested() {
            TODO("Not yet implemented")
        }

        override fun onPermissionRequestRequested() {
            TODO("Not yet implemented")
        }
    }

    private fun updateStatus(text: String) {
        textStatus.post { textStatus.text = text }
        bleAppListener?.onBleStatusUpdated(text)
    }

    private fun startSearchingAnimation() {
        progressBar.isIndeterminate = true
        progressBar.visibility = View.VISIBLE
    }

    private fun startConnectingAnimation() {
        progressBar.isIndeterminate = true
        progressBar.visibility = View.VISIBLE
    }

    private fun stopAnimations() {
        progressBar.isIndeterminate = false
        progressBar.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        if (!isAdded || activity == null || activity?.isFinishing == true || activity?.isDestroyed == true) {
            return  // Prevent UI updates if fragment/activity not valid
        }
        bleManager.listener = bleListener
        bleManager.start()
    }

    override fun onStop() {
        super.onStop()
        bleManager.stop()
    }
}
