package com.ibs.ibs_antdrivers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.RequiresPermission
import androidx.biometric.BiometricManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.ui.GroupListFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val auth by lazy { FirebaseAuth.getInstance() }


    // --- Notification constants (local only; still storing in SharedPreferences) ---
    private val CHANNEL_ID_GENERAL = "general"
    private val CHANNEL_NAME = "General"
    private val CHANNEL_DESC = "General notifications"

    // Runtime permission launcher for Android 13+ notifications
    private val requestNotifPermission = registerForActivityResult(RequestPermission()) { granted ->
        view?.let { root ->
            val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
            val switchNotifications = root.findViewById<MaterialSwitch>(R.id.switchNotifications)

            if (granted) {
                // Persist ON in SharedPreferences (your requirement)
                prefs.edit().putBoolean("notifications", true).apply()

                // Optional: show a small test notification to confirm it works
                ensureNotificationChannel()
                sendLocalTestNotification()

                Snackbar.make(root, "Notifications enabled", Snackbar.LENGTH_SHORT).show()
            } else {
                // Keep switch OFF if denied
                switchNotifications.isChecked = false
                Snackbar.make(root, "Permission denied. Enable in Settings to receive alerts.", Snackbar.LENGTH_LONG)
                    .setAction("Open Settings") { openAppNotificationSettings() }
                    .show()
            }
        }
    }

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
        val btnSupport          = view.findViewById<MaterialButton>(R.id.btnSupport)



        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        val prefs = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val uid = auth.currentUser?.uid

        // ---- Seed switches ----
        // Biometrics: from encrypted prefs (truth source)
        val deviceSupportsBiometrics = canUseBiometrics()
        switchBiometrics.isEnabled = deviceSupportsBiometrics
        switchBiometrics.isChecked = SecurePrefs.isBiometricEnabled(requireContext())

        // DarkMode + Notifications: from SharedPreferences (your requirement)
        switchDarkMode.isChecked      = prefs.getBoolean("dark_mode", false)
        switchNotifications.isChecked = prefs.getBoolean("notifications", true)

        // Optionally hydrate from Firebase (your existing behavior)
        if (uid != null) {
            ThemeManager.hydrateFromFirebase(requireContext(), uid) {
                switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
            }
        } else {
            Snackbar.make(view, "Not authenticated — using local settings only.", Snackbar.LENGTH_SHORT).show()
        }

        // ---- Support button → default email app with prefilled fields ----
        btnSupport.setOnClickListener {
            val to = "infinityandbeyond.contact@gmail.com"
            val subject = "IBS Driver App Support"
            val body = "Hi team,\n\nI need help with ...\n\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}"

            val uri = android.net.Uri.parse(
                "mailto:${android.net.Uri.encode(to)}" +
                        "?subject=${android.net.Uri.encode(subject)}" +
                        "&body=${android.net.Uri.encode(body)}"
            )
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            try {
                startActivity(Intent.createChooser(intent, "Contact support"))
            } catch (_: android.content.ActivityNotFoundException) {
                Snackbar.make(view, "No email app installed.", Snackbar.LENGTH_LONG).show()
            }
        }

        // ---- Biometrics toggle ----
        switchBiometrics.setOnCheckedChangeListener { _, enabled ->
            if (!deviceSupportsBiometrics) {
                Snackbar.make(view, "Biometrics not available. Enroll in device settings.", Snackbar.LENGTH_LONG)
                    .setAction("Enroll") { launchBiometricEnroll() }
                    .show()
                switchBiometrics.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (enabled) {
                if (!canUseBiometrics()) {
                    launchBiometricEnroll()
                    switchBiometrics.isChecked = false
                    return@setOnCheckedChangeListener
                }
                val hasCreds = !SecurePrefs.getEmail(requireContext()).isNullOrBlank() &&
                        !SecurePrefs.getPassword(requireContext()).isNullOrBlank()
                SecurePrefs.setBiometricEnabled(requireContext(), true)
                if (!hasCreds) {
                    Snackbar.make(view, "Enabled. After your next successful sign-in, biometrics will be offered.", Snackbar.LENGTH_LONG).show()
                } else {
                    Snackbar.make(view, "Biometric login enabled.", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                SecurePrefs.setBiometricEnabled(requireContext(), false)
                Snackbar.make(view, "Biometric login disabled.", Snackbar.LENGTH_SHORT).show()
            }
        }

        // ---- Dark mode toggle ----
        switchDarkMode.setOnCheckedChangeListener { _, enabled ->
            // Persist locally (SharedPreferences)
            prefs.edit().putBoolean("dark_mode", enabled).apply()

            // Persist in Firebase (optional, as you had)
            uid?.let {
                FirebaseDatabase.getInstance().reference
                    .child("users").child(it).child("settings").child("dark_mode")
                    .setValue(enabled)
            }

            // Apply immediately (if you use ThemeManager)
            ThemeManager.setDarkMode(requireContext(), enabled, uid)
        }

        // ---- Notifications toggle (SharedPreferences ONLY) ----
        switchNotifications.setOnCheckedChangeListener @androidx.annotation.RequiresPermission(
            android.Manifest.permission.POST_NOTIFICATIONS
        ) { _, enabled ->
            if (enabled) {
                // On Android 13+, we must have POST_NOTIFICATIONS permission
                if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission()) {
                    // Ask for permission; result handled in launcher
                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnCheckedChangeListener
                }
                // Permission already granted (or pre-13): store ON in SharedPreferences
                prefs.edit().putBoolean("notifications", true).apply()

                // Optional: give immediate feedback with a local test notification
                ensureNotificationChannel()
                sendLocalTestNotification()

                uid?.let {
                    FirebaseDatabase.getInstance().reference
                        .child("users").child(it).child("settings").child("notifications")
                        .setValue(true)
                }
            } else {
                // Store OFF
                prefs.edit().putBoolean("notifications", false).apply()
                uid?.let {
                    FirebaseDatabase.getInstance().reference
                        .child("users").child(it).child("settings").child("notifications")
                        .setValue(false)
                }
                Snackbar.make(view, "Notifications disabled", Snackbar.LENGTH_SHORT).show()
            }
        }

        // ---- Version ----
        txtVersion.text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        // ---- Change password ----
        btnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_changePassword)
        }

        // ---- Sign out ----
        btnSignOut.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Sign out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign out") { _, _ ->
                    auth.signOut()
                    SessionPrefs.clear(requireContext())
                    SecurePrefs.clear(requireContext())
                    startActivity(Intent(requireContext(), Login::class.java))
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ---- Load profile (if signed in) ----
        uid?.let { userId ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val snap = withContext(Dispatchers.IO) {
                        FirebaseDatabase.getInstance()
                            .reference.child("users").child(userId)
                            .get().await()
                    }
                    val profile = snap.getValue(UserProfile::class.java)

                    val fullName = listOfNotNull(profile?.firstName, profile?.lastName)
                        .joinToString(" ").ifEmpty { "Employee" }
                    txtName.text = fullName
                    txtEmail.text = profile?.email ?: auth.currentUser?.email ?: "email@example.com"

                    val url = profile?.profileImageUrl
                    if (!url.isNullOrBlank()) {
                        imgAvatar.load(url) { crossfade(true) }
                    } else {
                        imgAvatar.setImageResource(R.drawable.vector_announcements)
                    }
                } catch (e: Exception) {
                    Snackbar.make(view, "Failed to load profile: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Helpers: Biometrics ---

    private fun canUseBiometrics(): Boolean {
        val authFlags = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        val result = BiometricManager.from(requireContext()).canAuthenticate(authFlags)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun launchBiometricEnroll() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authFlags = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authFlags)
            }
            startActivity(intent)
        } else {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
    }

    // --- Helpers: Notifications (permission + channel + test) ---

    private fun hasNotifPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = NotificationManagerCompat.from(requireContext())
            if (mgr.getNotificationChannel(CHANNEL_ID_GENERAL) == null) {
                val channel = NotificationChannelCompat.Builder(
                    CHANNEL_ID_GENERAL,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                )
                    .setName(CHANNEL_NAME)
                    .setDescription(CHANNEL_DESC)
                    .build()
                mgr.createNotificationChannel(channel)
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun sendLocalTestNotification() {
        // If you don't want any test ping at all, you can delete this method + its callers.
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifPermission()) return

        val notif = NotificationCompat.Builder(requireContext(), CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notifications) // <-- ensure you have a valid monochrome icon
            .setContentTitle("Notifications enabled")
            .setContentText("You’ll receive alerts from now on.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("You’ll receive alerts from now on."))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(requireContext()).notify(1001, notif)
    }

    private fun openAppNotificationSettings() {
        val intent = Intent().apply {
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(intent)
    }
}
