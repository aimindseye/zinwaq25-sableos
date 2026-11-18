package org.sableos.design

import android.content.Context
import android.content.pm.PackageManager

/**
 * Sable Watch (aimindseye/sable-watch), the companion that sends the watch face its counts. Hub, Calendar and
 * Weather let it read the same counts-only rows Sable Launcher reads: Hub's per-app counts, Calendar's next events
 * and Weather's current line. It is not a SableOS app and is signed with its owner's key, so the package name is
 * not enough: the caller must also be signed with [CERT_SHA256].
 */
object SableWatchCaller {
    const val PACKAGE = "io.github.aimindseye.sablewatch"

    /** SHA-256 of Sable Watch's signing certificate (the owner's TenSeven Launcher key). */
    const val CERT_SHA256 = "9b2047390e5aa77537b8e8875cdfd3c2c72d0a0715e172a7f77457fc4300f292"

    private const val HEX_RADIX = 16

    /** True when Sable Watch, signed with its own key, is one of [callingUid]'s packages. */
    fun isCaller(
        context: Context,
        callingUid: Int,
    ): Boolean {
        val packageManager = context.packageManager
        return matches(packageManager.getPackagesForUid(callingUid)?.toList().orEmpty()) { pkg, cert ->
            runCatching {
                packageManager.hasSigningCertificate(pkg, cert, PackageManager.CERT_INPUT_SHA256)
            }.getOrDefault(false)
        }
    }

    /** [isCaller]'s rule without Android: the package is there and [hasCertificate] confirms its key. */
    fun matches(
        packages: List<String>,
        hasCertificate: (String, ByteArray) -> Boolean,
    ): Boolean = PACKAGE in packages && hasCertificate(PACKAGE, certificate())

    fun certificate(): ByteArray = CERT_SHA256.chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
}
