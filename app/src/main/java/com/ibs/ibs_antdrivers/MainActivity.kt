package com.ibs.ibs_antdrivers

import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

private lateinit var bottomNavView: BottomNavigationView


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavView = findViewById(R.id.bottom_navigation)

        // Set "Home" as the selected item
        bottomNavView.selectedItemId = R.id.navHomeDriver

        // Load the HomeFragment initially
        replaceFragment(HomeFragment())

        bottomNavView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navHomeDriver -> {

                    replaceFragment(HomeFragment())
                    true
                }
                R.id.navCamera -> {

                    // replaceFragment(MealPlanFragment()) UNCOMMENT AND ADD CORRECT CLASS WHEN IT IS CREATED
                    true
                }
                R.id.navCallCycle -> {

                    // replaceFragment(SearchFragment())  UNCOMMENT AND ADD CORRECT CLASS WHEN IT IS CREATED
                    true
                }
                R.id.navCatalogue -> {

                     replaceFragment(CatalogueFragment())
                    true
                }
                R.id.navAnnouncementsDriver -> {

                    replaceFragment(AnnouncementsFragment())
                    true
                }
                else -> false
            }
        }

        // Set default fragment if there's no saved instance state
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        // Stop the alarm sound if the app is opened from notification
        val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ringtone = RingtoneManager.getRingtone(this, notificationSound)
        if (ringtone.isPlaying) {
            ringtone.stop()
        }

    }


    private fun replaceFragment(fragment: Fragment) {

        supportFragmentManager.beginTransaction()
            .replace(R.id.frame_container, fragment)
            .commit()
    }
    
    
}