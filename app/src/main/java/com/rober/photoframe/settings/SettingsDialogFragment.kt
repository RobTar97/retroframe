package com.rober.photoframe.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.rober.photoframe.R

class SettingsDialogFragment : DialogFragment() {

    private lateinit var etInterval: EditText
    private lateinit var cbShuffle: CheckBox
    private lateinit var cbVideos: CheckBox
    private lateinit var cbVideoSound: CheckBox
    private lateinit var etWakeTime: EditText
    private lateinit var etSleepTime: EditText
    private lateinit var cbAlarmEnabled: CheckBox
    private lateinit var etAlarmTime: EditText
    
    var onSettingsChanged: (() -> Unit)? = null
    var onChangeFolderRequested: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etInterval = view.findViewById(R.id.etInterval)
        cbShuffle = view.findViewById(R.id.cbShuffle)
        cbVideos = view.findViewById(R.id.cbVideos)
        cbVideoSound = view.findViewById(R.id.cbVideoSound)
        etWakeTime = view.findViewById(R.id.etWakeTime)
        etSleepTime = view.findViewById(R.id.etSleepTime)
        cbAlarmEnabled = view.findViewById(R.id.cbAlarmEnabled)
        etAlarmTime = view.findViewById(R.id.etAlarmTime)

        loadSettings()

        view.findViewById<Button>(R.id.btnChangeFolder).setOnClickListener {
            onChangeFolderRequested?.invoke()
            // Don't dismiss - let user stay in settings
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dismiss()
        }

        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSettings()
            onSettingsChanged?.invoke()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun loadSettings() {
        etInterval.setText(PhotoframePreferences.slideIntervalSeconds.toString())
        cbShuffle.isChecked = PhotoframePreferences.shuffle
        cbVideos.isChecked = PhotoframePreferences.includeVideos
        cbVideoSound.isChecked = PhotoframePreferences.videoSoundEnabled
        
        etWakeTime.setText(PhotoframePreferences.wakeTime ?: "")
        etSleepTime.setText(PhotoframePreferences.sleepTime ?: "")
        
        // Load alarm settings
        val alarm = com.rober.photoframe.data.AlarmManager.getAlarm()
        cbAlarmEnabled.isChecked = alarm?.enabled ?: false
        if (alarm != null) {
            etAlarmTime.setText(String.format("%02d:%02d", alarm.hour, alarm.minute))
        }
    }

    private fun saveSettings() {
        val interval = etInterval.text.toString().toIntOrNull() ?: 10
        PhotoframePreferences.slideIntervalSeconds = interval.coerceIn(5, 3600)
        PhotoframePreferences.shuffle = cbShuffle.isChecked
        PhotoframePreferences.includeVideos = cbVideos.isChecked
        PhotoframePreferences.videoSoundEnabled = cbVideoSound.isChecked
        
        PhotoframePreferences.wakeTime = etWakeTime.text.toString().trim().takeIf { it.isNotEmpty() }
        PhotoframePreferences.sleepTime = etSleepTime.text.toString().trim().takeIf { it.isNotEmpty() }
        
        try {
            context?.let { ctx ->
                com.rober.photoframe.util.ScreenManager.scheduleAlarms(ctx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Save alarm settings
        if (cbAlarmEnabled.isChecked) {
            val alarmTime = etAlarmTime.text.toString().trim()
            if (alarmTime.isNotEmpty()) {
                try {
                    val parts = alarmTime.split(":")
                    val hour = parts[0].toInt()
                    val minute = parts[1].toInt()
                    val alarm = com.rober.photoframe.data.Alarm(
                        id = "main_alarm",
                        hour = hour,
                        minute = minute,
                        enabled = true
                    )
                    com.rober.photoframe.data.AlarmManager.saveAlarm(alarm)
                    context?.let { ctx ->
                        com.rober.photoframe.alarm.AlarmScheduler.scheduleAlarm(ctx, alarm)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Cancel alarm if disabled
            val alarm = com.rober.photoframe.data.AlarmManager.getAlarm()
            if (alarm != null) {
                context?.let { ctx ->
                    com.rober.photoframe.alarm.AlarmScheduler.cancelAlarm(ctx, alarm)
                }
                com.rober.photoframe.data.AlarmManager.deleteAlarm()
            }
        }
    }
}
