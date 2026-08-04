package com.rober.photoframe.settings

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.rober.photoframe.R
import com.rober.photoframe.data.Alarm
import com.rober.photoframe.data.AlarmSettings
import com.rober.photoframe.data.FolderNames
import com.rober.photoframe.schedule.AlarmScheduler
import com.rober.photoframe.schedule.DailySchedule
import com.rober.photoframe.ui.AboutDialogFragment

class SettingsDialogFragment : DialogFragment() {
    private lateinit var etInterval: EditText
    private lateinit var cbShuffle: CheckBox
    private lateinit var cbSubfolders: CheckBox
    private lateinit var cbVideos: CheckBox
    private lateinit var cbVideoSound: CheckBox
    private lateinit var cbKeepScreenOn: CheckBox
    private lateinit var cbBurnIn: CheckBox
    private lateinit var rgTransition: RadioGroup
    private lateinit var etWakeTime: EditText
    private lateinit var etSleepTime: EditText
    private lateinit var sbNightBrightness: SeekBar
    private lateinit var nightBrightnessLabel: TextView
    private lateinit var cbAlarmEnabled: CheckBox
    private lateinit var etAlarmTime: EditText
    private lateinit var cbAutoStart: CheckBox
    private lateinit var currentFolder: TextView
    private lateinit var exactAlarmWarning: TextView

    var onSettingsSaved: (() -> Unit)? = null
    var onChangeFolderRequested: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.dialog_settings, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        etInterval = view.findViewById(R.id.etInterval)
        cbShuffle = view.findViewById(R.id.cbShuffle)
        cbSubfolders = view.findViewById(R.id.cbSubfolders)
        cbVideos = view.findViewById(R.id.cbVideos)
        cbVideoSound = view.findViewById(R.id.cbVideoSound)
        cbKeepScreenOn = view.findViewById(R.id.cbKeepScreenOn)
        cbBurnIn = view.findViewById(R.id.cbBurnIn)
        rgTransition = view.findViewById(R.id.rgTransition)
        etWakeTime = view.findViewById(R.id.etWakeTime)
        etSleepTime = view.findViewById(R.id.etSleepTime)
        sbNightBrightness = view.findViewById(R.id.sbNightBrightness)
        nightBrightnessLabel = view.findViewById(R.id.nightBrightnessLabel)
        cbAlarmEnabled = view.findViewById(R.id.cbAlarmEnabled)
        etAlarmTime = view.findViewById(R.id.etAlarmTime)
        cbAutoStart = view.findViewById(R.id.cbAutoStart)
        currentFolder = view.findViewById(R.id.currentFolder)
        exactAlarmWarning = view.findViewById(R.id.exactAlarmWarning)

        setUpTimePicker(etWakeTime, defaultMinutes = DEFAULT_WAKE_MINUTES)
        setUpTimePicker(etSleepTime, defaultMinutes = DEFAULT_SLEEP_MINUTES)
        setUpTimePicker(etAlarmTime, defaultMinutes = DEFAULT_WAKE_MINUTES)
        setUpBrightnessSlider()

        loadSettings()
        showCurrentFolder()
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
        etInterval.setText(
            PhotoframePreferences.slideIntervalSeconds.toString(),
            TextView.BufferType.EDITABLE,
        )
        cbShuffle.isChecked = PhotoframePreferences.shuffle
        cbSubfolders.isChecked = PhotoframePreferences.includeSubfolders
        cbVideos.isChecked = PhotoframePreferences.includeVideos
        cbVideoSound.isChecked = PhotoframePreferences.videoSoundEnabled
        cbKeepScreenOn.isChecked = PhotoframePreferences.keepScreenOn
        cbBurnIn.isChecked = PhotoframePreferences.burnInProtection
        cbAutoStart.isChecked = PhotoframePreferences.autoStartOnBoot

        val brightness = PhotoframePreferences.nightBrightnessPercent
        sbNightBrightness.progress =
            if (brightness == PhotoframePreferences.BRIGHTNESS_SYSTEM) 0 else brightness
        updateBrightnessLabel(sbNightBrightness.progress)

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
        if (cbAlarmEnabled.isChecked &&
            !validateTime(etAlarmTime, R.string.error_alarm_time, required = true)
        ) {
            return false
        }

        val interval =
            etInterval.text.toString().toIntOrNull()
                ?: PhotoframePreferences.DEFAULT_INTERVAL_SECONDS

        PhotoframePreferences.slideIntervalSeconds = interval
        PhotoframePreferences.shuffle = cbShuffle.isChecked
        PhotoframePreferences.includeSubfolders = cbSubfolders.isChecked
        PhotoframePreferences.includeVideos = cbVideos.isChecked
        PhotoframePreferences.videoSoundEnabled = cbVideoSound.isChecked
        PhotoframePreferences.keepScreenOn = cbKeepScreenOn.isChecked
        PhotoframePreferences.burnInProtection = cbBurnIn.isChecked
        PhotoframePreferences.autoStartOnBoot = cbAutoStart.isChecked
        PhotoframePreferences.nightBrightnessPercent = sbNightBrightness.progress

