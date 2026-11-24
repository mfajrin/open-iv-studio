package com.example.core

import java.util.Locale

data class JoaatResult(
  val input: String,
  val unsignedInt: Long,
  val signedInt: Int,
  val hexUpper: String,
)

data class GtaColorBreakdown(
  val hex: String,
  val rInt: Int,
  val gInt: Int,
  val bInt: Int,
  val rFloat: String,
  val gFloat: String,
  val bFloat: String,
  val abgrHex: String,
)

object GtaHashUtils {
  /**
   * Computes GTA V Jenkins One-At-A-Time (JOAAT) hash on lowercase ASCII string.
   */
  fun joaat(input: String): JoaatResult {
    val normalized = input.lowercase(Locale.US)
    var hash = 0
    for (ch in normalized) {
      hash += ch.code
      hash += (hash shl 10)
      hash = hash xor (hash ushr 6)
    }
    hash += (hash shl 3)
    hash = hash xor (hash ushr 11)
    hash += (hash shl 15)

    val unsigned = hash.toLong() and 0xFFFFFFFFL
    val hex = "0x" + unsigned.toString(16).uppercase(Locale.US).padStart(8, '0')
    return JoaatResult(
      input = input,
      unsignedInt = unsigned,
      signedInt = hash,
      hexUpper = hex,
    )
  }

  fun parseHexColor(hexInput: String): GtaColorBreakdown? {
    val clean = hexInput.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val rgbHex = if (clean.length == 8) clean.substring(2) else clean
    val r = rgbHex.substring(0, 2).toIntOrNull(16) ?: return null
    val g = rgbHex.substring(2, 4).toIntOrNull(16) ?: return null
    val b = rgbHex.substring(4, 6).toIntOrNull(16) ?: return null
    val abgr = (0xFF shl 24) or (b shl 16) or (g shl 8) or r
    return GtaColorBreakdown(
      hex = "#" + rgbHex.uppercase(Locale.US),
      rInt = r,
      gInt = g,
      bInt = b,
      rFloat = String.format(Locale.US, "%.4f", r / 255f),
      gFloat = String.format(Locale.US, "%.4f", g / 255f),
      bFloat = String.format(Locale.US, "%.4f", b / 255f),
      abgrHex = "0x" + (abgr.toLong() and 0xFFFFFFFFL).toString(16).uppercase(Locale.US).padStart(8, '0'),
    )
  }
}
