package com.ibs.ibs_antdrivers

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---- Views ----
        val imgAvatar           = view.findViewById<ImageView>(R.id.imgAvatar)
        val txtName             = view.findViewById<TextView>(R.id.txtName)
        val txtEmail            = view.findViewById<TextView>(R.id.txtEmail)
        val switchBiometrics    = view.findViewById<MaterialSwitch>(R.id.switchBiometrics)
        val switchDarkMode      = view.findViewById<MaterialSwitch>(R.id.switchDarkMode)
        val switchNotifications = view.findViewById<MaterialSwitch>(R.id.switchNotifications)
        val btnChangePassword   = view.findViewById<MaterialButton>(R.id.btnChangePassword)
        val btnSignOut          = view.findViewById<MaterialButton>(R.id.btnSignOut)
        val txtVersion          = view.findViewById<TextView>(R.id.txtVersion)
        val btnBack             = view.findViewById<ImageView>(R.id.btnBackSettings)
        // (optional) val btnSupport = view.findViewById<MaterialButton>(R.id.btnSupport)

        // ---- Back nav ----
        btnBack.setOnClickListener { findNavController().navigateUp() }

        // ---- Local prefs for switches ----
        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

        switchBiometrics.isChecked    = prefs.getBoolean("biometrics", false)
        switchDarkMode.isChecked      = prefs.getBoolean("dark_mode", false)
        switchNotifications.isChecked = prefs.getBoolean("notifications", true)

        switchBiometrics.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("biometrics", v).apply()
        }
        switchDarkMode.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("dark_mode", v).apply()
            // If you want to apply immediately, wire AppCompatDelegate here.
        }
        switchNotifications.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean("notifications", v).apply()
        }

        // ---- Version label ----
        txtVersion.text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        // ---- Change password ----
        btnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_changePassword)
        }

        // ---- Sign out ----
        btnSignOut.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sign out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign out") { _, _ ->
                    auth.signOut()
                    Snackbar.make(view, "Signed out", Snackbar.LENGTH_SHORT).show()
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ---- Load profile from Realtime Database ----
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Snackbar.make(view, "Not authenticated", Snackbar.LENGTH_LONG).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) {
                    FirebaseDatabase.getInstance()
                        .reference.child("users").child(uid)
                        .get().await()
                }
                val profile = snap.getValue(UserProfile::class.java)

                // Name + email
                val fullName = listOfNotNull(profile?.firstName, profile?.lastName)
                    .joinToString(" ").ifEmpty { "Employee" }
                txtName.text = fullName
                txtEmail.text = profile?.email ?: auth.currentUser?.email ?: "email@example.com"

                // Avatar
                val url = profile?.profileImageUrl
                if (!url.isNullOrBlank()) {
                    imgAvatar.load(url) {
                        crossfade(true)
                    }
                } else {
                    // fallback drawable if you have one
                    imgAvatar.setImageResource(R.drawable.vector_announcements)
                }
            } catch (e: Exception) {
                Snackbar.make(view, "Failed to load profile: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }

        // (optional) Support button click:
        // btnSupport.setOnClickListener { /* open email/chat */ }
    }
}
