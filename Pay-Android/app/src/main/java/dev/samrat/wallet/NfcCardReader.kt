package dev.samrat.wallet

import android.app.Activity
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class NfcCardSnapshot(
    val type: String,
    val fingerprint: String,
    val technologies: List<String>,
    val publicRecords: List<String>,
)

internal data class NfcScanResult(
    val card: NfcCardSnapshot? = null,
    val error: String? = null,
)

internal class NfcCardReader(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var callback: ((NfcScanResult) -> Unit)? = null

    fun start(callback: (NfcScanResult) -> Unit) {
        this.callback = callback
        val nfc = adapter
        when {
            nfc == null -> callback(NfcScanResult(error = "NFC hardware is not available on this device"))
            !nfc.isEnabled -> callback(NfcScanResult(error = "Turn on NFC in Android settings"))
            else -> nfc.enableReaderMode(
                activity,
                ::onTagDiscovered,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE,
                null,
            )
        }
    }

    fun stop() {
        callback = null
        adapter?.let { runCatching { it.disableReaderMode(activity) } }
    }

    private fun onTagDiscovered(tag: Tag) {
        val result = runCatching { readSnapshot(tag) }
            .fold(
                onSuccess = { NfcScanResult(card = it) },
                onFailure = { NfcScanResult(error = it.message ?: "NFC card could not be read") },
            )
        activity.runOnUiThread {
            val currentCallback = callback
            stop()
            currentCallback?.invoke(result)
        }
    }

    private fun readSnapshot(tag: Tag): NfcCardSnapshot {
        val technologies = tag.techList.map { it.substringAfterLast('.') }.sorted()
        val type = when {
            "MifareClassic" in technologies -> "MIFARE Classic card"
            "MifareUltralight" in technologies -> "MIFARE Ultralight card"
            "NfcF" in technologies -> "FeliCa / NFC-F card"
            "IsoDep" in technologies -> "ISO-DEP smart card"
            "NfcV" in technologies -> "NFC-V card"
            "Ndef" in technologies -> "NFC tag"
            else -> "NFC card"
        }
        val publicRecords = readNdefRecords(tag)
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(tag.id)
            .take(8)
            .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        return NfcCardSnapshot(type, fingerprint, technologies, publicRecords)
    }

    private fun readNdefRecords(tag: Tag): List<String> {
        val ndef = Ndef.get(tag) ?: return emptyList()
        return runCatching {
            ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            message?.records?.mapNotNull(::describeRecord).orEmpty()
        }.getOrDefault(emptyList()).also { runCatching { ndef.close() } }
    }

    private fun describeRecord(record: NdefRecord): String? {
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            val payload = record.payload
            if (payload.isEmpty()) return null
            val languageLength = payload[0].toInt() and 0x3F
            val start = 1 + languageLength
            if (start >= payload.size) return null
            return String(payload, start, payload.size - start, StandardCharsets.UTF_8).take(200)
        }
        record.toUri()?.toString()?.let { return it.take(200) }
        record.toMimeType()?.let { return "MIME: ${it.take(120)}" }
        return null
    }
}
