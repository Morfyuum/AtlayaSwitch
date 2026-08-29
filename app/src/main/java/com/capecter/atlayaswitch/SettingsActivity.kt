package com.capecter.atlayaswitch

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.Button
import android.widget.PopupMenu
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import rikka.shizuku.Shizuku
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Zentrale Einstellungen von AtlayaSwitch: Zielprofil wählen. Erreichbar über
 * die "App-Info"-Seite der Systemeinstellungen (Intent-Filter
 * ACTION_APPLICATION_PREFERENCES), damit im Alltag kein eigenes, auffälliges
 * Menü nötig ist.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var recyclerView: RecyclerView
    private lateinit var targetProfileWarning: View
    private lateinit var targetProfileWarningText: TextView
    private lateinit var targetProfileWarningButton: Button
    private lateinit var switchModeGroup: RadioGroup
    private lateinit var languageButton: Button
    private lateinit var helpButton: Button
    private lateinit var shizukuBanner: View
    private lateinit var shizukuStatusText: TextView
    private lateinit var openShizukuButton: Button
    private lateinit var switchProfileButton: Button
    private lateinit var installedVersionText: TextView
    private lateinit var updateStatusText: TextView
    private lateinit var updateCheckButton: Button
    private lateinit var updateDownloadButton: Button
    private lateinit var updateAutoSwitch: Switch
    private lateinit var updateBackgroundSwitch: Switch
    private lateinit var supportContactButton: Button
    private lateinit var backButton: Button

    private var profileAdapter: ProfileAdapter? = null
    private var latestUpdateUrl: String? = null
    private var lastShizukuStateRefreshMs = 0L

    /** Ergebnis der Laufzeit-Abfrage von POST_NOTIFICATIONS (nur Android 13+/API 33) beim
     * Aktivieren des Hintergrund-Update-Checks - siehe updateBackgroundSwitch weiter unten. */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                schedulePeriodicUpdateCheck()
            } else {
                Toast.makeText(this, "Ohne Benachrichtigungs-Berechtigung kein Hintergrund-Update-Hinweis möglich.", Toast.LENGTH_LONG).show()
                updateBackgroundSwitch.isChecked = false
                prefs.edit().putBoolean(KEY_BACKGROUND_UPDATE_CHECK, false).apply()
            }
        }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuUtils.REQUEST_CODE_PERMISSION) {
            if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                loadProfiles()
            } else {
                Toast.makeText(this, "Shizuku-Berechtigung verweigert.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences("atlaya_switch", MODE_PRIVATE)

        languageButton = findViewById(R.id.language_button)
        helpButton = findViewById(R.id.help_button)
        languageButton.text = currentLanguageTag().uppercase(Locale.ROOT)
        languageButton.setOnClickListener { showLanguageMenu(it) }
        helpButton.setOnClickListener { showHelpDialog() }

        recyclerView = findViewById(R.id.profile_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.isNestedScrollingEnabled = false

        targetProfileWarning = findViewById(R.id.target_profile_warning)
        targetProfileWarningText = findViewById(R.id.target_profile_warning_text)
        targetProfileWarningButton = findViewById(R.id.target_profile_warning_button)

        switchModeGroup = findViewById(R.id.switch_mode_group)
        val savedSwitchMode = prefs.getString(KEY_SWITCH_MODE, SWITCH_MODE_END_SESSION)
        switchModeGroup.check(
            if (savedSwitchMode == SWITCH_MODE_END_SESSION) R.id.switch_mode_end_session
            else R.id.switch_mode_switch_only
        )
        switchModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val endSession = checkedId == R.id.switch_mode_end_session
            prefs.edit()
                .putString(KEY_SWITCH_MODE, if (endSession) SWITCH_MODE_END_SESSION else SWITCH_MODE_SWITCH_ONLY)
                .apply()
        }

        shizukuBanner = findViewById(R.id.shizuku_banner)
        shizukuStatusText = findViewById(R.id.shizuku_status_text)
        openShizukuButton = findViewById(R.id.shizuku_open_button)
        switchProfileButton = findViewById(R.id.shizuku_switch_profile_button)
        switchProfileButton.setOnClickListener { ShizukuUtils.openUserSettings(this) }

        installedVersionText = findViewById(R.id.installed_version_text)
        installedVersionText.text = getString(
            R.string.settings_installed_version,
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        )

        updateStatusText = findViewById(R.id.update_status_text)
        updateCheckButton = findViewById(R.id.update_check_button)
        updateDownloadButton = findViewById(R.id.update_download_button)
        updateAutoSwitch = findViewById(R.id.update_auto_switch)

        updateCheckButton.setOnClickListener { checkForUpdates() }
        updateDownloadButton.setOnClickListener {
            latestUpdateUrl?.let { url -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        updateAutoSwitch.isChecked = prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, false)
        updateAutoSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_AUTO_UPDATE_CHECK, checked).apply()
        }

        updateBackgroundSwitch = findViewById(R.id.update_background_switch)
        updateBackgroundSwitch.isChecked = prefs.getBoolean(KEY_BACKGROUND_UPDATE_CHECK, false)
        updateBackgroundSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_BACKGROUND_UPDATE_CHECK, checked).apply()
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    schedulePeriodicUpdateCheck()
                }
            } else {
                cancelPeriodicUpdateCheck()
            }
        }

        supportContactButton = findViewById(R.id.support_contact_button)
        supportContactButton.setOnClickListener { openSupportMail() }

        backButton = findViewById(R.id.settings_back_button)
        backButton.setOnClickListener { finish() }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizukuState()

        if (updateAutoSwitch.isChecked) {
            checkForUpdates()
        } else {
            updateStatusText.text = ""
        }
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuState()
    }

    /**
     * Zeigt/versteckt den Shizuku-Hinweis und laedt bei Verfuegbarkeit die Profile -
     * wird auch in onResume aufgerufen, damit die Rueckkehr aus der Shizuku-App oder
     * aus den System-Einstellungen automatisch weitermacht, ohne dass der Nutzer
     * AtlayaSwitch neu oeffnen muss. Drei Zustaende, weil "nicht installiert" und
     * "installiert, laeuft aber nicht (z.B. nach Neustart)" unterschiedliche
     * Anleitungen brauchen - beide zusaetzlich unterschieden nach Profil, weil
     * Entwickleroptionen/Drahtloses Debugging nur im Profil "Eigentuemer" sichtbar sind.
     */
    /**
     * Entprellt wiederholte Aufrufe (z.B. onCreate direkt gefolgt von onResume, oder
     * schnelles Verlassen/Zurueckkehren beim Testen): jeder Durchlauf startet mehrere
     * eigene Shizuku-UserService-Binds (loadProfiles), die auf
     * diesem Geraet durch eine SELinux-Restriktion ohnehin schon langsam sind - ohne
     * Entprellung stapeln sich bei mehrfachem Aufruf binnen kurzer Zeit viele
     * ueberlappende Binds, was den Shizuku-Dienst zusaetzlich verstopft.
     */
    private fun refreshShizukuState() {
        val now = System.currentTimeMillis()
        if (now - lastShizukuStateRefreshMs < 1500L) return
        lastShizukuStateRefreshMs = now

        if (!ShizukuUtils.isShizukuAvailable()) {
            shizukuBanner.visibility = View.VISIBLE
            val installed = ShizukuUtils.isShizukuPackageInstalled(this)
            val owner = ShizukuUtils.isOwnerProfile()

            when {
                !installed && owner -> {
                    shizukuStatusText.text = getString(R.string.settings_shizuku_not_installed_owner)
                    openShizukuButton.visibility = View.VISIBLE
                    openShizukuButton.text = getString(R.string.settings_shizuku_install_button)
                    openShizukuButton.setOnClickListener { ShizukuUtils.openPlayStoreForShizuku(this) }
                    switchProfileButton.visibility = View.GONE
                }
                !installed && !owner -> {
                    shizukuStatusText.text = getString(R.string.settings_shizuku_not_installed_other)
                    openShizukuButton.visibility = View.GONE
                    switchProfileButton.visibility = View.VISIBLE
                }
                installed && owner -> {
                    shizukuStatusText.text = getString(R.string.settings_shizuku_not_running)
                    openShizukuButton.visibility = View.VISIBLE
                    openShizukuButton.text = getString(R.string.settings_shizuku_open_button)
                    openShizukuButton.setOnClickListener { ShizukuUtils.openShizukuApp(this) }
                    switchProfileButton.visibility = View.GONE
                }
                else -> {
                    // installed && !owner: Entwickleroptionen/Drahtloses Debugging sind
                    // in sekundaeren Profilen nicht sichtbar - "Shizuku oeffnen" waere
                    // hier ein Sackgassen-Tap, stattdessen zum Profilwechsel anbieten.
                    shizukuStatusText.text = getString(R.string.settings_shizuku_wrong_profile)
                    openShizukuButton.visibility = View.GONE
                    switchProfileButton.visibility = View.VISIBLE
                }
            }
            return
        }
        shizukuBanner.visibility = View.GONE

        if (ShizukuUtils.hasPermission()) {
            loadProfiles()
        } else {
            ShizukuUtils.requestPermission()
        }
    }

    /** Aktuell aktive App-Sprache (AppCompatDelegate) oder, falls noch keine gewählt
     * wurde, die Gerätesprache - fällt auf Deutsch zurück, falls beides unbekannt ist. */
    private fun currentLanguageTag(): String {
        val applicationLocales = AppCompatDelegate.getApplicationLocales()
        if (!applicationLocales.isEmpty) {
            applicationLocales[0]?.language?.let { return it }
        }
        return Locale.getDefault().language.ifEmpty { "de" }
    }

    /**
     * Setzt setApplicationLocales() ein (AppCompat 1.6+) statt eigener Konfigurations-
     * Tricks - das ist der von Android vorgesehene Weg fuer Pro-App-Sprachen, persistiert
     * die Wahl selbststaendig ueber Neustarts hinweg und stoesst bei Bedarf automatisch
     * ein recreate() dieser Activity an, ohne dass hier manuell rekonfiguriert werden muss.
     */
    private fun showLanguageMenu(anchor: View) {
        val names = resources.getStringArray(R.array.settings_language_names)
        val tags = resources.getStringArray(R.array.settings_language_tags)
        val popup = PopupMenu(this, anchor)
        names.forEachIndexed { index, name -> popup.menu.add(0, index, index, name) }
        popup.setOnMenuItemClickListener { item ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[item.itemId]))
            true
        }
        popup.show()
    }

    /**
     * Setzt den Hilfetext aus den ohnehin schon uebersetzten Bausteinen der Sektion
     * "Verhalten beim Wechsel" zusammen, statt sie im Hilfetext selbst noch einmal zu
     * uebersetzen - eine Quelle der Wahrheit pro Sprache, kein Auseinanderlaufen
     * zwischen Bildschirmtext und Hilfetext moeglich.
     */
    private fun showHelpDialog() {
        val copyrightPrefix = getString(R.string.settings_help_copyright_prefix)
        val licenseLinkText = getString(R.string.settings_help_license_link)
        val body = SpannableStringBuilder(copyrightPrefix).append(licenseLinkText)
        body.setSpan(
            URLSpan(licenseUrl()),
            copyrightPrefix.length,
            copyrightPrefix.length + licenseLinkText.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        body.append("\n\n")
        body.append(buildString {
            append(getString(R.string.settings_help_body))
            append("\n\n")
            append(getString(R.string.settings_section_switch_mode))
            append("\n")
            append(getString(R.string.settings_switch_mode_switch_only))
            append(": ")
            append(getString(R.string.settings_switch_mode_switch_only_hint))
            append("\n\n")
            append(getString(R.string.settings_switch_mode_end_session))
            append(": ")
            append(getString(R.string.settings_switch_mode_end_session_hint))
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_help_title)
            .setMessage(body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        // setMessage() allein macht Spans nicht klickbar - erst das MovementMethod
        // auf der tatsaechlichen Dialog-TextView aktiviert den URLSpan-Tap.
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun checkForUpdates() {
        updateStatusText.text = getString(R.string.settings_update_status_checking)
        updateDownloadButton.visibility = View.GONE
        UpdateChecker.check(
            context = this,
            onResult = { result ->
                latestUpdateUrl = result.downloadUrl
                if (result.updateAvailable) {
                    updateStatusText.text = getString(
                        R.string.settings_update_status_available, result.latestVersion, result.currentVersion
                    )
                    updateDownloadButton.visibility = if (result.downloadUrl.isNotEmpty()) View.VISIBLE else View.GONE
                } else {
                    updateStatusText.text = getString(R.string.settings_update_status_current, result.currentVersion)
                }
            },
            onError = { e ->
                updateStatusText.text = getString(R.string.settings_update_status_error, e.message ?: "")
            }
        )
    }

    private fun loadProfiles() {
        ShizukuUtils.listProfiles(
            context = this,
            onResult = { profiles ->
                val currentTargetId = prefs.getInt(MainActivity.KEY_TARGET_USER_ID, -1)
                profileAdapter = ProfileAdapter(profiles, currentTargetId) { profile ->
                    prefs.edit().putInt(MainActivity.KEY_TARGET_USER_ID, profile.userId).apply()
                    profileAdapter?.setSelected(profile.userId)
                    Toast.makeText(this, "Zielprofil gespeichert", Toast.LENGTH_SHORT).show()
                    checkTargetProfileInstalled(profile.userId, profile.label)
                }
                recyclerView.adapter = profileAdapter

                val currentTarget = profiles.find { it.userId == currentTargetId }
                if (currentTarget != null) {
                    checkTargetProfileInstalled(currentTarget.userId, currentTarget.label)
                } else {
                    targetProfileWarning.visibility = View.GONE
                }
            },
            onError = { e ->
                Toast.makeText(this, "Profile konnten nicht geladen werden: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    /**
     * Das Zielprofil ist absichtlich das unverfängliche Tarnprofil, das auch Dritte
     * (z.B. bei einer Kontrolle) zu sehen bekommen dürfen sollen. AtlayaSwitch selbst
     * dort installiert zu haben würde genau das verraten, was das Profil verbergen
     * soll - deshalb wird das bei jeder Zielprofil-Auswahl aktiv geprüft statt nur in
     * der Dokumentation als manuelle Installationsregel zu stehen.
     */
    private fun checkTargetProfileInstalled(userId: Int, label: String) {
        ShizukuUtils.isPackageInstalledInProfile(
            context = this,
            userId = userId,
            onResult = { installed ->
                if (installed) {
                    targetProfileWarning.visibility = View.VISIBLE
                    targetProfileWarningText.text = getString(R.string.settings_target_profile_warning, label)
                    targetProfileWarningButton.setOnClickListener {
                        ShizukuUtils.uninstallFromProfile(
                            context = this,
                            userId = userId,
                            onResult = { ok ->
                                if (ok) {
                                    Toast.makeText(this, getString(R.string.settings_target_profile_removed_toast), Toast.LENGTH_SHORT).show()
                                    targetProfileWarning.visibility = View.GONE
                                } else {
                                    Toast.makeText(this, getString(R.string.settings_target_profile_remove_failed, label), Toast.LENGTH_LONG).show()
                                }
                            },
                            onError = {
                                Toast.makeText(this, getString(R.string.settings_target_profile_remove_failed, label), Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                } else {
                    targetProfileWarning.visibility = View.GONE
                }
            },
            onError = {
                targetProfileWarning.visibility = View.GONE
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private class ProfileAdapter(
        private val profiles: List<ShizukuUtils.GraphenProfile>,
        private var selectedUserId: Int,
        private val onClick: (ShizukuUtils.GraphenProfile) -> Unit
    ) : RecyclerView.Adapter<ProfileAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(android.R.id.text1)
        }

        fun setSelected(userId: Int) {
            selectedUserId = userId
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val profile = profiles[position]
            val marker = if (profile.userId == selectedUserId) "✓ " else ""
            holder.text.text = "$marker${profile.label} (ID ${profile.userId})"
            holder.itemView.setOnClickListener { onClick(profile) }
        }

        override fun getItemCount(): Int = profiles.size
    }

    /** Registriert den periodischen Hintergrund-Check bei WorkManager - Mindestintervall
     * von Android ist 15 Minuten, taeglich reicht fuer einen Update-Hinweis voellig aus und
     * schont den Akku. Ueberlebt App-Neustarts UND Geraete-Neustarts automatisch (WorkManager
     * persistiert eingeplante Arbeit selbst), es ist also keine BOOT_COMPLETED-Behandlung nötig. */
    private fun schedulePeriodicUpdateCheck() {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            UpdateCheckWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelPeriodicUpdateCheck() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(UpdateCheckWorker.UNIQUE_WORK_NAME)
    }

    companion object {
        const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
        const val KEY_BACKGROUND_UPDATE_CHECK = "background_update_check"
        const val KEY_SWITCH_MODE = "switch_mode"
        const val SWITCH_MODE_SWITCH_ONLY = "switch_only"
        const val SWITCH_MODE_END_SESSION = "end_session"
        private val LICENSE_SITE_LANGUAGES = setOf("de", "en", "fr", "it", "es")
    }

    /** Zeigt auf die Sprachversion der Lizenzseite, die zur aktuell gewählten App-Sprache
     * passt - fällt auf Deutsch zurück, falls die Website diese Sprache (noch) nicht hat. */
    private fun licenseUrl(): String {
        val tag = currentLanguageTag().takeIf { it in LICENSE_SITE_LANGUAGES } ?: "de"
        return "https://atlaya.capecter.com/atlayaswitch/$tag/lizenz.html"
    }

    /** Versionsnummer wird vorausgefuellt (analog zu installedVersionText oben) - erspart
     * Chris die Rueckfrage "welche Version?" bei jeder Support-Mail. */
    private fun openSupportMail() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:AtlayaSwitch@capecter.com")
            putExtra(Intent.EXTRA_SUBJECT, "[AtlayaSwitch] Support")
            putExtra(
                Intent.EXTRA_TEXT,
                "(Bitte hier beschreiben, was nicht funktioniert oder gewünscht ist.)\n\n---\nAtlayaSwitch $versionName"
            )
        }
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "Keine E-Mail-App gefunden.", Toast.LENGTH_LONG).show()
        }
    }
}
