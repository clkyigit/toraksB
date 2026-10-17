package com.clkyigit.toraksb.geometry

/** Planlama BT'sinin çekildiği solunum fazı; yönlendirmede nefes tutma hatırlatması olarak gösterilir. */
enum class BreathPhase(val label: String, val csv: String, val reminder: String?) {
    FREE("Serbest solunum", "serbest", null),
    EXP_HOLD("Ekspiryum sonu nefes tutma", "ekspiryum_sonu", "Hastaya nefesini verip tutturun (ekspiryum sonu)"),
    INSP_HOLD("İnspiryum sonu nefes tutma", "inspiryum_sonu", "Hastaya nefes alıp tutturun (inspiryum sonu)"),
}
