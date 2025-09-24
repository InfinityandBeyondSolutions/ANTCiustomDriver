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
import android.widget.ProgressBar
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

    private lateinit var btnClockIn: Button
    private lateinit var btnClockOut: Button

    private lateinit var progressClockIn: ProgressBar
    private lateinit var progressClockOut: ProgressBar


    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
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

        btnClockIn = view.findViewById(R.id.btnClockIn)
        btnClockOut = view.findViewById(R.id.btnClockOut)

        confetti = view.findViewById(R.id.confetti)

        progressClockIn = view.findViewById(R.id.progressClockIn)
        progressClockOut = view.findViewById(R.id.progressClockOut)


        baseBtnTint = btnClockIn.backgroundTintList?.defaultColor
            ?: ContextCompat.getColor(requireContext(), R.color.antyellow)

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

        // Settings shortcut
        settingsBtn = view.findViewById<ImageView?>(R.id.settingsIcon)?.apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }

        btnClockIn.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            animateButtonPress(v)

            val act = activity as? MainActivity ?: return@setOnClickListener
            if (!act.isTrackingActive()) {
                // Hide button, show spinner
                btnClockIn.visibility = View.GONE
                progressClockIn.visibility = View.VISIBLE

                act.clockIn()
                celebrate()

                // Swap UI right away
                animateStateChange(true)

                // Delay longer (2s to be safe)
                v.postDelayed({
                    refreshUi()
                    progressClockIn.visibility = View.GONE
                    // Button will be restored by refreshUi()
                }, 2000L)
            }
        }

        btnClockOut.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            animateButtonPress(v)

            val act = activity as? MainActivity ?: return@setOnClickListener
            if (act.isTrackingActive()) {
                // Hide button, show spinner
                btnClockOut.visibility = View.GONE
                progressClockOut.visibility = View.VISIBLE

                act.clockOut()

                // Swap UI right away
                animateStateChange(false)

                // Delay longer (2s to be safe)
                v.postDelayed({
                    refreshUi()
                    progressClockOut.visibility = View.GONE
                    // Button will be restored by refreshUi()
                }, 2000L)
            }
        }

    }
        override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val act = activity as? MainActivity ?: return
        val active = act.isTrackingActive()

        tvStatus.setTextWithFade(if (active) "Status: Online / Tracking" else "Status: Offline")

        val clockInAt = act.getClockInAt()
        if (clockInAt > 0) {
            tvTimes.visibility = View.VISIBLE
            tvTimes.setTextWithFade("Clocked in at: ${timeFmt.format(Date(clockInAt))}")
        } else {
            tvTimes.setTextWithFade("Not clocked in")
            tvTimes.visibility = View.VISIBLE
        }

        // Only one button visible at a time
        btnClockIn.visibility = if (active) View.GONE else View.VISIBLE
        btnClockOut.visibility = if (active) View.VISIBLE else View.GONE

        tweenButtonTint(active)
    }

    private fun animateStateChange(activeAfter: Boolean) {
        tvStatus.setTextWithFade(if (activeAfter) "Status: Online / Tracking" else "Status: Offline")
        tvTimes.setTextWithFade(
            if (activeAfter) "Clocked in at: ${timeFmt.format(Date(System.currentTimeMillis()))}"
            else "Not clocked in"
        )

        btnClockIn.visibility = if (activeAfter) View.GONE else View.VISIBLE
        btnClockOut.visibility = if (activeAfter) View.VISIBLE else View.GONE

        tweenButtonTint(activeAfter)

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
        val current = btnClockIn.backgroundTintList?.defaultColor ?: baseBtnTint ?: return
        val base = baseBtnTint ?: current

        val target = if (activeAfter) {
            ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), 0.22f)
        } else base

        if (current == target) return

        ValueAnimator.ofObject(ArgbEvaluator(), current, target).apply {
            duration = 220L
            addUpdateListener { anim ->
                val c = anim.animatedValue as Int
                if (btnClockIn.visibility == View.VISIBLE) {
                    btnClockIn.backgroundTintList = android.content.res.ColorStateList.valueOf(c)
                } else {
                    btnClockOut.backgroundTintList = android.content.res.ColorStateList.valueOf(c)
                }
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
}
