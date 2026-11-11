package de.bgghome.gwand.ui

import de.bgghome.gwand.api.Archiv
import de.bgghome.gwand.api.Benutzer
import de.bgghome.gwand.api.GrampsPerson
import de.bgghome.gwand.api.Medium
import de.bgghome.gwand.api.Ort
import de.bgghome.gwand.api.Quelle
import de.bgghome.gwand.api.Zitat
import de.bgghome.gwand.api.ZeitleistenEintrag
import de.bgghome.webtrees.nativ.api.DescendantNode
import de.bgghome.webtrees.nativ.api.Pedigree
import de.bgghome.webtrees.nativ.api.Person
import de.bgghome.webtrees.nativ.ui.ViewerState
import de.bgghome.webtrees.nativ.ui.tree.Sibling

enum class Bildschirm { Laden, Anmeldung, Haupt }

/** Die Reiter unten: Start, Baum, Personen, Fotos, Mehr (Orte, Quellen, Archive, Einstellungen). */
enum class Bereich { Start, Baum, Personen, Fotos, Mehr }

enum class Nachschlagart { Orte, Quellen, Archive }

/** Ein Jahrestag auf der Startseite: wer, was, wann, vor wie vielen Jahren. */
data class Jahrestag(
    val personen: List<Person>,
    /** Ereignisart, uebersetzt ("Geburt", "Hochzeit" …) */
    val art: String,
    /** Englischer Grundname ("Birth", "Marriage", "Death") */
    val typ: String,
    val monat: Int,
    val tag: Int,
    val jahre: Int,
    val lebend: Boolean,
)

/** Das geoeffnete Profil: die Person mit allem, was der Server dazu liefert, und was nachgeladen wird. */
data class Profil(
    val handle: String,
    val person: GrampsPerson? = null,
    val anzeige: Person? = null,
    val zeitleiste: List<ZeitleistenEintrag> = emptyList(),
    /** Verwandtschaft zur Startperson, leer wenn unbekannt oder die Person selbst */
    val verwandtschaft: String = "",
    val laedt: Boolean = true,
    val reiter: Int = 0,
    /** Zitate der Person und ihrer Ereignisse samt Quellen, erst beim Oeffnen des Reiters geladen */
    val zitate: List<Zitat>? = null,
    val quellen: Map<String, Quelle> = emptyMap(),
    val gemerkt: Boolean = false,
    /** Eltern, Geschwister, Partner, Kinder mit Bild - nachgeladen; bis dahin zeigt die Liste sie ohne Bild. */
    val angehoerige: Map<String, Person> = emptyMap(),
)

/** Detailseite beim Nachschlagen. */
sealed interface Detail {
    data class OrtDetail(val ort: Ort) : Detail
    data class QuelleDetail(val quelle: Quelle) : Detail
    data class ArchivDetail(val archiv: Archiv, val quellen: List<Quelle>) : Detail
}

data class Zustand(
    val bildschirm: Bildschirm = Bildschirm.Laden,
    val beschaeftigt: Boolean = false,
    /** Fehler auf dem Anmeldebildschirm */
    val fehler: String? = null,
    /** Kurze Meldung unten (Snackbar) */
    val meldung: String? = null,
    val adresse: String = "",
    val benutzername: String = "",
    val konto: Benutzer? = null,
    val baumName: String = "",
    val farben: Baumfarben = Baumfarben(),
    val bereich: Bereich = Bereich.Baum,
    val version: String = "",

    // Baum
    val wurzel: String? = null,
    /** Die gespeicherte Startperson: Bezug fuer "Verwandtschaft zu …" */
    val startperson: String? = null,
    val ahnenGenerationen: Int = 4,
    val nachkommenGenerationen: Int = 2,
    val ahnentafel: Pedigree? = null,
    val nachkommen: DescendantNode? = null,
    val geschwister: Map<String, List<Sibling>> = emptyMap(),
    val zeigeGeschwister: Boolean = true,
    val zeigeCousins: Boolean = true,
    val kannErinnern: Boolean = false,
    val erinnerung: Boolean = false,
    val ausgewaehlt: Person? = null,

    // Profil
    val profil: Profil? = null,
    val profilVerlauf: List<String> = emptyList(),

    // Start
    val jahrestage: List<Jahrestag> = emptyList(),
    val jahrestageTage: Int = 7,
    val zuletztGesehen: List<Person> = emptyList(),
    val lesezeichen: List<Person> = emptyList(),
    val zuletztGeaendert: List<Person> = emptyList(),
    val startLaedt: Boolean = false,

    // Personen
    val suche: String = "",
    val treffer: List<Person> = emptyList(),
    val trefferGesamt: Int = 0,
    val naechsteSeite: Int? = null,
    val sucheLaedt: Boolean = false,

    // Fotos
    val fotos: List<Medium> = emptyList(),
    val fotosGesamt: Int = 0,
    val fotosNaechste: Int? = 1,
    val fotosLaedt: Boolean = false,
    val betrachter: ViewerState? = null,

    // Mehr: Nachschlagen
    val nachschlagart: Nachschlagart = Nachschlagart.Orte,
    val nachschlagSuche: String = "",
    val orte: List<Ort> = emptyList(),
    val quellenListe: List<Quelle> = emptyList(),
    val archive: List<Archiv> = emptyList(),
    val nachschlagGesamt: Int = 0,
    val nachschlagNaechste: Int? = 1,
    val nachschlagLaedt: Boolean = false,
    val detail: Detail? = null,
    val typen: Map<String, String> = emptyMap(),
)
