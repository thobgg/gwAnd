package de.bgghome.gwand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import de.bgghome.gwand.daten.BaumBau
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.forSex
import de.bgghome.webtrees.nativ.ui.treeColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Faecherdiagramm: die Mittelperson unten in der Mitte, ihre Vorfahren in Halbkreisen darueber, je Generation ein Ring,
 * vaeterlicherseits links. Tipp auf ein Feld oeffnet das Profil.
 */
@Composable
fun FaecherAnsicht(tafel: Pedigree, onPerson: (Person) -> Unit, modifier: Modifier = Modifier) {
    val farben = treeColors
    val flaeche = MaterialTheme.colorScheme.surface
    val schrift = MaterialTheme.colorScheme.onSurface
    val messer = rememberTextMeasurer()
    val nachN = tafel.ancestors.associateBy { it.n }
    val generationen = maxOf(2, tafel.ancestors.maxOfOrNull { BaumBau.generation(it.n) + 1 } ?: 2)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val b = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val radius = min(b / 2f, h * 0.92f) * 0.96f
        val mitte = Offset(b / 2f, (h + radius) / 2f)
        val innen = radius * 0.17f
        val ring = (radius - innen) / (generationen - 1)

        fun treffer(p: Offset): Person? {
            val dx = p.x - mitte.x; val dy = p.y - mitte.y
            val r = sqrt(dx * dx + dy * dy)
            if (r <= innen) return nachN[1]?.person
            val g = ((r - innen) / ring).toInt() + 1
            if (g >= generationen || dy > 0) return null
            var winkel = atan2(dy, dx).toDouble()          // -PI (links) … 0 (rechts) ueber oben
            if (winkel > 0) winkel -= 2 * PI
            val anteil = (winkel + PI) / PI                    // 0 links … 1 rechts
            val i = (anteil * (1 shl g)).toInt().coerceIn(0, (1 shl g) - 1)
            return nachN[(1 shl g) + i]?.person
        }

        Canvas(Modifier.fillMaxSize().pointerInput(tafel) { detectTapGestures { p -> treffer(p)?.let(onPerson) } }) {
            // Mittelperson
            nachN[1]?.person?.let { p ->
                drawCircle(farben.forSex(p.sex).copy(alpha = 0.25f), innen, mitte)
                drawCircle(farben.forSex(p.sex), innen, mitte, style = Stroke(2f))
                beschriften(messer, p, mitte, 0f, innen * 1.8f, 12f, schrift)
            }
            for (g in 1 until generationen) {
                val anzahl = 1 shl g
                val r1 = innen + (g - 1) * ring
                val r2 = r1 + ring
                val schritt = 180f / anzahl
                for (i in 0 until anzahl) {
                    val start = 180f + i * schritt
                    val person = nachN[anzahl + i]?.person
                    val pfad = ringstueck(mitte, r1, r2, start, schritt)
                    drawPath(pfad, if (person != null) farben.forSex(person.sex).copy(alpha = 0.22f) else flaeche)
                    drawPath(pfad, farben.connector, style = Stroke(1.2f))
                    if (person == null) continue
                    // Beschriftung: innen quer, aussen entlang des Radius
                    val mittelWinkel = Math.toRadians((start + schritt / 2).toDouble())
                    val rm = (r1 + r2) / 2
                    val punkt = Offset(mitte.x + (rm * cos(mittelWinkel)).toFloat(), mitte.y + (rm * sin(mittelWinkel)).toFloat())
                    val quer = g <= 2
                    val drehung = if (quer) (start + schritt / 2 + 90f) else (start + schritt / 2).let { if (it > 270f) it else it + 180f }
                    val bogen = (rm * Math.toRadians(schritt.toDouble())).toFloat()
                    val breite = if (quer) bogen * 0.9f else ring * 0.92f
                    val hoehe = if (quer) ring * 0.86f else bogen * 0.86f
                    val groesse = when (g) { 1 -> 12f; 2 -> 11f; 3 -> 10f; else -> 9f }
                    // Jeder Text bleibt in seinem Feld: Zeilen nach der verfuegbaren Hoehe, Rest abgeschnitten
                    clipPath(pfad) { beschriften(messer, person, punkt, drehung, breite, groesse, schrift, nurName = g >= 4, hoehe = hoehe) }
                }
            }
        }
    }
}

private fun ringstueck(m: Offset, r1: Float, r2: Float, start: Float, schritt: Float): Path = Path().apply {
    arcTo(androidx.compose.ui.geometry.Rect(m, r2), start, schritt, forceMoveTo = true)
    arcTo(androidx.compose.ui.geometry.Rect(m, r1), start + schritt, -schritt, forceMoveTo = false)
    close()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.beschriften(
    messer: androidx.compose.ui.text.TextMeasurer, p: Person, punkt: Offset, drehung: Float, breite: Float, groesse: Float,
    farbe: Color, nurName: Boolean = false, hoehe: Float = Float.MAX_VALUE,
) {
    if (breite < 20f) return
    val zeilenhoehe = groesse.sp.toPx() * 1.3f
    val zeilen = (hoehe / zeilenhoehe).toInt().coerceIn(1, 3)
    val text = if (nurName || p.lifespan.isBlank() || zeilen < 2) p.name else p.name + "\n" + p.lifespan
    val mass = messer.measure(text, TextStyle(color = farbe, fontSize = groesse.sp), overflow = TextOverflow.Ellipsis,
        maxLines = if (nurName) 1 else zeilen, constraints = Constraints(maxWidth = breite.toInt()))
    rotate(drehung, punkt) {
        drawText(mass, topLeft = Offset(punkt.x - mass.size.width / 2f, punkt.y - mass.size.height / 2f))
    }
}