        PhotoframePreferences.transitionEffect =
            when (rgTransition.checkedRadioButtonId) {
                R.id.rbSlide -> TransitionEffect.SLIDE
                R.id.rbZoom -> TransitionEffect.ZOOM
                else -> TransitionEffect.FADE
            }

        PhotoframePreferences.wakeTime = etWakeTime.text.toString()
        PhotoframePreferences.sleepTime = etSleepTime.text.toString()

        val context = requireContext()
        DailySchedule.scheduleAlarms(context)
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

    // -------------------------------------------------------------- time pickers

    /**
     * Turns a time field into something you tap rather than type into.
     *
     * Free text was a genuinely poor fit here. The target device is a tablet on a shelf, often
     * operated with a thumb, and the field wanted exactly `HH:mm` — so `7:00`, `7am` and `0700`
     * were all rejected with an error message, and the on-screen keyboard covered the Save
     * button while the user worked out why. The system picker cannot produce an invalid time,
     * and it honours the device's 12- or 24-hour preference for free.
     *
     * Validation stays in place regardless: a value can still arrive from an older install's
     * saved preferences.
     */
    private fun setUpTimePicker(
        field: EditText,
        defaultMinutes: Int,
    ) {
        field.isFocusable = false
        field.isFocusableInTouchMode = false
        field.isCursorVisible = false
        // Removing the key listener is what actually keeps the soft keyboard away; a
        // non-focusable EditText will still raise it on some old vendor keyboards.
        field.keyListener = null
        field.setOnClickListener { showTimePicker(field, defaultMinutes) }
    }

    private fun showTimePicker(
        field: EditText,
        defaultMinutes: Int,
    ) {
        val existing = TimeOfDay.parse(field.text.toString())
        val start =
            if (existing == PhotoframePreferences.TIME_DISABLED) defaultMinutes else existing

        val picker =
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    field.setText(TimeOfDay.format(hour * 60 + minute).orEmpty())
                    field.error = null
                },
                start / 60,
                start % 60,
                DateFormat.is24HourFormat(requireContext()),
            )

        // Every one of these times is optional, and a picker with no way out but a valid time
        // would make "no schedule" unreachable once a schedule had been set.
        picker.setButton(
            DialogInterface.BUTTON_NEUTRAL,
            getString(R.string.time_clear),
        ) { _, _ ->
            field.setText("")
            field.error = null
        }
        picker.show()
    }

    // ---------------------------------------------------------- night brightness

    private fun setUpBrightnessSlider() {
        sbNightBrightness.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean,
                ) = updateBrightnessLabel(progress)

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            },
        )
    }

    private fun updateBrightnessLabel(progress: Int) {
        nightBrightnessLabel.text =
            if (progress < PhotoframePreferences.MIN_NIGHT_BRIGHTNESS) {
                getString(R.string.settings_night_brightness_off)
            } else {
                getString(R.string.settings_night_brightness, progress)
            }
    }

    // ------------------------------------------------------------- chosen folder

    /**
     * Names the folder the slideshow is reading, so "why am I seeing these photos?" has an
     * answer without going back through the picker.
     */
    private fun showCurrentFolder() {
        val uriString = PhotoframePreferences.galleryUriString
        if (uriString.isNullOrEmpty()) {
            currentFolder.setText(R.string.settings_current_folder_unknown)
            return
        }

        val documentId =
            try {
                DocumentsContract.getTreeDocumentId(uriString.toUri())
            } catch (e: IllegalArgumentException) {
                null
            }

        val name = FolderNames.fromTreeDocumentId(documentId)
        currentFolder.text =
            if (name == null) {
                getString(R.string.settings_current_folder_whole_volume)
            } else {
                getString(R.string.settings_current_folder, name)
            }
    }

    private fun validateTime(
        field: EditText,
        errorRes: Int,
        required: Boolean = false,
    ): Boolean {
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
        // onResume can fire while the view is gone (returning from the system settings
        // screen as the dialog is being dismissed), and the fields are lateinit.
        if (view == null || !isAdded) return

        val needed =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !DailySchedule.canScheduleExact(requireContext())

        exactAlarmWarning.visibility = if (needed) View.VISIBLE else View.GONE
        if (!needed) return

        exactAlarmWarning.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@setOnClickListener
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData("package:${requireContext().packageName}".toUri()),
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
        // Or picked a different folder — the dialog stays open behind the picker.
        showCurrentFolder()
    }

    private companion object {
        /** Where the pickers open when a field is blank. */
        const val DEFAULT_WAKE_MINUTES = 7 * 60
        const val DEFAULT_SLEEP_MINUTES = 22 * 60
    }
}
