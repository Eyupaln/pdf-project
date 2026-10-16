package com.scanpdf.ocr.pdf

/**
 * Görüntü (piksel) uzayı ile PDF sayfa uzayı arasındaki eşleme.
 *
 * PDF `/Rotate`, sayfanın **görüntülenirken saat yönünde** kaç derece
 * döndürüleceğini söyler (PDF 32000-1:2008, 14.11.2). Bu nedenle bir görüntü
 * noktasını sayfa uzayına taşırken döndürmenin **tersi** uygulanır.
 *
 * Türetme: görüntü koordinatı yukarıdan aşağı (dx, dy), sayfa uzayı ise alttan
 * yukarı (0..[width], 0..[height]) ölçülür. Önce nokta "yukarıdan aşağı" temsile
 * çevrilir, sonra saat yönünde `r` derece döndürülüp ötelenerek sayfa uzayına
 * getirilir. Ters eşleme:
 *
 * | r   | sayfa x    | sayfa y    | metin matrisi açısı |
 * |-----|------------|------------|---------------------|
 * | 0   | dx         | H - dy     | 0°                  |
 * | 90  | dy         | dx         | +90°                |
 * | 180 | W - dx     | dy         | 180°                |
 * | 270 | W - dy     | H - dx     | -90°                |
 *
 * Ters yönde doğrulama: `PageGeometryRenderingTest`, gerçek PDFBox ile çizilmiş
 * döndürülmüş sayfaları çözümleyip bu eşlemenin öngördüğü pikseli doğrular.
 *
 * Saf Kotlin: Android sınıfı kullanmaz, bu yüzden JVM birim testleriyle
 * doğrulanabilir.
 */
internal object PageGeometry {

    /** Görüntülenen sayfadaki bir noktanın sayfa uzayındaki yeri ve metin açısı. */
    data class Anchor(
        /** Sayfa uzayında x (kırpma kutusunun sol kenarından). */
        val x: Double,
        /** Sayfa uzayında y (kırpma kutusunun alt kenarından). */
        val y: Double,
        /** Metin matrisi döndürmesi, derece; saat yönünün tersi pozitif. */
        val degrees: Double,
    )

    /** `/Rotate` değerini 0/90/180/270 aralığına indirger. */
    fun normalizeRotation(rotation: Int): Int = ((rotation % 360) + 360) % 360

    /**
     * Döndürme uygulanmış sayfanın görüntülenen boyutu (nokta).
     *
     * 90° ve 270°'de genişlik/yükseklik yer değiştirir.
     */
    fun displayWidth(width: Double, height: Double, rotation: Int): Double =
        if (isQuarterTurn(rotation)) height else width

    /** [displayWidth]'ın yükseklik karşılığı. */
    fun displayHeight(width: Double, height: Double, rotation: Int): Double =
        if (isQuarterTurn(rotation)) width else height

    /** 90° veya 270° (tek çeyrek dönüş) mü? */
    fun isQuarterTurn(rotation: Int): Boolean {
        val r = normalizeRotation(rotation)
        return r == 90 || r == 270
    }

    /**
     * Görüntü noktasını ([dx], [dy]) sayfa uzayına taşır.
     *
     * @param width kırpma kutusunun genişliği (nokta)
     * @param height kırpma kutusunun yüksekliği (nokta)
     */
    fun anchor(dx: Double, dy: Double, width: Double, height: Double, rotation: Int): Anchor =
        when (normalizeRotation(rotation)) {
            90 -> Anchor(x = dy, y = dx, degrees = 90.0)
            180 -> Anchor(x = width - dx, y = dy, degrees = 180.0)
            270 -> Anchor(x = width - dy, y = height - dx, degrees = -90.0)
            else -> Anchor(x = dx, y = height - dy, degrees = 0.0)
        }

    /**
     * [anchor] işleminin tersi: sayfa uzayı noktası hangi görüntü pikseline denk gelir?
     *
     * Yalnızca test ve doğrulama içindir; metin katmanı yazımı [anchor] kullanır.
     */
    fun imagePoint(x: Double, y: Double, width: Double, height: Double, rotation: Int): Pair<Double, Double> =
        when (normalizeRotation(rotation)) {
            90 -> y to x
            180 -> width - x to y
            270 -> height - y to width - x
            else -> x to height - y
        }
}
