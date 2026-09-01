# EMScript Overview

Vorläufige Dokumentation fuer v2.1 / Meilenstein 2.

EMScript ist die textuelle Skript- und Kompatibilitaetsschicht von VisualTasker Studio. Sie dient dazu, Workflows lesbar, testbar und zwischen Editoren uebertragbar zu machen.

Die kanonische VisualTasker-Syntax ist in [EMScript Language Spec](EMScript-Language-Spec.md) festgelegt. Parser duerfen Legacy- und Macrorify-nahe Eingaben tolerieren; Generatoren sollen langfristig nur die kanonische Form ausgeben.

## Rolle im Studio

| Ebene | Rolle |
|---|---|
| EMScript | Textuelle Darstellung und Compiler-/Validator-Ziel |
| Blockeditor | Native Compose-Projektion auf ein `WorkspaceDocument` |
| Flowchart | Strenge, read-only Darstellung aus semantischer IR |
| Runtime | Fuehrt nur akzeptierte, stabile Semantik aus |

Der tolerante Macro-/Blockly-Import im Blockeditor erzeugt noch keinen ausfuehrbaren `ScriptIr`. Er erhaelt Legacy-Struktur sichtbar und annotiert bekannte Blöcke mit Canonical Command Metadata.

## Begriffe

- Events starten einen Workflow, zum Beispiel `EVENT.ON_START`.
- Actions beschreiben ausfuehrbare Schritte, zum Beispiel `UI.CLICK_TEXT`.
- Values/Reporter liefern Werte, zum Beispiel `TEXT_LITERAL`.
- Conditions und Kontrollfluss sind fuer den toleranten Legacy-Import v2.1 noch nicht freigegeben.

## Geplante Strukturen

Die folgenden Strukturen sind geplant oder in anderen EMScript-Kontexten zu pruefen, werden durch v2.1 aber nicht neu implementiert:

- `SWITCH`
- `CASE`
- `DEFAULT`
- `ENDSWITCH`
- `THROW`
- `TRY`
- `CATCH`
- `FINALLY`
- `ENDTRY`
