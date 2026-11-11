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
import de.bgghome.webtrees.nativ.data.DateForm
import de.bgghome.gwand.daten.Verwandtschaftsart

enum class Bildschirm { Laden, Anmeldung, Haupt }

/** Die Reiter unten: Start, Baum, Personen, Fotos, Mehr (Orte, Quellen, Archive, Einstellungen). */
enum class Bereich { Start, Baum, Personen, Fotos, Mehr }

enum class Nachschlagart { Orte, Quellen, Archive, Verlauf }

/** Darstellung des Baums: Sanduhr (Vorfahren und Nachkommen) oder Faecher (Vorfahren im Halbkreis). */
enum class BaumArt { Sanduhr, Faecher }

data class Chat(val verlauf: List<Pair<String, String>> = emptyList(), val laeuft: Boolean = false)

/** Ein Vorgang im Verlauf, aufbereitet: wer, wann, was (Personen mit Namen). */
data class Verlaufseintrag(
    val vorgang: Int,
    val wann: Double,
    val wer: String,
    val aenderungen: List<Pair<String, Int>>,
    val personen: List<Person>,
)

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
    /** Anmeldung ueber OIDC: was der Server unter [adresse] anbietet (null: noch nicht gefragt oder kein OIDC). */
    val oidc: de.bgghome.gwand.api.OidcKonfig? = null,
    /** Offenes Anmeldefenster eines Anbieters: Startadresse und Name. */
    val oidcFenster: OidcFenster? = null,
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
    val baumArt: BaumArt = BaumArt.Sanduhr,
    /** Kein Netz: die App zeigt den zuletzt geladenen Stand. */
    val offline: Boolean = false,
    val chatMoeglich: Boolean = false,
    val chat: Chat? = null,
    val zeigeGeschwister: Boolean = true,
    val zeigeCousins: Boolean = true,
    /** Partner der Cousins (nur wenn Cousins gezeigt werden) */
    val zeigeCousinPartner: Boolean = true,
    /** Fruehere Mittelpersonen, die zuletzt oben: Zurueck haengt den Baum wieder dorthin */
    val wurzelVerlauf: List<String> = emptyList(),
    /** Baum ohne Kopfzeile und untere Leiste */
    val baumVollbild: Boolean = false,
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
    val verlauf: List<Verlaufseintrag> = emptyList(),
    val typen: Map<String, String> = emptyMap(),

    // Bearbeiten
    val ereignisTypen: List<String> = emptyList(),
    val dialog: Dialog? = null,
    val speichert: Boolean = false,
    val ortVorschlaege: List<Ort> = emptyList(),
    val personVorschlaege: List<Person> = emptyList(),
    val quellVorschlaege: List<Quelle> = emptyList(),

    // Gesichter und Texterkennung an einem Bild
    val gesichter: Gesichter? = null,
    val texterkennung: Texterkennung? = null,
) {
    /** Bearbeiten erst ab Editor: Beitragende duerfen nur anlegen, und fast jede Ergaenzung aendert vorhandene Datensaetze. */
    val darfBearbeiten: Boolean get() = konto?.darfBearbeiten == true
}

/** Ein Gesicht (Ausschnitt) in Prozent des Bildes: links, oben, rechts, unten. */
data class Rahmen(val l: Double, val o: Double, val r: Double, val u: Double) {
    val ganzzahlig: List<Int> get() = listOf(l, o, r, u).map { Math.round(it).toInt().coerceIn(0, 100) }
    fun enthaelt(x: Double, y: Double) = x in l..r && y in o..u
    /** Ueberdeckung zweier Rahmen (Schnitt durch Vereinigung), um erkannte Gesichter bei schon zugeordneten wegzulassen. */
    fun ueberdeckung(b: Rahmen): Double {
        val sl = maxOf(l, b.l); val so = maxOf(o, b.o); val sr = minOf(r, b.r); val su = minOf(u, b.u)
        val schnitt = maxOf(0.0, sr - sl) * maxOf(0.0, su - so)
        val vereinigung = (r - l) * (u - o) + (b.r - b.l) * (b.u - b.o) - schnitt
        return if (vereinigung <= 0) 0.0 else schnitt / vereinigung
    }
}

/** Gesichter-Ansicht eines Bildes: wer schon einen Ausschnitt hat, wer nur am Bild haengt, was der Server erkennt. */
data class Gesichter(
    val medium: String,
    val titel: String,
    val bild: String,
    val zugeordnet: List<Pair<Person, Rahmen>> = emptyList(),
    val ohneRahmen: List<Person> = emptyList(),
    val erkannt: List<Rahmen> = emptyList(),
    val laedt: Boolean = true,
)

/** Ergebnis der Texterkennung eines Bildes; [text] null = laeuft noch. */
data class Texterkennung(val medium: String, val titel: String, val text: String? = null, val fehler: String? = null)

/** Offener Bearbeiten-Dialog; die Eingaben selbst haelt das Formular, hier steht nur der Ausgangsstand. */
sealed interface Dialog {
    /** [handle] null = neue Person */
    data class PersonDialog(val handle: String?, val vorname: String = "", val nachname: String = "", val rufname: String = "", val geschlecht: Int = 2, val stand: Long? = null) : Dialog

    /**
     * Ereignis einer Person ([person]) oder Familie ([familie]); [ereignis] null = neues Ereignis.
     * [datum] null heisst: das vorhandene Datum laesst sich nicht als Eingabe darstellen ([datumText]) und bleibt.
     */
    data class EreignisDialog(
        val person: String?,
        val familie: String?,
        val ereignis: String?,
        val typ: String,
        val datum: DateForm?,
        val datumText: String,
        val ort: String?,
        val ortName: String,
        val beschreibung: String,
        val stand: Long?,
        val personStand: Long?,
    ) : Dialog

    data class VerwandterDialog(val zu: String, val zuName: String, val geschlechtZu: Int, val art: Verwandtschaftsart?, val familie: String? = null) : Dialog

    /** Zitat an die Person oder eines ihrer Ereignisse: (art, handle) to Beschriftung */
    data class ZitatDialog(val ziele: List<Pair<Pair<String, String>, String>>) : Dialog

    data class BeziehungDialog(val person: String) : Dialog

    data class FotoDialog(val person: String) : Dialog

    data class LoeschenDialog(val text: String, val aktion: suspend () -> Unit) : Dialog

    /** Verlauf einer Person im Profil; [eintraege] null = laedt. */
    data class ObjektVerlaufDialog(val titel: String, val eintraege: List<Verlaufseintrag>? = null) : Dialog

    /** Verwandtschaft zwischen [von] und einer gewaehlten Person; [wege] null = noch nicht berechnet. */
    data class VerwandtschaftDialog(val von: Person, val zu: Person? = null, val wege: List<Pair<String, List<Person>>>? = null, val laedt: Boolean = false) : Dialog

    data class BerichtDialog(val person: Person, val grampsId: String, val berichte: List<de.bgghome.gwand.api.Bericht>? = null, val laeuft: String? = null) : Dialog

    data class ExportDialog(val laeuft: String? = null) : Dialog

    data object UeberDialog : Dialog

    data class DnaDialog(val name: String, val treffer: List<Pair<Person?, de.bgghome.gwand.api.DnaTreffer>>? = null) : Dialog

    /** Zusammenfuehren: [bleibt] ist die Person im Profil, [geht] die gewaehlte zweite. */
    data class ZusammenfuehrenDialog(val bleibt: Person, val geht: Person? = null) : Dialog
}

data class OidcFenster(val url: String, val anbieter: String)
