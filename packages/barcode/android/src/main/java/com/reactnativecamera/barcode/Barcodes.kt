package com.reactnativecamera.barcode

import com.google.zxing.BarcodeFormat

object Barcodes {
  val VALID_BAR_CODE_TYPES: Map<String, String> = mapOf(
    "aztec" to BarcodeFormat.AZTEC.toString(),
    "ean13" to BarcodeFormat.EAN_13.toString(),
    "ean8" to BarcodeFormat.EAN_8.toString(),
    "qr" to BarcodeFormat.QR_CODE.toString(),
    "pdf417" to BarcodeFormat.PDF_417.toString(),
    "upc_e" to BarcodeFormat.UPC_E.toString(),
    "datamatrix" to BarcodeFormat.DATA_MATRIX.toString(),
    "code39" to BarcodeFormat.CODE_39.toString(),
    "code93" to BarcodeFormat.CODE_39.toString(),
    "interleaved2of5" to BarcodeFormat.ITF.toString(),
    "codabar" to BarcodeFormat.CODABAR.toString(),
    "code128" to BarcodeFormat.CODE_128.toString(),
    "maxicode" to BarcodeFormat.MAXICODE.toString(),
    "rss14" to BarcodeFormat.RSS_14.toString(),
    "rssexpanded" to BarcodeFormat.RSS_EXPANDED.toString(),
    "upc_a" to BarcodeFormat.UPC_A.toString(),
    "upc_ean" to BarcodeFormat.UPC_EAN_EXTENSION.toString()
  )

  fun inverse(barcodeFormat: BarcodeFormat): String {
    val format = barcodeFormat.toString()
    for ((own, external) in VALID_BAR_CODE_TYPES) {
      if (external == format) {
        return own
      }
    }

    throw Exception("Unsupported barcode format $format")
  }
}
