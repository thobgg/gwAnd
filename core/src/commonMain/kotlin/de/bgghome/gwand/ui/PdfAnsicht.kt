package de.bgghome.gwand.ui

import androidx.compose.runtime.Composable

/**
 * PDF im eigenen Betrachter: Seiten untereinander in Bildschirmbreite, je Seite mit zwei Fingern zoombar.
 * Die Datei hat gwAnd schon mit dem Anmelde-Token geladen; sie verlaesst die App nicht.
 */
@Composable
expect fun PdfAnsicht(pdf: PdfAnzeige, onClose: () -> Unit)
