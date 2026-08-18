package com.capecter.atlayaswitch

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Test-Modus fuer die UID-Stabilitaetspruefung (siehe PROMPT_NFC_TRIGGER.md).
 * Bevor irgendeine Trigger-Logik entsteht, muss hier per Doppel-Scan geklaert
 * werden, ob der jeweilige Chip eine feste oder eine rotierende UID sendet -
 * nur bei fester UID ist ein NFC-Trigger ueberhaupt sinnvoll umsetzbar.
 */
class NfcTestActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var firstScanUid: String? = null

    private lateinit var statusText: TextView
    private lateinit var scan1Text: TextView
    private lateinit var scan2Text: TextView
    private lateinit var resultText: TextView
    private lateinit var resetButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_test)

        statusText = findViewById(R.id.nfc_status_text)
        scan1Text = findViewById(R.id.nfc_scan1_text)
        scan2Text = findViewById(R.id.nfc_scan2_text)
        resultText = findViewById(R.id.nfc_result_text)
        resetButton = findViewById(R.id.nfc_reset_button)
        resetButton.setOnClickListener { resetScans() }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            statusText.text = getString(R.string.nfc_test_no_hardware)
            resetButton.isEnabled = false
            return
        }

        resetScans()
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return

        if (!adapter.isEnabled) {
            statusText.text = getString(R.string.nfc_test_disabled)
            return
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        val uid = tag?.id?.let { bytes -> bytes.joinToString("") { "%02X".format(it) } } ?: return
        handleScan(uid)
    }

    private fun handleScan(uid: String) {
        val first = firstScanUid
        if (first == null) {
            firstScanUid = uid
            scan1Text.text = getString(R.string.nfc_test_scan1_label, uid)
            statusText.text = getString(R.string.nfc_test_scan_again)
        } else {
            scan2Text.text = getString(R.string.nfc_test_scan2_label, uid)
            val stable = uid.equals(first, ignoreCase = true)
            resultText.text = getString(
                if (stable) R.string.nfc_test_result_stable else R.string.nfc_test_result_rotating
            )
            statusText.text = getString(R.string.nfc_test_done)
        }
    }

    private fun resetScans() {
        firstScanUid = null
        scan1Text.text = getString(R.string.nfc_test_scan1_placeholder)
        scan2Text.text = getString(R.string.nfc_test_scan2_placeholder)
        resultText.text = ""
        statusText.text = getString(R.string.nfc_test_scan_first)
    }
}
