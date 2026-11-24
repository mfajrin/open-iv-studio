package com.example.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

data class DecodedTexture(
  val bitmap: Bitmap,
  val width: Int,
  val height: Int,
  val formatLabel: String,
  val hasAlpha: Boolean,
  val mipmapCount: Int,
  val byteSize: Long,
  val palette: List<Int>,
)

object TextureDecoder {
  fun decode(
    bytes: ByteArray,
    fileName: String,
  ): Result<DecodedTexture> {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return try {
      when {
        ext == "dds" || isDdsMagic(bytes) -> decodeDds(bytes)
        ext == "tga" -> decodeTga(bytes)
        else -> decodeStandardBitmap(bytes, ext)
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun isDdsMagic(bytes: ByteArray): Boolean {
    return bytes.size >= 4 &&
      bytes[0] == 'D'.code.toByte() &&
      bytes[1] == 'D'.code.toByte() &&
      bytes[2] == 'S'.code.toByte() &&
      bytes[3] == ' '.code.toByte()
  }

  private fun decodeStandardBitmap(
    bytes: ByteArray,
    ext: String,
  ): Result<DecodedTexture> {
    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bmp =
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        ?: return Result.failure(IllegalArgumentException("Format gambar tidak dapat didekode (${ext.uppercase()})"))
    val label =
      when (ext) {
        "png" -> "PNG (RGBA)"
        "jpg", "jpeg" -> "JPEG (24-bit)"
        "webp" -> "WebP"
        "bmp" -> "BMP"
        else -> ext.uppercase().ifEmpty { "Bitmap" }
      }
    return Result.success(
      DecodedTexture(
        bitmap = bmp,
        width = bmp.width,
        height = bmp.height,
        formatLabel = label,
        hasAlpha = bmp.hasAlpha(),
        mipmapCount = 1,
        byteSize = bytes.size.toLong(),
        palette = extractPalette(bmp),
      )
    )
  }

  private fun decodeDds(bytes: ByteArray): Result<DecodedTexture> {
    if (bytes.size < 128 || !isDdsMagic(bytes)) {
      return Result.failure(IllegalArgumentException("Header DDS tidak valid"))
    }
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    buf.position(4)
    val headerSize = buf.int // 124
    if (headerSize != 124) {
      return Result.failure(IllegalArgumentException("Ukuran header DDS tidak didukung: $headerSize"))
    }
    buf.int // flags
    val height = buf.int
    val width = buf.int
    buf.int // pitchOrLinearSize
    buf.int // depth
    val mipMapCount = max(1, buf.int)

    // Skip dwReserved1[11] (44 bytes) -> offset 76 is ddspf
    buf.position(76)
    buf.int // pfSize
    val pfFlags = buf.int
    val fourCcBytes = ByteArray(4)
    buf.get(fourCcBytes)
    val fourCc = String(fourCcBytes, Charsets.US_ASCII)
    val rgbBitCount = buf.int
    val rMask = buf.int
    val gMask = buf.int
    val bMask = buf.int
    val aMask = buf.int

    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
      return Result.failure(IllegalArgumentException("Dimensi DDS di luar batas: ${width}x$height"))
    }

    val dataOffset = 128
    val pixels = IntArray(width * height)
    var hasAlpha = false
    val formatLabel: String

    val hasFourCc = (pfFlags and 0x4) != 0
    if (hasFourCc) {
      when (fourCc) {
        "DXT1" -> {
          formatLabel = "DDS • DXT1 (BC1)"
          hasAlpha = decodeDxt1(bytes, dataOffset, width, height, pixels)
        }
        "DXT3" -> {
          formatLabel = "DDS • DXT3 (BC2)"
          hasAlpha = true
          decodeDxt3(bytes, dataOffset, width, height, pixels)
        }
        "DXT5" -> {
          formatLabel = "DDS • DXT5 (BC3)"
          hasAlpha = true
          decodeDxt5(bytes, dataOffset, width, height, pixels)
        }
        "DX10" -> {
          return Result.failure(IllegalArgumentException("DDS DX10 extended header belum didukung"))
        }
        else -> {
          return Result.failure(IllegalArgumentException("Codec DDS FourCC '$fourCc' belum didukung"))
        }
      }
    } else if (rgbBitCount == 32) {
      formatLabel = "DDS • A8R8G8B8 (32-bit)"
      hasAlpha = aMask != 0
      val rShift = Integer.numberOfTrailingZeros(rMask).coerceIn(0, 24)
      val gShift = Integer.numberOfTrailingZeros(gMask).coerceIn(0, 24)
      val bShift = Integer.numberOfTrailingZeros(bMask).coerceIn(0, 24)
      val aShift = if (aMask != 0) Integer.numberOfTrailingZeros(aMask).coerceIn(0, 24) else 24

      var p = dataOffset
      for (i in 0 until width * height) {
        if (p + 4 > bytes.size) break
        val v =
          (bytes[p].toInt() and 0xFF) or
            ((bytes[p + 1].toInt() and 0xFF) shl 8) or
            ((bytes[p + 2].toInt() and 0xFF) shl 16) or
            ((bytes[p + 3].toInt() and 0xFF) shl 24)
        p += 4
        val r = if (rMask != 0) ((v and rMask) ushr rShift) and 0xFF else 0
        val g = if (gMask != 0) ((v and gMask) ushr gShift) and 0xFF else 0
        val b = if (bMask != 0) ((v and bMask) ushr bShift) and 0xFF else 0
        val a = if (aMask != 0) ((v and aMask) ushr aShift) and 0xFF else 255
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
      }
    } else if (rgbBitCount == 24) {
      formatLabel = "DDS • R8G8B8 (24-bit)"
      var p = dataOffset
      for (i in 0 until width * height) {
        if (p + 3 > bytes.size) break
        val b = bytes[p].toInt() and 0xFF
        val g = bytes[p + 1].toInt() and 0xFF
        val r = bytes[p + 2].toInt() and 0xFF
        p += 3
        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
      }
    } else {
      return Result.failure(IllegalArgumentException("Format bit DDS tidak didukung: ${rgbBitCount}-bit"))
    }

    val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    return Result.success(
      DecodedTexture(
        bitmap = bmp,
        width = width,
        height = height,
        formatLabel = formatLabel,
        hasAlpha = hasAlpha,
        mipmapCount = mipMapCount,
        byteSize = bytes.size.toLong(),
        palette = extractPalette(bmp),
      )
    )
  }

  private fun decodeDxt1(
    bytes: ByteArray,
    startOffset: Int,
    width: Int,
    height: Int,
    out: IntArray,
  ): Boolean {
    var hasTransparent = false
    val blocksX = (width + 3) / 4
    val blocksY = (height + 3) / 4
    var offset = startOffset
    val colors = IntArray(4)

    for (by in 0 until blocksY) {
      for (bx in 0 until blocksX) {
        if (offset + 8 > bytes.size) return hasTransparent
        val c0 = u16(bytes, offset)
        val c1 = u16(bytes, offset + 2)
        val bits = u32(bytes, offset + 4)
        offset += 8

        val r0 = ((c0 ushr 11) and 0x1F) * 255 / 31
        val g0 = ((c0 ushr 5) and 0x3F) * 255 / 63
        val b0 = (c0 and 0x1F) * 255 / 31
        val r1 = ((c1 ushr 11) and 0x1F) * 255 / 31
        val g1 = ((c1 ushr 5) and 0x3F) * 255 / 63
        val b1 = (c1 and 0x1F) * 255 / 31

        colors[0] = (0xFF shl 24) or (r0 shl 16) or (g0 shl 8) or b0
        colors[1] = (0xFF shl 24) or (r1 shl 16) or (g1 shl 8) or b1

        if (c0 > c1) {
          colors[2] = (0xFF shl 24) or (((2 * r0 + r1) / 3) shl 16) or (((2 * g0 + g1) / 3) shl 8) or ((2 * b0 + b1) / 3)
          colors[3] = (0xFF shl 24) or (((r0 + 2 * r1) / 3) shl 16) or (((g0 + 2 * g1) / 3) shl 8) or ((b0 + 2 * b1) / 3)
        } else {
          colors[2] = (0xFF shl 24) or (((r0 + r1) / 2) shl 16) or (((g0 + g1) / 2) shl 8) or ((b0 + b1) / 2)
          colors[3] = 0x00000000
          hasTransparent = true
        }

        for (py in 0 until 4) {
          val y = by * 4 + py
          if (y >= height) continue
          for (px in 0 until 4) {
            val x = bx * 4 + px
            if (x >= width) continue
            val code = (bits ushr (2 * (py * 4 + px))) and 0x3
            out[y * width + x] = colors[code]
          }
        }
      }
    }
    return hasTransparent
  }

  private fun decodeDxt3(
    bytes: ByteArray,
    startOffset: Int,
    width: Int,
    height: Int,
    out: IntArray,
  ) {
    val blocksX = (width + 3) / 4
    val blocksY = (height + 3) / 4
    var offset = startOffset
    val colors = IntArray(4)

    for (by in 0 until blocksY) {
      for (bx in 0 until blocksX) {
        if (offset + 16 > bytes.size) return
        val alphaLow = u32(bytes, offset).toLong() and 0xFFFFFFFFL
        val alphaHigh = u32(bytes, offset + 4).toLong() and 0xFFFFFFFFL
        val alpha64 = (alphaHigh shl 32) or alphaLow

        val c0 = u16(bytes, offset + 8)
        val c1 = u16(bytes, offset + 10)
        val bits = u32(bytes, offset + 12)
        offset += 16

        unpackRgb565Pair(c0, c1, colors)
        for (py in 0 until 4) {
          val y = by * 4 + py
          if (y >= height) continue
          for (px in 0 until 4) {
            val x = bx * 4 + px
            if (x >= width) continue
            val idx = py * 4 + px
            val a4 = ((alpha64 ushr (idx * 4)) and 0xFL).toInt()
            val a = a4 * 17
            val code = (bits ushr (2 * idx)) and 0x3
            out[y * width + x] = (a shl 24) or (colors[code] and 0x00FFFFFF)
          }
        }
      }
    }
  }

  private fun decodeDxt5(
    bytes: ByteArray,
    startOffset: Int,
    width: Int,
    height: Int,
    out: IntArray,
  ) {
    val blocksX = (width + 3) / 4
    val blocksY = (height + 3) / 4
    var offset = startOffset
    val colors = IntArray(4)
    val alphas = IntArray(8)

    for (by in 0 until blocksY) {
      for (bx in 0 until blocksX) {
        if (offset + 16 > bytes.size) return
        val a0 = bytes[offset].toInt() and 0xFF
        val a1 = bytes[offset + 1].toInt() and 0xFF
        alphas[0] = a0
        alphas[1] = a1
        if (a0 > a1) {
          for (k in 1..6) alphas[k + 1] = ((7 - k) * a0 + k * a1) / 7
        } else {
          for (k in 1..4) alphas[k + 1] = ((5 - k) * a0 + k * a1) / 5
          alphas[6] = 0
          alphas[7] = 255
        }
        var alphaBits = 0L
        for (b in 0 until 6) {
          alphaBits = alphaBits or ((bytes[offset + 2 + b].toLong() and 0xFFL) shl (8 * b))
        }

        val c0 = u16(bytes, offset + 8)
        val c1 = u16(bytes, offset + 10)
        val bits = u32(bytes, offset + 12)
        offset += 16

        unpackRgb565Pair(c0, c1, colors)
        for (py in 0 until 4) {
          val y = by * 4 + py
          if (y >= height) continue
          for (px in 0 until 4) {
            val x = bx * 4 + px
            if (x >= width) continue
            val idx = py * 4 + px
            val aCode = ((alphaBits ushr (3 * idx)) and 0x7L).toInt()
            val cCode = (bits ushr (2 * idx)) and 0x3
            out[y * width + x] = (alphas[aCode] shl 24) or (colors[cCode] and 0x00FFFFFF)
          }
        }
      }
    }
  }

  private fun unpackRgb565Pair(
    c0: Int,
    c1: Int,
    colors: IntArray,
  ) {
    val r0 = ((c0 ushr 11) and 0x1F) * 255 / 31
    val g0 = ((c0 ushr 5) and 0x3F) * 255 / 63
    val b0 = (c0 and 0x1F) * 255 / 31
    val r1 = ((c1 ushr 11) and 0x1F) * 255 / 31
    val g1 = ((c1 ushr 5) and 0x3F) * 255 / 63
    val b1 = (c1 and 0x1F) * 255 / 31
    colors[0] = (r0 shl 16) or (g0 shl 8) or b0
    colors[1] = (r1 shl 16) or (g1 shl 8) or b1
    colors[2] = (((2 * r0 + r1) / 3) shl 16) or (((2 * g0 + g1) / 3) shl 8) or ((2 * b0 + b1) / 3)
    colors[3] = (((r0 + 2 * r1) / 3) shl 16) or (((g0 + 2 * g1) / 3) shl 8) or ((b0 + 2 * b1) / 3)
  }

  private fun decodeTga(bytes: ByteArray): Result<DecodedTexture> {
    if (bytes.size < 18) return Result.failure(IllegalArgumentException("Header TGA tidak valid"))
    val idLength = bytes[0].toInt() and 0xFF
    val imageType = bytes[2].toInt() and 0xFF
    val width = u16(bytes, 12)
    val height = u16(bytes, 14)
    val bpp = bytes[16].toInt() and 0xFF
    val descriptor = bytes[17].toInt() and 0xFF
    val topOrigin = (descriptor and 0x20) != 0

    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
      return Result.failure(IllegalArgumentException("Dimensi TGA tidak valid: ${width}x$height"))
    }
    if (bpp !in listOf(8, 24, 32)) {
      return Result.failure(IllegalArgumentException("Bit depth TGA tidak didukung: ${bpp}-bit"))
    }

    val bytesPerPixel = bpp / 8
    var pos = 18 + idLength
    val totalPixels = width * height
    val pixels = IntArray(totalPixels)

    fun readPixelAt(p: Int): Int {
      return when (bytesPerPixel) {
        1 -> {
          val g = bytes[p].toInt() and 0xFF
          (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        3 -> {
          val b = bytes[p].toInt() and 0xFF
          val g = bytes[p + 1].toInt() and 0xFF
          val r = bytes[p + 2].toInt() and 0xFF
          (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        else -> {
          val b = bytes[p].toInt() and 0xFF
          val g = bytes[p + 1].toInt() and 0xFF
          val r = bytes[p + 2].toInt() and 0xFF
          val a = bytes[p + 3].toInt() and 0xFF
          (a shl 24) or (r shl 16) or (g shl 8) or b
        }
      }
    }

    when (imageType) {
      2, 3 -> {
        for (i in 0 until totalPixels) {
          if (pos + bytesPerPixel > bytes.size) break
          pixels[i] = readPixelAt(pos)
          pos += bytesPerPixel
        }
      }
      10, 11 -> {
        var written = 0
        while (written < totalPixels && pos < bytes.size) {
          val packet = bytes[pos++].toInt() and 0xFF
          val count = (packet and 0x7F) + 1
          if ((packet and 0x80) != 0) {
            if (pos + bytesPerPixel > bytes.size) break
            val argb = readPixelAt(pos)
            pos += bytesPerPixel
            for (k in 0 until count) {
              if (written >= totalPixels) break
              pixels[written++] = argb
            }
          } else {
            for (k in 0 until count) {
              if (written >= totalPixels || pos + bytesPerPixel > bytes.size) break
              pixels[written++] = readPixelAt(pos)
              pos += bytesPerPixel
            }
          }
        }
      }
      else -> return Result.failure(IllegalArgumentException("Tipe TGA ($imageType) tidak didukung"))
    }

    if (!topOrigin) {
      for (y in 0 until height / 2) {
        val topRow = y * width
        val botRow = (height - 1 - y) * width
        for (x in 0 until width) {
          val tmp = pixels[topRow + x]
          pixels[topRow + x] = pixels[botRow + x]
          pixels[botRow + x] = tmp
        }
      }
    }

    val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    return Result.success(
      DecodedTexture(
        bitmap = bmp,
        width = width,
        height = height,
        formatLabel = "TGA (${bpp}-bit${if (imageType >= 10) " RLE" else ""})",
        hasAlpha = bpp == 32,
        mipmapCount = 1,
        byteSize = bytes.size.toLong(),
        palette = extractPalette(bmp),
      )
    )
  }

  fun encodeToDdsUncompressed(bitmap: Bitmap): ByteArray {
    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

    val out = ByteBuffer.allocate(128 + pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    out.put("DDS ".toByteArray(Charsets.US_ASCII))
    out.putInt(124) // dwSize
    out.putInt(0x1 or 0x2 or 0x4 or 0x1000 or 0x8) // flags
    out.putInt(h)
    out.putInt(w)
    out.putInt(w * 4)
    out.putInt(0) // depth
    out.putInt(1) // mipmaps
    for (i in 0 until 11) out.putInt(0)
    // DDS_PIXELFORMAT
    out.putInt(32)
    out.putInt(0x41) // DDPF_RGB | DDPF_ALPHAPIXELS
    out.putInt(0) // FourCC
    out.putInt(32)
    out.putInt(0x00FF0000)
    out.putInt(0x0000FF00)
    out.putInt(0x000000FF)
    out.putInt(0xFF000000.toInt())
    out.putInt(0x1000) // caps
    out.putInt(0)
    out.putInt(0)
    out.putInt(0)
    out.putInt(0)

    for (argb in pixels) {
      out.putInt(argb)
    }
    return out.array()
  }

  fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
    val baos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
    return baos.toByteArray()
  }

  private fun extractPalette(bitmap: Bitmap): List<Int> {
    val w = bitmap.width
    val h = bitmap.height
    val stepX = max(1, w / 8)
    val stepY = max(1, h / 8)
    val buckets = linkedMapOf<Int, Int>()
    for (y in 0 until h step stepY) {
      for (x in 0 until w step stepX) {
        val c = bitmap.getPixel(x, y)
        val a = (c ushr 24) and 0xFF
        if (a < 32) continue
        // Quantize to 5-bit buckets for clean representative colors
        val r = ((c ushr 16) and 0xF0)
        val g = ((c ushr 8) and 0xF0)
        val b = (c and 0xF0)
        val key = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        buckets[key] = (buckets[key] ?: 0) + 1
      }
    }
    return buckets.entries
      .sortedByDescending { it.value }
      .take(5)
      .map { it.key }
  }

  private fun u16(
    b: ByteArray,
    o: Int,
  ): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

  private fun u32(
    b: ByteArray,
    o: Int,
  ): Int =
    (b[o].toInt() and 0xFF) or
      ((b[o + 1].toInt() and 0xFF) shl 8) or
      ((b[o + 2].toInt() and 0xFF) shl 16) or
      ((b[o + 3].toInt() and 0xFF) shl 24)
}
