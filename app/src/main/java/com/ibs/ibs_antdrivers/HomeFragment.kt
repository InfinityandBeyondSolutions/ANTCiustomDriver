package com.ibs.ibs_antdrivers

import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.widget.Toast
import java.sql.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var tvTimes: TextView
    private lateinit var btnClock: Button

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val PERMISSION_REQUEST_CODE = 100
    private lateinit var vehicleBtn : ImageView
    private lateinit var settingsBtn : ImageView

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkAndRequestPermissions()

        tvStatus = view.findViewById(R.id.tvStatus)
        tvTimes = view.findViewById(R.id.tvClockTimes)
        btnClock = view.findViewById(R.id.btnClock)

        refreshUi()

        btnClock.setOnClickListener {
            val act = activity as MainActivity
            if (act.isTrackingActive()) {
                act.clockOut()
            } else {
                act.clockIn()
            }
            view.postDelayed({ refreshUi() }, 600) // slight delay for DB write
        }
    }

    private fun refreshUi() {
        val act = activity as MainActivity
        val active = act.isTrackingActive()
        tvStatus.text = if (active) "Status: Online / Tracking" else "Status: Offline"
        btnClock.text = if (active) "Clock Out" else "Clock In"

        val clockInAt = act.getClockInAt()
        if (clockInAt > 0) {
            tvTimes.text = "Clocked in at: ${timeFmt.format(Date(clockInAt))}"
        } else {
            tvTimes.text = "Not clocked in"
        }
    }


    private fun checkAndRequestPermissions() {
        val context = requireContext()
        val activity = requireActivity()

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
}
