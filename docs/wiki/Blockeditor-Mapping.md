# Blockeditor Mapping

Vorläufige Dokumentation fuer v2.1 / Meilenstein 2.

Der Blockeditor importiert Macro-/Blockly-XML tolerant in ein `WorkspaceDocument`.

## Erhaltene Struktur

- Blocktyp
- direkte Fields als `field:<NAME>`
- Value-Inputs
- Statement-Inputs
- `next`-Ketten
- Original-ID und stabilisierte Block-ID
- XML-Pfad als Source-Metadata
- Root-Positionen

## Bekannte Blöcke

Bekannte Legacy-Blöcke bekommen eine Blockdefinition fuer bessere Darstellung und Canonical Command Metadata fuer spaetere Validator-/Plugin-Nutzung.

| Legacy Block | Darstellung | Struktur |
|---|---|---|
| `em_on_start` | `ON_START` | Statement `DO` |
| `em_scan_element_tree` | `SCAN ELEMENT TREE` | Previous/Next |
| `em_click_text` | `CLICK TEXT` | Value `TEXT: Text`, Previous/Next |
| `em_screenshot` | `SCREENSHOT` | Value `PATH: Text`, Previous/Next |
| `em_text` | `Text` | Reporter `Text`, Field `field:TEXT` |

## Unknown/Legacy

Unbekannte Blöcke werden nicht umgedeutet. Sie bleiben sichtbar, behalten Originaltyp, Felder, Inputs und Verkettung und werden als unsupported/legacy markiert.
