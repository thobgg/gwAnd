# gwAnd

Native Android-App für Handy und Tablet für [Gramps Web](https://www.grampsweb.org/). gwAnd spricht direkt mit der
Gramps Web API (3.x), ohne eigenen Serverteil, und läuft mit selbst gehosteten Instanzen genauso wie mit gehosteten.

Kein offizielles Projekt von Gramps oder Gramps Web.

## Stand

In Entwicklung, noch keine Veröffentlichung. Anmelden, Startperson wählen, Baum mit Vorfahren und Nachkommen, Suche.

## Aufbau

| Ordner | Inhalt |
|---|---|
| `core/` | Client für die Gramps Web API, Modelle, Übersetzer in die Baumdarstellung, ViewModel, Bildschirme |
| `app/` | Android-Hülle |
| `app4webtrees/` | Git-Submodul; daraus nutzt gwAnd Bausteine und die Baumdarstellung |
| `testdaten/` | Demo-Baum Falkenrath (CC0) und aufgezeichnete Antworten eines Testservers |
| `server/` | Compose-Datei für einen Testserver |

## Bauen

```
git clone --recurse-submodules <repo>
./gradlew :core:desktopTest :app:assembleDebug
```

## Lizenz

GPL-3.0
