package com.berkant.yaninda.core.phone

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.openPhoneDialer(normalizedPhoneNumber: String): Boolean {
    require(NORMALIZED_PHONE_PATTERN.matches(normalizedPhoneNumber)) {
        "The phone number must be normalized before opening the dialer."
    }
    val dialIntent = Intent(
        Intent.ACTION_DIAL,
        Uri.fromParts("tel", normalizedPhoneNumber, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(dialIntent)
        true
    } catch (_: RuntimeException) {
        false
    }
}

private val NORMALIZED_PHONE_PATTERN = Regex("^\\+?[0-9]{7,15}$")
