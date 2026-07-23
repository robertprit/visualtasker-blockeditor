# Legacy Blockly XML Import

Vorläufige Dokumentation fuer v2.1 / Meilenstein 2.

Blockly- und Macrorify-XML sind Importformate, nicht die semantische Wahrheit des neuen Systems.

Diese Dokumentation ist vorlaeufig im Blockeditor-Repository abgelegt. Die vollstaendige VisualTasker-Studio-Dokumentation zieht spaeter in das Hauptrepository-Wiki um, sobald das Hauptrepository oeffentlich ist.

## Importverhalten

1. XML wird strukturell gelesen.
2. Block-IDs werden stabilisiert, falls IDs fehlen oder doppelt vorkommen.
3. Bekannte Blöcke werden typisiert und mit Canonical Metadata versehen.
4. Unbekannte Blöcke bleiben sichtbar als unsupported/legacy.
5. Das Ergebnis wird als `WorkspaceDocument` gespeichert und kann via Save/Load wieder geladen werden.

## Fehlerverhalten

- Kaputtes XML erzeugt einen sichtbaren Importfehler.
- Unbekannte Blocktypen erzeugen im Blockeditor keinen Crash.
- Der Flowchart-Macro-Import bleibt streng und darf bei unsupported Semantik fail-fast abbrechen.

## Grenzen

- Keine Conditions
- Keine Loops
- Keine Branch Labels
- Keine Runtime-Ausfuehrung
- Kein Reverse Compiler
- Keine Blockly-WebView-Semantik
- Keine stille Weiterleitung in den Flowchart-Import
