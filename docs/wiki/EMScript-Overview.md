# EMScript Overview

Vorläufige Dokumentation fuer v2.1 / Meilenstein 2.

EMScript ist die textuelle Skript- und Kompatibilitaetsschicht von VisualTasker
Studio. Sie dient dazu, Workflows lesbar, testbar und zwischen Editoren
uebertragbar zu machen.

## Rolle im Studio

| Ebene | Rolle |
|---|---|
| EMScript | Textuelle Darstellung und Compiler-/Validator-Ziel |
| Blockeditor | Native Compose-Projektion auf ein `WorkspaceDocument` |
| Flowchart | Strenge, read-only Darstellung aus semantischer IR |
| Runtime | Fuehrt nur akzeptierte, stabile Semantik aus |

Der tolerante Macro-/Blockly-Import im Blockeditor erzeugt noch keinen
ausfuehrbaren `ScriptIr`. Er erhaelt Legacy-Struktur sichtbar und annotiert
bekannte Blöcke mit Canonical Command Metadata.

## Begriffe

- Events starten einen Workflow, zum Beispiel `EVENT.ON_START`.
- Actions beschreiben ausfuehrbare Schritte, zum Beispiel `UI.CLICK_TEXT`.
- Values/Reporter liefern Werte, zum Beispiel `TEXT_LITERAL`.
- Conditions und Kontrollfluss sind fuer den toleranten Legacy-Import v2.1 noch
  nicht freigegeben.

## Aktueller WSS-Parser-/Dry-Run-Slice

Der aktuelle WSS-Quellstand erkennt `LET`, `SET`, `WAIT`, Text-`CLICK`,
`OUTPUT`, `BEEP`, `VIBRATE`, `IF/ELSEIF/ELSE`, `LOOP` und `WHILE`
sowie numerische, String- und Boolean-Literale und die Operatoren
`+ - * / % == != < <= > >=`.

Das bedeutet Parser-/Dry-Run-Unterstuetzung, nicht automatisch Android-Real-Run.
`click`, `beep` und `vibrate` bleiben am Capability Gate blockiert, bis
ihre realen Hostadapter freigegeben sind.

## Geplante Strukturen

Die folgenden Strukturen sind reservierte oder geplante Syntax. Der
Syntax-Highlighter kann einzelne Begriffe bereits markieren; Parser, IR und
Runtime implementieren sie dadurch noch nicht:

- `SWITCH`, `CASE`, `DEFAULT`, `END SWITCH`
- `THROW`, `TRY`, `CATCH`, `FINALLY`, `END TRY`
- `ON ... END ON` fuer Ereignis- und Callbackhandler
- Funktionen und strukturierte Adapterobjekte

## Macrorify-Kompatibilitaet und Erweiterungen

Die vollständige Referenz mit Macrorify-Vergleich, dokumentierten
Unstimmigkeiten und den geplanten VisualTasker-Vertraegen fuer Try/Catch,
Custom Chrome Tabs, Tasker, Shizuku, Termux, scrcpy und Charts steht unter:

- [Macrorify EMScript and VisualTasker comparison](Macrorify-EMScript.md)

Jede dort beschriebene Familie ist als implementiert, Descriptor, Projection,
geplant oder Adapter gekennzeichnet. Die Statusangabe ist Teil des Vertrags.
