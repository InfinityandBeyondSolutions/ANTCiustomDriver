package com.ibs.ibs_antdrivers.ui.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.ibs.ibs_antdrivers.MainActivity
import com.ibs.ibs_antdrivers.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriverHomeFragment : Fragment() {

    private var vehicleTrackingBtn: ImageButton? = null
    private var settingsBtn: ImageView? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvTimes: TextView
    private lateinit var btnClock: Button

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tvStatus)
        tvTimes = view.findViewById(R.id.tvClockTimes)
        btnClock = view.findViewById(R.id.btnClock)

        // Optional buttons (only wire up if they exist in your layout)
        vehicleTrackingBtn = view.findViewById<ImageButton?>(R.id.vehicleTrackingBtn)?.apply {
            setOnClickListener {
                // If you want this to also toggle tracking, you can call the same actions here.
                val act = activity as? MainActivity ?: return@setOnClickListener
                if (act.isTrackingActive()) {
                    act.clockOut()
                } else {
                    act.clockIn()
                }
                view.postDelayed({ refreshUi() }, 800)
            }
        }

       //settingsBtn = view.findViewById<ImageView?>(R.id.settingsBtn)?.apply {
        //    setOnClickListener {
                // Open system Location settings for quick access
          //      startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
           // }
      //  }

        btnClock.setOnClickListener {
            val act = activity as? MainActivity ?: return@setOnClickListener
            // This will handle permission requests if needed
            if (act.isTrackingActive()) {
                act.clockOut()
            } else {
                act.clockIn()
            }
            // Give the activity a moment to write prefs / DB
            view.postDelayed({ refreshUi() }, 800)
        }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // Update UI in case permissions were granted or state changed while away
        refreshUi()
    }

    private fun refreshUi() {
        val act = activity as? MainActivity ?: return
        val active = act.isTrackingActive()

        tvStatus.text = if (active) "Status: Online / Tracking" else "Status: Offline"
        btnClock.text = if (active) "Clock Out" else "Clock In"

        val clockInAt = act.getClockInAt()
        tvTimes.text = if (clockInAt > 0) {
            "Clocked in at: ${timeFmt.format(Date(clockInAt))}"
        } else {
            "Not clocked in"
        }
    }
}