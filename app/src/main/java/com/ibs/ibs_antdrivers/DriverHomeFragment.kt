package com.ibs.ibs_antdrivers

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.navigation.fragment.findNavController
import com.ibs.ibs_antdrivers.R

class DriverHomeFragment : Fragment() {

    private lateinit var vehicleTrackingBtn: ImageButton
    private lateinit var settingsBtn: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Handle arguments if needed
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_driver_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the ImageButton
        vehicleTrackingBtn = view.findViewById(R.id.vehicleTrackingBtn)
        settingsBtn = view.findViewById(R.id.settingsIcon)

        // Set click listener for vehicle tracking button
        vehicleTrackingBtn.setOnClickListener {
            findNavController().navigate(R.id.action_navHomeDriver_to_vehicleTrackingFragment)
        }
    }
}