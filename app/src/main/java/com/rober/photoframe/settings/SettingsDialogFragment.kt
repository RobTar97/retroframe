package com.rober.photoframe.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.rober.photoframe.R
import com.rober.photoframe.alarm.AlarmScheduler
import com.rober.photoframe.data.Alarm
import com.rober.photoframe.data.AlarmSettings
import com.rober.photoframe.ui.AboutDialogFragment
import com.rober.photoframe.util.ScreenManager

class SettingsDialogFragment : DialogFragment() {

    private lateinit var etInterval: EditText
    private lateinit var cbShuffle: CheckBox
    private lateinit var cbVideos: CheckBox
    private lateinit var cbVideoSound: CheckBox
    private lateinit var cbKeepScreenOn: CheckBox
    private lateinit var rgTransition: RadioGroup
    private lateinit var etWakeTime: EditText
    private lateinit var etSleepTime: EditText
    private lateinit var cbAlarmEnabled: CheckBox
    private lateinit var etAlarmTime: EditText
    private lateinit var cbAutoStart: CheckBox
    private lateinit var exactAlarmWarning: TextView

    var onSettingsSaved: (() -> Unit)? = null
    var onChangeFolderRequested: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.dialog_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etInterval = view.findViewById(R.id.etInterval)
        cbShuffle = view.findViewById(R.id.cbShuffle)
        cbVideos = view.findViewById(R.id.cbVideos)
        cbVideoSound = view.findViewById(R.id.cbVideoSound)
        cbKeepScreenOn = view.findViewById(R.id.cbKeepScreenOn)
        rgTransition = view.findViewById(R.id.rgTransition)
        etWakeTime = view.findViewById(R.id.etWakeTime)
        etSleepTime = view.findViewById(R.id.etSleepTime)
        cbAlarmEnabled = view.findViewById(R.id.cbAlarmEnabled)
        etAlarmTime = view.findViewById(R.id.etAlarmTime)
        cbAutoStart = view.findViewById(R.id.cbAutoStart)
        exactAlarmWarning = view.findViewById(R.id.exactAlarmWarning)

        loadSettings()
        showExactAlarmWarningIfNeeded()

        view.findViewById<Button>(R.id.btnChangeFolder).setOnClickListener {
            onChangeFolderRequested?.invoke()
            // Stays open deliberately: the picker returns here, and dismissing would drop
            // any other edits the user had already made.
        }

        view.findViewById<Button>(R.id.btnAbout).setOnClickListener {
            AboutDialogFragment().show(parentFragmentManager, "about")
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dismiss() }

        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (saveSettings()) {
                onSettingsSaved?.invoke()
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun loadSettings() {
        etInterval.setText(PhotoframePreferences.slideIntervalSeconds.toString())
        cbShuffle.isChecked = PhotoframePreferences.shuffle
        cbVideos.isChecked = PhotoframePreferences.includeVideos
        cbVideoSound.isChecked = PhotoframePreferences.videoSoundEnabled
        cbKeepScreenOn.isChecked = PhotoframePreferences.keepScreenOn
        cbAutoStart.isChecked = PhotoframePreferences.autoStartOnBoot

        rgTransition.check(
            when (PhotoframePreferences.transitionEffect) {
                TransitionEffect.FADE -> R.id.rbFade
                TransitionEffect.SLIDE -> R.id.rbSlide
                TransitionEffect.ZOOM -> R.id.rbZoom
            },
        )

        etWakeTime.setText(PhotoframePreferences.wakeTime.orEmpty())
        etSleepTime.setText(PhotoframePreferences.sleepTime.orEmpty())

        val alarm = AlarmSettings.get()
        cbAlarmEnabled.isChecked = alarm?.enabled == true
        etAlarmTime.setText(
            alarm?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty(),
        )
    }

    /** @return true when the settings were valid and saved. */
    private fun saveSettings(): Boolean {
        // Validate times before writing anything, so a typo cannot leave settings half-applied.
        if (!validateTime(etWakeTime, R.string.error_wake_time)) return false
        if (!validateTime(etSleepTime, R.string.error_sleep_time)) return false
        if (cbAlarmEnabled.isChecked && !validateTime(etAlarmTime, R.string.error_alarm_time, required = true)) {
            return false
        }

        val interval = etInterval.text.toString().toIntOrNull()
            ?: PhotoframePreferences.DEFAULT_INTERVAL_SECONDS

        PhotoframePreferences.slideIntervalSeconds = interval
        PhotoframePreferences.shuffle = cbShuffle.isChecked
        PhotoframePreferences.includeVideos = cbVideos.isChecked
        PhotoframePreferences.videoSoundEnabled = cbVideoSound.isChecked
        PhotoframePreferences.keepScreenOn = cbKeepScreenOn.isChecked
        PhotoframePreferences.autoStartOnBoot = cbAutoStart.isChecked

        PhotoframePreferences.transitionEffect = when (rgTransition.checkedRadioButtonId) {
            R.id.rbSlide -> TransitionEffect.SLIDE
            R.id.rbZoom -> TransitionEffect.ZOOM
            else -> TransitionEffect.FADE
        }

        PhotoframePreferences.wakeTime = etWakeTime.text.toString()
        PhotoframePreferences.sleepTime = etSleepTime.text.toString()

        val context = requireContext()
        ScreenManager.scheduleAlarms(context)
        saveAlarm(context)

        return true
    }

    private fun saveAlarm(context: android.content.Context) {
        val existing = AlarmSettings.get()

        if (!cbAlarmEnabled.isChecked) {
            existing?.let { AlarmScheduler.cancel(context, it) }
            AlarmSettings.clear()
            return
        }

        val minutes = TimeOfDay.parse(etAlarmTime.text.toString())
        if (minutes == PhotoframePreferences.TIME_DISABLED) return

        val alarm = Alarm(hour = minutes / 60, minute = minutes % 60, enabled = true)
        AlarmSettings.save(alarm)
        AlarmScheduler.schedule(context, alarm)
    }

    private fun validateTime(field: EditText, errorRes: Int, required: Boolean = false): Boolean {
        val text = field.text.toString().trim()
        if (text.isEmpty()) {
            if (!required) return true
            field.error = getString(errorRes)
            return false
        }
        if (TimeOfDay.parse(text) == PhotoframePreferences.TIME_DISABLED) {
            field.error = getString(errorRes)
            return false
        }
        return true
    }

    /**
     * On Android 12+ the system can withhold exact alarms. The app now falls back to inexact
     * scheduling rather than failing silently, but the user deserves to know their 07:00 wake
     * may land at 07:01 — and how to fix it.
     */
    private fun showExactAlarmWarningIfNeeded() {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !ScreenManager.canScheduleExact(requireContext())

        exactAlarmWarning.visibility = if (needed) View.VISIBLE else View.GONE
        if (!needed) return

        exactAlarmWarning.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@setOnClickListener
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${requireContext().packageName}")),
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.error_open_settings, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted the permission and come back.
        showExactAlarmWarningIfNeeded()
    }
}
