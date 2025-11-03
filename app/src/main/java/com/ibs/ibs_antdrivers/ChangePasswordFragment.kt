package com.ibs.ibs_antdrivers

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ibs.ibs_antdrivers.R
import com.ibs.ibs_antdrivers.security.CryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ChangePasswordFragment : Fragment() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_change_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Back button (make sure your XML has @id/btnBackChangePassword)
        view.findViewById<ImageView?>(R.id.btnBackChangePassword)?.setOnClickListener {
            findNavController().navigateUp()
        }

        val tilCurrent = view.findViewById<TextInputLayout>(R.id.tilCurrent)
        val tilNew = view.findViewById<TextInputLayout>(R.id.tilNew)
        val tilConfirm = view.findViewById<TextInputLayout>(R.id.tilConfirm)
        val edtCurrent = view.findViewById<EditText>(R.id.edtCurrent)
        val edtNew = view.findViewById<EditText>(R.id.edtNew)
        val edtConfirm = view.findViewById<EditText>(R.id.edtConfirm)
        val strengthBar = view.findViewById<LinearProgressIndicator>(R.id.strengthBar)
        val txtStrengthLabel = view.findViewById<TextView>(R.id.txtStrengthLabel)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.progress)
        val btnback = view.findViewById<ImageView>(R.id.btnBackChangePassword)

        // Ensure progress goes 0..100
        strengthBar.max = 100

        btnback.setOnClickListener {
            replaceFragment(SettingsFragment())
        }

        fun scorePassword(pw: String): Pair<Int, String> {
            var score = 0
            if (pw.length >= 8) score++
            if (pw.any { it.isLowerCase() } && pw.any { it.isUpperCase() }) score++
            if (pw.any { it.isDigit() }) score++
            if (pw.any { !it.isLetterOrDigit() }) score++
            val label = arrayOf("Very Weak", "Weak", "Okay", "Good", "Strong")[score]
            return score to label
        }



        edtNew.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val (score, label) = scorePassword(s?.toString().orEmpty())
                strengthBar.progress = score * 25 // 0..100
                txtStrengthLabel.text = label
            }
        })

        fun validate(): Boolean {
            val cur = edtCurrent.text?.toString()?.trim().orEmpty()
            val new = edtNew.text?.toString()?.trim().orEmpty()
            val conf = edtConfirm.text?.toString()?.trim().orEmpty()
            var ok = true
            if (cur.isEmpty()) {
                tilCurrent.error = "Required"; ok = false
            } else tilCurrent.error = null
            if (new.length < 8) {
                tilNew.error = "Min 8 characters"; ok = false
            } else tilNew.error = null
            if (conf != new) {
                tilConfirm.error = "Passwords do not match"; ok = false
            } else tilConfirm.error = null
            return ok
        }

        fun setBusy(b: Boolean) {
            progress.isVisible = b
            btnSave.isEnabled = !b
        }

        btnSave.setOnClickListener {
            if (!validate() || btnSave.isEnabled.not()) return@setOnClickListener
            val user = auth.currentUser ?: run {
                Snackbar.make(view, "Not authenticated", Snackbar.LENGTH_LONG)
                    .show(); return@setOnClickListener
            }
            val email = user.email ?: run {
                Snackbar.make(view, "Missing email", Snackbar.LENGTH_LONG)
                    .show(); return@setOnClickListener
            }
            val uid = user.uid

            val current = edtCurrent.text.toString()
            val newPw = edtNew.text.toString()

            setBusy(true)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val cred = EmailAuthProvider.getCredential(email, current)
                    withContext(Dispatchers.IO) { user.reauthenticate(cred).await() }
                    withContext(Dispatchers.IO) { user.updatePassword(newPw).await() }

                    val cipher = CryptoHelper.encryptToCombinedBase64(newPw)
                    db.child("users").child(uid).child("passwordCipher").setValue(cipher).await()


                    edtCurrent.text = null; edtNew.text = null; edtConfirm.text = null
                    Snackbar.make(view, "Password updated.", Snackbar.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Snackbar.make(view, "Failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                } finally {
                    setBusy(false)
                }
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.main_container, fragment)
            .addToBackStack(null)
            .commit()
    }
}

