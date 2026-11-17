package io.github.hyrumrichardson.luma.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the phone's contacts. Needs [Manifest.permission.READ_CONTACTS]. */
class Contacts(private val context: Context) {

    val canRead: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Every contact with a phone number. Each contact's numbers are ordered with the default one
     * first, then mobile numbers, then the rest.
     */
    suspend fun withPhoneNumbers(): List<Contact> = withContext(Dispatchers.IO) {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.IS_SUPER_PRIMARY)
        val rows = mutableListOf<Row>()
        context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val name = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val number = c.getColumnIndexOrThrow(Phone.NUMBER)
            val type = c.getColumnIndexOrThrow(Phone.TYPE)
            val primary = c.getColumnIndexOrThrow(Phone.IS_SUPER_PRIMARY)
            while (c.moveToNext()) {
                val n = c.getString(number)?.takeIf { it.isNotBlank() } ?: continue
                val display = c.getString(name)?.takeIf { it.isNotBlank() } ?: continue
                rows += Row(c.getLong(id), display, n, c.getInt(type), c.getInt(primary) != 0)
            }
        }
        rows.groupBy { it.contactId }.map { (id, phones) ->
            val ordered = phones
                .sortedWith(compareByDescending<Row> { it.primary }.thenByDescending { it.type == Phone.TYPE_MOBILE })
                .map { it.number }
                .distinctBy { it.filter(Char::isDigit) }
            Contact(id, phones.first().name, ordered)
        }
    }

    private class Row(val contactId: Long, val name: String, val number: String, val type: Int, val primary: Boolean)
}
