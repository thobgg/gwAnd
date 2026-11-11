package de.bgghome.gwand.daten

/**
 * Deutsche Namen der Gramps-Typen (Ereignisse, Orte, Archive, Medienarten, Familien), in den Begriffen der deutschen
 * Gramps-Uebersetzung. Der Server uebersetzt Typlisten nur in seine eigene Sprache, darum stehen sie hier.
 * Englisch braucht keine Tabelle: die Grundnamen sind englisch.
 */
object Typnamen {

    fun fuer(sprache: String): Map<String, String> = when (sprache) {
        "de" -> DE
        "fr" -> FR
        "nl" -> NL
        "es" -> ES
        else -> emptyMap()
    }

    // Franzoesisch, Niederlaendisch, Spanisch: die haeufigen Arten (Ereignisse, Orte, Archive, Familien); seltene bleiben englisch.
    private val FR = mapOf(
        "Unknown" to "Inconnu", "Birth" to "Naissance", "Death" to "Décès", "Baptism" to "Baptême", "Christening" to "Baptême (enfant)",
        "Burial" to "Inhumation", "Cremation" to "Crémation", "Marriage" to "Mariage", "Engagement" to "Fiançailles", "Divorce" to "Divorce",
        "Marriage Banns" to "Bans de mariage", "Marriage Contract" to "Contrat de mariage", "Occupation" to "Profession", "Residence" to "Résidence",
        "Confirmation" to "Confirmation", "First Communion" to "Première communion", "Education" to "Éducation", "Graduation" to "Diplôme",
        "Military Service" to "Service militaire", "Emigration" to "Émigration", "Immigration" to "Immigration", "Census" to "Recensement",
        "Religion" to "Religion", "Will" to "Testament", "Probate" to "Homologation", "Property" to "Propriété", "Retirement" to "Retraite",
        "Adopted" to "Adopté", "Stillbirth" to "Mort-né", "Naturalization" to "Naturalisation", "Nobility Title" to "Titre de noblesse",
        "Country" to "Pays", "State" to "État", "County" to "Comté", "City" to "Ville", "Parish" to "Paroisse", "Village" to "Village",
        "Hamlet" to "Hameau", "Farm" to "Ferme", "Building" to "Bâtiment", "Street" to "Rue", "Municipality" to "Commune", "District" to "District",
        "Library" to "Bibliothèque", "Cemetery" to "Cimetière", "Church" to "Église", "Archive" to "Archives", "Collection" to "Collection",
        "Married" to "Mariés", "Unmarried" to "Non mariés", "Civil Union" to "Union civile",
    )
    private val NL = mapOf(
        "Unknown" to "Onbekend", "Birth" to "Geboorte", "Death" to "Overlijden", "Baptism" to "Doop", "Christening" to "Kinderdoop",
        "Burial" to "Begrafenis", "Cremation" to "Crematie", "Marriage" to "Huwelijk", "Engagement" to "Verloving", "Divorce" to "Scheiding",
        "Marriage Banns" to "Ondertrouw", "Marriage Contract" to "Huwelijkscontract", "Occupation" to "Beroep", "Residence" to "Woonplaats",
        "Confirmation" to "Vormsel", "First Communion" to "Eerste communie", "Education" to "Opleiding", "Graduation" to "Diploma",
        "Military Service" to "Militaire dienst", "Emigration" to "Emigratie", "Immigration" to "Immigratie", "Census" to "Volkstelling",
        "Religion" to "Religie", "Will" to "Testament", "Probate" to "Boedel", "Property" to "Bezit", "Retirement" to "Pensioen",
        "Adopted" to "Geadopteerd", "Stillbirth" to "Doodgeboren", "Naturalization" to "Naturalisatie", "Nobility Title" to "Adellijke titel",
        "Country" to "Land", "State" to "Staat", "County" to "Graafschap", "City" to "Stad", "Parish" to "Parochie", "Village" to "Dorp",
        "Hamlet" to "Gehucht", "Farm" to "Boerderij", "Building" to "Gebouw", "Street" to "Straat", "Municipality" to "Gemeente", "District" to "District",
        "Library" to "Bibliotheek", "Cemetery" to "Begraafplaats", "Church" to "Kerk", "Archive" to "Archief", "Collection" to "Collectie",
        "Married" to "Gehuwd", "Unmarried" to "Ongehuwd", "Civil Union" to "Geregistreerd partnerschap",
    )
    private val ES = mapOf(
        "Unknown" to "Desconocido", "Birth" to "Nacimiento", "Death" to "Defunción", "Baptism" to "Bautismo", "Christening" to "Bautizo",
        "Burial" to "Entierro", "Cremation" to "Cremación", "Marriage" to "Matrimonio", "Engagement" to "Compromiso", "Divorce" to "Divorcio",
        "Marriage Banns" to "Amonestaciones", "Marriage Contract" to "Contrato matrimonial", "Occupation" to "Ocupación", "Residence" to "Residencia",
        "Confirmation" to "Confirmación", "First Communion" to "Primera comunión", "Education" to "Educación", "Graduation" to "Graduación",
        "Military Service" to "Servicio militar", "Emigration" to "Emigración", "Immigration" to "Inmigración", "Census" to "Censo",
        "Religion" to "Religión", "Will" to "Testamento", "Probate" to "Validación de testamento", "Property" to "Propiedad", "Retirement" to "Jubilación",
        "Adopted" to "Adoptado", "Stillbirth" to "Nacido muerto", "Naturalization" to "Naturalización", "Nobility Title" to "Título nobiliario",
        "Country" to "País", "State" to "Estado", "County" to "Condado", "City" to "Ciudad", "Parish" to "Parroquia", "Village" to "Pueblo",
        "Hamlet" to "Aldea", "Farm" to "Granja", "Building" to "Edificio", "Street" to "Calle", "Municipality" to "Municipio", "District" to "Distrito",
        "Library" to "Biblioteca", "Cemetery" to "Cementerio", "Church" to "Iglesia", "Archive" to "Archivo", "Collection" to "Colección",
        "Married" to "Casados", "Unmarried" to "No casados", "Civil Union" to "Unión civil",
    )

    /** Objektklassen fuer den Verlauf ("Family" -> "Familie"). */
    fun klasse(klasse: String, sprache: String): String = if (sprache == "de") KLASSEN_DE[klasse] ?: klasse else when (klasse) {
        "Family" -> "Family"; "Media" -> "Media object"; "Repository" -> "Repository"; else -> klasse
    }

    private val KLASSEN_DE = mapOf(
        "Person" to "Person", "Family" to "Familie", "Event" to "Ereignis", "Place" to "Ort", "Source" to "Quelle",
        "Citation" to "Fundstelle", "Media" to "Medium", "Note" to "Notiz", "Repository" to "Archiv", "Tag" to "Schlagwort",
    )

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
