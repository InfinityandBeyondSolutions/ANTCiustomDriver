package com.ibs.ibs_antdrivers.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.ibs.ibs_antdrivers.MainActivity
import com.ibs.ibs_antdrivers.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var vehicleTrackingBtn: ImageButton? = null
    private var settingsBtn: ImageView? = null
    private var confetti: LottieAnimationView? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvTimes: TextView
    private lateinit var btnClock: Button

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // We capture the initial button background tint to tween back/forth
    private var baseBtnTint: Int? = null

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
        confetti = view.findViewById(R.id.confetti)

        baseBtnTint = btnClock.backgroundTintList?.defaultColor
            ?: ContextCompat.getColor(requireContext(), R.color.antyellow)

        // Optional buttons (only wire up if they exist in your layout)
        vehicleTrackingBtn = view.findViewById<ImageButton?>(R.id.vehicleTrackingBtn)?.apply {
            setOnClickListener {
                val act = activity as? MainActivity ?: return@setOnClickListener

                // Spin delight
                animate().rotationBy(360f).setDuration(400L).start()

                // Toggle tracking
                val wasActive = act.isTrackingActive()
                if (wasActive) act.clockOut() else act.clockIn()

                // Immediate optimistic UI, then refresh after activity persists changes
                animateStateChange(activeAfter = !wasActive)
                postDelayed({ refreshUi() }, 800L)
            }
        }

        // If you have a settings icon with id 'settingsIcon', wire it:
        settingsBtn = view.findViewById<ImageView?>(R.id.settingsIcon)?.apply {
            setOnClickListener {
                // Open system Location settings for quick access
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }

        btnClock.setOnClickListener { v ->
            // Haptic + press bounce
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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

            val act = activity as? MainActivity ?: return@setOnClickListener

            val wasActive = act.isTrackingActive()
            if (wasActive) act.clockOut() else act.clockIn()

            // Tiny celebration when clocking in
            if (!wasActive) celebrate()

            // Optimistic animation ahead of data write
            animateStateChange(activeAfter = !wasActive)
            v.postDelayed({ refreshUi() }, 800L)
        }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // Update UI in case permissions/state changed while away
        refreshUi()
    }

    private fun refreshUi() {
        val act = activity as? MainActivity ?: return
        val active = act.isTrackingActive()

        tvStatus.setTextWithFade(if (active) "Status: Online / Tracking" else "Status: Offline")
        btnClock.text = if (active) "Clock Out" else "Clock In"

        val clockInAt = act.getClockInAt()
        if (clockInAt > 0) {
            tvTimes.visibility = View.VISIBLE
            tvTimes.setTextWithFade("Clocked in at: ${timeFmt.format(Date(clockInAt))}")
        } else {
            tvTimes.setTextWithFade("Not clocked in")
            tvTimes.visibility = View.VISIBLE // or View.GONE if you prefer to hide when not clocked in
        }

        // Make sure status is visible once we have data
        if (tvStatus.visibility != View.VISIBLE) tvStatus.visibility = View.VISIBLE

        // Keep button tint in sync with state
        tweenButtonTint(activeAfter = active)
    }

    private fun animateStateChange(activeAfter: Boolean) {
        tvStatus.setTextWithFade(if (activeAfter) "Status: Online / Tracking" else "Status: Offline")

        tvTimes.setTextWithFade(
            if (activeAfter) {
                "Clocked in at: ${timeFmt.format(Date(System.currentTimeMillis()))}"
            } else {
                "Not clocked in"
            }
        )
        tvStatus.visibility = View.VISIBLE
        tvTimes.visibility = View.VISIBLE

        tweenButtonTint(activeAfter)

        // If the quick action is present, add a tiny scale pulse to echo the change
        vehicleTrackingBtn?.animate()
            ?.scaleX(0.96f)?.scaleY(0.96f)
            ?.setDuration(80L)
            ?.withEndAction {
                vehicleTrackingBtn?.animate()
                    ?.scaleX(1f)?.scaleY(1f)
                    ?.setDuration(120L)
                    ?.setInterpolator(OvershootInterpolator(2f))
                    ?.start()
            }?.start()
    }

    private fun tweenButtonTint(activeAfter: Boolean) {
        val current = btnClock.backgroundTintList?.defaultColor ?: baseBtnTint ?: return
        val base = baseBtnTint ?: current

        // When active, lighten the base a bit; when inactive, go back to base
        val target = if (activeAfter) {
            ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), 0.22f)
        } else {
            base
        }

        if (current == target) return

        ValueAnimator.ofObject(ArgbEvaluator(), current, target).apply {
            duration = 220L
            addUpdateListener { anim ->
                val c = anim.animatedValue as Int
                btnClock.backgroundTintList = android.content.res.ColorStateList.valueOf(c)
            }
            start()
        }
    }

    private fun celebrate() {
        // Play confetti if Lottie view is present; otherwise a quick alpha pulse
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

        // Fallback: simple alpha pulse on the clock button
        btnClock.animate()
            .alpha(0.0f).setDuration(80L)
            .withEndAction {
                btnClock.animate().alpha(1f).setDuration(160L).start()
            }.start()
    }

    // ---- Helpers ----

    private fun TextView.setTextWithFade(newText: String, duration: Long = 180L) {
        if (text.toString() == newText) return
        animate().alpha(0f).setDuration(duration).withEndAction {
            text = newText
            animate().alpha(1f).setDuration(duration).start()
        }.start()
    }
}