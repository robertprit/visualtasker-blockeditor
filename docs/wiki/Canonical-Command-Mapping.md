# Canonical Command Mapping

Vorläufige Dokumentation fuer v2.1 / Meilenstein 2.

Canonical Command Metadata beschreibt bekannte Legacy-Blöcke in stabilen VisualTasker-Begriffen. Dieses Mapping ist ein Descriptor-Vertrag und erzeugt noch keinen ausfuehrbaren Workflow.

| Legacy Block | Canonical Command | Namespace | Command ID | Argumente |
|---|---|---|---|---|
| `em_on_start` | `EVENT.ON_START` | `EVENT` | `ON_START` | keine |
| `em_scan_element_tree` | `ACCESSIBILITY.SCAN_ELEMENT_TREE` | `ACCESSIBILITY` | `SCAN_ELEMENT_TREE` | keine |
| `em_click_text` | `UI.CLICK_TEXT` | `UI` | `CLICK_TEXT` | `TEXT: Text` |
| `em_screenshot` | `VISION.SCREENSHOT` | `VISION` | `SCREENSHOT` | `PATH: Text` |
| `em_text` | `TEXT_LITERAL` | `TEXT` | `TEXT_LITERAL` | `field:TEXT: Text` |

## Persistente Metadata

Bekannte importierte Blöcke enthalten `macro.canonical.*`-Metadaten, unter anderem:

- `macro.canonical.command`
- `macro.canonical.namespace`
- `macro.canonical.commandId`
- `macro.canonical.legacyType`
- `macro.canonical.category`
- `macro.canonical.kind`
- `macro.canonical.arguments`

Unbekannte Blöcke erhalten keine Canonical-Metadata.
