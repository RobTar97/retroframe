package com.rober.photoframe.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.rober.photoframe.R

/**
 * Shown once, before the system folder picker.
 *
 * Without it the app's very first action is to throw a stranger into Android's file browser
 * with no explanation. Worse, on Android 11 and newer the picker *refuses* the two places
 * people try first — the top level of storage and the Downloads folder — with only
 * "To protect your privacy, choose another folder" and no hint as to which folder would be
 * acceptable. That is a dead end an ordinary person has no way to reason their way out of.
 *
 * So: say what is about to happen, and say which folder to pick.
 */
class WelcomeDialogFragment : DialogFragment() {

    /** Invoked when the user is ready to choose a folder. */
    var onContinue: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.dialog_welcome, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        view.findViewById<View>(R.id.btnWelcomeContinue).setOnClickListener {
            dismiss()
            onContinue?.invoke()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        const val TAG = "welcome"
    }
}
