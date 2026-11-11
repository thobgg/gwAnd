package de.bgghome.gwand.daten

/**
 * Deutsche Namen der Gramps-Typen (Ereignisse, Orte, Archive, Medienarten, Familien), in den Begriffen der deutschen
 * Gramps-Uebersetzung. Der Server uebersetzt Typlisten nur in seine eigene Sprache, darum stehen sie hier.
 * Englisch braucht keine Tabelle: die Grundnamen sind englisch.
 */
object Typnamen {

    fun fuer(sprache: String): Map<String, String> = when (sprache) {
        "de" -> DE
        else -> emptyMap()
    }

    private val DE = mapOf(
        // Ereignisse
        "Unknown" to "Unbekannt", "Custom" to "Benutzerdefiniert", "Adopted" to "Adoptiert", "Birth" to "Geburt", "Death" to "Tod",
        "Adult Christening" to "Erwachsenentaufe", "Baptism" to "Taufe", "Bar Mitzvah" to "Bar Mitzwa", "Bas Mitzvah" to "Bat Mizwa",
        "Blessing" to "Segnung", "Burial" to "Beerdigung", "Cause Of Death" to "Todesursache", "Census" to "Volkszählung",
        "Christening" to "Kindtaufe", "Confirmation" to "Konfirmation", "Cremation" to "Einäscherung", "Degree" to "Abschluss",
        "Education" to "Ausbildung", "Elected" to "Gewählt", "Emigration" to "Auswanderung", "First Communion" to "Erstkommunion",
        "Immigration" to "Einwanderung", "Graduation" to "Studienabschluss", "Medical Information" to "Medizinische Information",
        "Military Service" to "Militärdienst", "Naturalization" to "Einbürgerung", "Nobility Title" to "Adelstitel",
        "Number of Marriages" to "Anzahl der Ehen", "Occupation" to "Beruf", "Ordination" to "Ordination", "Probate" to "Testamentseröffnung",
        "Property" to "Besitz", "Religion" to "Religion", "Residence" to "Wohnort", "Retirement" to "Ruhestand", "Will" to "Testament",
        "Marriage" to "Heirat", "Marriage Settlement" to "Ehevertrag", "Marriage License" to "Heiratserlaubnis",
        "Marriage Contract" to "Ehevertrag (Kontrakt)", "Marriage Banns" to "Aufgebot", "Engagement" to "Verlobung", "Divorce" to "Scheidung",
        "Divorce Filing" to "Scheidungsantrag", "Annulment" to "Annullierung", "Alternate Marriage" to "Alternative Heirat",
        "Stillbirth" to "Totgeburt",
        // Orte
        "Country" to "Land", "State" to "Bundesland", "County" to "Landkreis", "City" to "Stadt", "Parish" to "Pfarrei",
        "Locality" to "Ortsteil", "Street" to "Straße", "Province" to "Provinz", "Region" to "Region", "Department" to "Departement",
        "Neighborhood" to "Stadtteil", "District" to "Bezirk", "Borough" to "Stadtbezirk", "Municipality" to "Gemeinde",
        "Town" to "Kleinstadt", "Village" to "Dorf", "Hamlet" to "Weiler", "Farm" to "Hof", "Building" to "Gebäude", "Number" to "Hausnummer",
        // Archive
        "Library" to "Bibliothek", "Cemetery" to "Friedhof", "Church" to "Kirche", "Archive" to "Archiv", "Album" to "Album",
        "Web site" to "Webseite", "Bookstore" to "Buchhandlung", "Collection" to "Sammlung", "Safe" to "Tresor",
        // Medienarten
        "Audio" to "Audio", "Book" to "Buch", "Card" to "Karte", "Electronic" to "Elektronisch", "Fiche" to "Mikrofiche", "Film" to "Film",
        "Magazine" to "Zeitschrift", "Manuscript" to "Manuskript", "Map" to "Landkarte", "Newspaper" to "Zeitung", "Photo" to "Foto",
        "Tombstone" to "Grabstein", "Video" to "Video",
        // Familien
        "Married" to "Verheiratet", "Unmarried" to "Unverheiratet", "Civil Union" to "Eingetragene Partnerschaft",
    )
}
