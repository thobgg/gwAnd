# Musterantworten Gramps Web API

Server http://192.168.178.73:8096, Konto admin.

| Antwort | HTTP | Gesamtzahl |
|---|---|---|
| token-felder | 200 |  |
| metadata | 200 |  |
| people-liste | 200 | 493 |
| person-voll | 200 |  |
| person-timeline | 200 | 8 |
| familie-voll | 200 |  |
| relation-vater | 200 |  |
| ereignis-voll | 200 |  |
| ort-voll | 200 |  |
| medien-liste | 200 | 142 |
| filter-regeln-personen | 200 |  |
| filter-vorfahren-4gen | 200 | 31 |
| filter-nachkommen-3gen | 200 | 2 |
| baum-wurzel-I0008 | 200 | 1 |
| baum-vorfahren-I0008 | 200 | 31 |
| baum-nachkommen-I0008 | 200 | 5 |
| baum-partner-I0008 | 200 | 3 |
| bookmarks | 200 |  |
| transaktionen-verlauf | 200 | 3 |
| notizen-typen | 200 |  |
| ereignis-typen | 200 |  |
| tasks | 200 |  |
| baeume | 200 |  |
| benutzer-ich | 200 |  |

## Befunde zu den offenen Fragen

- API-Version laut metadata: `{"schema": "3.25.2", "version": "3.25.2"}`
- Startperson laut metadata (`default_person`): `nicht gesetzt`
- Mittelperson: `I0001` Handle `1051e1fd7896bb447616685e8e7`
- Vorschaubild mit Header: HTTP 200
- Vorschaubild mit Parameter jwt: HTTP 200
- Vorschaubild ohne Anmeldung: HTTP 401
- Filterregel `IsLessThanNthGenerationAncestorOf`: vorhanden
- Filterregel `IsLessThanNthGenerationDescendantOf`: vorhanden
- Filterregel `IsRelatedWith`: vorhanden
- Filterregel `HasIdOf`: vorhanden
- Filterregel `ProbablyAlive`: vorhanden
- Filterregel `HasBirth`: vorhanden
- Filterregel `IsSiblingOf`: NICHT vorhanden
- Filterregel `HasAnniversary`: NICHT vorhanden
