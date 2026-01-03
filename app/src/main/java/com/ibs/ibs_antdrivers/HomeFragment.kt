package com.ibs.ibs_antdrivers

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import coil.transform.CircleCropTransformation
import com.airbnb.lottie.LottieAnimationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.MainActivity
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.ui.GroupListFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    private var vehicleTrackingBtn: ImageButton? = null
    private var confetti: LottieAnimationView? = null
    private lateinit var btnMsg: ImageButton

    private lateinit var tvStatus: TextView
    private lateinit var tvTimes: TextView
    private lateinit var tvTimeElapsed: TextView

    private lateinit var btnClockIn: Button
    private lateinit var btnClockOut: Button

    private lateinit var progressClockIn: ProgressBar
    private lateinit var progressClockOut: ProgressBar

    private lateinit var llstatus: LinearLayout
    private lateinit var lldate: LinearLayout
    private lateinit var lltime: LinearLayout

    private lateinit var settingsBtn: ImageView
    private lateinit var btnPhonebook: ImageView

    // New enhanced UI elements
    private lateinit var userName: TextView
    private lateinit var greetingBadge: TextView
    private lateinit var currentDate: TextView
    private lateinit var currentTime: TextView
    private lateinit var userAvatar: ImageView
    private var clockStatusBadge: TextView? = null

    private val auth by lazy { FirebaseAuth.getInstance() }

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    private val timeDisplayFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private var baseBtnTint: Int? = null

    // --- NEW: ticker to refresh "elapsed since clock-in"
    private val uiHandler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val act = activity as? MainActivity ?: return
            if (act.isTrackingActive()) {
                updateTimesUi(act.getClockInAt()) // keep elapsed fresh
            }
            // Also update current time display
            updateDateTimeDisplay()
            uiHandler.postDelayed(this, TimeUnit.MINUTES.toMillis(1))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStatus = view.findViewById(R.id.tvStatus)
        tvTimes = view.findViewById(R.id.tvClockTimes)
        tvTimeElapsed = view.findViewById(R.id.tvTimeElapsed)

        btnClockIn = view.findViewById(R.id.btnClockIn)
        btnClockOut = view.findViewById(R.id.btnClockOut)
        btnMsg = view.findViewById(R.id.ibGoToMsg)

        confetti = view.findViewById(R.id.confetti)

        progressClockIn = view.findViewById(R.id.progressClockIn)
        progressClockOut = view.findViewById(R.id.progressClockOut)

        settingsBtn = view.findViewById(R.id.settingsIcon)
        btnPhonebook= view.findViewById(R.id.btnPhonebook)

        // New enhanced UI elements
        userName = view.findViewById(R.id.userName)
        greetingBadge = view.findViewById(R.id.greetingBadge)
        currentDate = view.findViewById(R.id.currentDate)
        currentTime = view.findViewById(R.id.currentTime)
        userAvatar = view.findViewById(R.id.userAvatar)
        clockStatusBadge = view.findViewById(R.id.clockStatusBadge)

        llstatus = view.findViewById(R.id.llstatus)
        lldate   = view.findViewById(R.id.lldate)
        lltime   = view.findViewById(R.id.lltime)

        llstatus.alpha = 1f
        llstatus.visibility = View.VISIBLE
        lldate.alpha = 1f
        lldate.visibility = View.VISIBLE
        lltime.alpha = 1f
        lltime.visibility = View.VISIBLE

        baseBtnTint = btnClockIn.backgroundTintList?.defaultColor
            ?: ContextCompat.getColor(requireContext(), R.color.antyellow)

        // Initialize enhanced UI
        updateGreeting()
        updateDateTimeDisplay()
        loadUserProfile()

        // Vehicle tracking quick toggle
        vehicleTrackingBtn = view.findViewById<ImageButton?>(R.id.vehicleTrackingBtn)?.apply {
            setOnClickListener {
                val act = activity as? MainActivity ?: return@setOnClickListener
                animate().rotationBy(360f).setDuration(400L).start()

                val wasActive = act.isTrackingActive()
                if (wasActive) act.clockOut() else act.clockIn()

                animateStateChange(!wasActive)
                refreshUi()
            }
        }

        settingsBtn.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_settings)
        }

        btnPhonebook.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_adminPhonebook)
        }


        //GOING TO CHAT NAVIGATION DOWN HERE
        btnMsg.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_groupList)
        }

        btnClockIn.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            animateButtonPress(v)

            val act = activity as? MainActivity ?: return@setOnClickListener
            if (!act.isTrackingActive()) {
                btnClockIn.visibility = View.GONE
                progressClockIn.visibility = View.VISIBLE

                act.clockIn()
                celebrate()
                animateStateChange(true)

                v.postDelayed({
                    refreshUi()
                    progressClockIn.visibility = View.GONE
                }, 2000L)
            }
        }

        btnClockOut.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            animateButtonPress(v)

            val act = activity as? MainActivity ?: return@setOnClickListener
            if (act.isTrackingActive()) {
                btnClockOut.visibility = View.GONE
                progressClockOut.visibility = View.VISIBLE

                act.clockOut()
                animateStateChange(false)

                v.postDelayed({
                    refreshUi()
                    progressClockOut.visibility = View.GONE
                }, 2000L)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        updateGreeting()
        updateDateTimeDisplay()

        // Start ticker for time updates
        uiHandler.postDelayed(ticker, TimeUnit.MINUTES.toMillis(1))
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacksAndMessages(null)
    }

    private fun refreshUi() {
        val act = activity as? MainActivity ?: return
        val active = act.isTrackingActive()

        // Status chip + time pill
        updateStatusUi(active)
        updateTimesUi(act.getClockInAt())

        // Only one button visible at a time
        btnClockIn.visibility = if (active) View.GONE else View.VISIBLE
        btnClockOut.visibility = if (active) View.VISIBLE else View.GONE

        tweenButtonTint(active)
    }

    private fun updateStatusUi(active: Boolean) {
        tvStatus.visibility = View.VISIBLE
        tvStatus.setTextWithFade(if (active) "Online / Tracking" else "Offline")

        // Background chip color
        tvStatus.setBackgroundResource(
            if (active) R.drawable.bg_chip_status_online else R.drawable.bg_chip_status_offline
        )
        // Optional: ensure legible text color (your palette is fine, but this is safe)
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.navyblue))
    }

    private fun updateTimesUi(clockInAt: Long) {
        tvTimes.visibility = View.VISIBLE
        tvTimeElapsed.visibility = View.VISIBLE
        if (clockInAt > 0) {
            val at = timeFmt.format(Date(clockInAt))
            val elapsed = humanElapsed(clockInAt, System.currentTimeMillis())
            tvTimes.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_time)
            tvTimes.setTextWithFade("$at")
            tvTimeElapsed.setTextWithFade("$elapsed")
        } else {
            tvTimes.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_time)
            tvTimes.setTextWithFade("Not clocked in")
            tvTimeElapsed.setTextWithFade("0 minutes")
        }
    }

    private fun humanElapsed(startMs: Long, nowMs: Long): String {
        val d = nowMs - startMs
        if (d < 0) return ""
        val h = TimeUnit.MILLISECONDS.toHours(d)
        val m = TimeUnit.MILLISECONDS.toMinutes(d) % 60
        return when {
            h <= 0 && m < 1 -> "just now"
            h <= 0          -> "${m}m elapsed"
            else            -> "${h}h ${m}m elapsed"
        }
    }

    private fun animateStateChange(activeAfter: Boolean) {
        // helpers
        fun fadeOut(v: View, onEnd: (() -> Unit)? = null) {
            if (v.visibility == View.VISIBLE && v.alpha > 0f) {
                v.animate().alpha(0f).setDuration(90L).withEndAction {
                    v.visibility = View.INVISIBLE   // use GONE if you want reflow
                    onEnd?.invoke()
                }.start()
            } else {
                v.visibility = View.INVISIBLE
                v.alpha = 0f
                onEnd?.invoke()
            }
        }
        fun fadeIn(v: View) {
            v.visibility = View.VISIBLE
            v.animate().alpha(1f).setDuration(140L).start()
        }

        // 1) fade both status/time containers out BEFORE the button animation
        var pending = 2
        val afterBothFaded = {
            pending--
            if (pending == 0) {
                // 2) update text + buttons while hidden
                updateStatusUi(activeAfter)
                tvTimes.setTextWithFade(
                    if (activeAfter)
                        "Clocked in: ${timeFmt.format(Date(System.currentTimeMillis()))} • just now"
                    else
                        "Not clocked in"
                )
                btnClockIn.visibility = if (activeAfter) View.GONE else View.VISIBLE
                btnClockOut.visibility = if (activeAfter) View.VISIBLE else View.GONE
                tweenButtonTint(activeAfter)

                // 3) wiggle the quick toggle; when done, bring the layouts back
                val btn = vehicleTrackingBtn
                if (btn != null) {
                    btn.animate()
                        .scaleX(0.96f).scaleY(0.96f)
                        .setDuration(80L)
                        .withEndAction {
                            btn.animate()
                                .scaleX(1f).scaleY(1f)
                                .setDuration(120L)
                                .setInterpolator(OvershootInterpolator(2f))
                                .withEndAction {
                                    // 4) fade them back in immediately after wiggle
                                    fadeIn(llstatus)
                                    fadeIn(lldate)
                                    fadeIn(lltime)
                                }
                                .start()
                        }
                        .start()
                } else {
                    // fallback if the button isn't present
                    fadeIn(llstatus)
                    fadeIn(lldate)
                    fadeIn(lldate)
                }
            }
        }

        fadeOut(llstatus, afterBothFaded)
        fadeOut(lldate, afterBothFaded)
        fadeOut(lltime, afterBothFaded)
    }


    private fun tweenButtonTint(activeAfter: Boolean) {
        val current = (if (btnClockIn.visibility == View.VISIBLE)
            btnClockIn.backgroundTintList?.defaultColor
        else
            btnClockOut.backgroundTintList?.defaultColor
                ) ?: baseBtnTint ?: return

        val base = baseBtnTint ?: current
        val target = if (activeAfter) {
            ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), 0.22f)
        } else base

        if (current == target) return

        ValueAnimator.ofObject(ArgbEvaluator(), current, target).apply {
            duration = 220L
            addUpdateListener { anim ->
                val c = anim.animatedValue as Int
                val list = ColorStateList.valueOf(c)
                // Set both to keep them in sync when visibility swaps
                btnClockIn.backgroundTintList = list
                btnClockOut.backgroundTintList = list
            }
            start()
        }
    }

    private fun celebrate() {
        val lottie = confetti
        if (lottie != null) {
            lottie.visibility = View.VISIBLE
            lottie.progress = 0f
            lottie.playAnimation()
            lottie.addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lottie.visibility = View.GONE
                    lottie.removeAllAnimatorListeners()
                }
            })
            return
        }

        val visibleBtn = if (btnClockIn.visibility == View.VISIBLE) btnClockIn else btnClockOut
        visibleBtn.animate()
            .alpha(0.0f).setDuration(80L)
            .withEndAction {
                visibleBtn.animate().alpha(1f).setDuration(160L).start()
            }.start()
    }

    private fun animateButtonPress(v: View) {
        v.animate()
            .scaleX(0.96f).scaleY(0.96f)
            .setDuration(80L)
            .withEndAction {
                v.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(120L)
                    .setInterpolator(OvershootInterpolator(2f))
                    .start()
            }
            .start()
    }

    private fun TextView.setTextWithFade(newText: String, duration: Long = 180L) {
        if (text.toString() == newText) return
        animate().alpha(0f).setDuration(duration).withEndAction {
            text = newText
            animate().alpha(1f).setDuration(duration).start()
        }.start()
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENHANCED UI FUNCTIONS - Greeting, Date/Time, User Profile
    // ═══════════════════════════════════════════════════════════════════════════

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (greeting, emoji) = when {
            hour < 5 -> "Good Night" to "🌙"
            hour < 12 -> "Good Morning" to "☀️"
            hour < 17 -> "Good Afternoon" to "🌤️"
            hour < 21 -> "Good Evening" to "🌆"
            else -> "Good Night" to "🌙"
        }
        greetingBadge.text = "$greeting $emoji"
    }

    private fun updateDateTimeDisplay() {
        val now = Date()
        currentDate.text = dateFmt.format(now)
        currentTime.text = timeDisplayFmt.format(now)

        // Update clock status badge based on tracking state
        val act = activity as? MainActivity
        val isActive = act?.isTrackingActive() == true
        clockStatusBadge?.text = if (isActive) "Active" else "Ready"
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) {
                    FirebaseDatabase.getInstance()
                        .reference.child("users").child(uid)
                        .get().await()
                }
                val profile = snap.getValue(UserProfile::class.java)

                // Get first name only for cleaner display
                val firstName = profile?.firstName?.trim()?.split(" ")?.firstOrNull()
                    ?: profile?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: "Driver"

                userName.text = firstName

                // Load profile image if available
                val imageUrl = profile?.profileImageUrl
                if (!imageUrl.isNullOrBlank()) {
                    userAvatar.load(imageUrl) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.ic_user_avatar)
                        error(R.drawable.ic_user_avatar)
                    }
                } else {
                    userAvatar.setImageResource(R.drawable.ic_user_avatar)
                }
            } catch (e: Exception) {
                // Fallback to email-based name or default
                val email = auth.currentUser?.email
                val fallbackName = email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Driver"
                userName.text = fallbackName
            }
        }
    }
}
