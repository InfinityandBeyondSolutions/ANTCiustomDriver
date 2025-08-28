package com.ibs.ibs_antdrivers

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class VehicleTracking : Fragment() {

    private lateinit var database: DatabaseReference
    private lateinit var containerLayout: LinearLayout
    private lateinit var noText: TextView
    private lateinit var recyclerView: RecyclerView
    private val announcements = mutableListOf<Announcement>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_vehicle_tracking, container, false)

        /*Babes if you keen for some ideas, you can add a report incident feature in case of any accidents or small errors,
        * as well as any repairs being made to a selected vehicle
        *
        * Keep in mind each vehicle will be its own entity so they will have their own Vehicle ID's , and we need to know which driver did something to each vehicle
        * so reports will be driver and vehicle specific
        *
        * RBAC implementation so the driver will only be able to see the incidents that they report, we will
        * also need to be able to select a vehicle from a drop down at the top of the page so they know which vehicle they are capturing data for
        *
        * Then you can add a fuel tracking feature, the drivers will only be able to take a LIVE photo (they cannot upload one manually later) of the fuel meter
        * and they need to enter the fuel meter number itself
        *
        * then we can add nay other feature you can come up with later */

        return view
    }


}


