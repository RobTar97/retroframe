package com.rober.photoframe.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.rober.photoframe.R

/**
 * About and licences.
 *
 * This exists for a legal reason, not a decorative one. GPL-3.0 §4–5 require that anyone
 * who receives a binary also receives the licence terms and a route to the source. The
 * release notes on GitHub say so, but somebody who was handed the APK on an SD card has
 * never seen those — the installed app is the only thing they have.
 *
 * It is also what F-Droid and Play both expect to find.
 */
class AboutDialogFragment : DialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.dialog_about, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version, versionName(), versionCode())

        view.findViewById<Button>(R.id.btnSource).setOnClickListener { open(SOURCE_URL) }
        view.findViewById<Button>(R.id.btnLicense).setOnClickListener { open(LICENSE_URL) }
        view.findViewById<Button>(R.id.btnPrivacy).setOnClickListener { open(PRIVACY_URL) }
        view.findViewById<Button>(R.id.btnAboutClose).setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    /**
     * Opens a URL in whatever browser exists. This needs no INTERNET permission — the app is
     * handing the address to another app, not fetching it. That distinction is the reason
     * RetroFrame can still claim it has no network access.
     */
    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            // A stripped-down tablet may genuinely have no browser. The URLs are also
            // printed in the dialog, so there is still a way to reach them.
            Toast.makeText(requireContext(), R.string.about_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun packageInfo(): PackageInfo? =
        try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        } catch (e: Exception) {
            null
        }

    private fun versionName(): String = packageInfo()?.versionName ?: "?"

    @Suppress("DEPRECATION") // longVersionCode is API 28+; this app supports 22.
    private fun versionCode(): Long {
        val info = packageInfo() ?: return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }

    companion object {
        const val SOURCE_URL = "https://github.com/RobTar97/retroframe"
        const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

        // Play and F-Droid both want a reachable privacy policy, and someone handed the APK
        // on an SD card has no other route to it.
        const val PRIVACY_URL = "https://github.com/RobTar97/retroframe/blob/main/PRIVACY.md"
    }
}
