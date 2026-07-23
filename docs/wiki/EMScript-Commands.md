# EMScript Commands

Vorläufige Dokumentation fuer v2.1 / Meilenstein 2.

Diese Seite beschreibt die Canonical Commands, die aus bekannten Legacy-Blöcken als Metadata erkannt werden. Sie ist keine neue EMScript-Syntax und keine Runtime-Freigabe.

| Canonical Command | Art | Argumente | Status |
|---|---|---|---|
| `EVENT.ON_START` | Event | keine | Descriptor v2.1 |
| `ACCESSIBILITY.SCAN_ELEMENT_TREE` | Action | keine | Descriptor v2.1 |
| `UI.CLICK_TEXT` | Action | `TEXT: Text` | Descriptor v2.1 |
| `VISION.SCREENSHOT` | Action | `PATH: Text` | Descriptor v2.1 |
| `TEXT_LITERAL` | Reporter | `field:TEXT: Text` | Descriptor v2.1 |

## Nicht durch v2.1 eingefuehrt

- Keine Runtime Handler
- Keine neue EMScript-Grammatik
- Keine Kontrollfluss-Semantik
- Keine automatische Flowchart-Konvertierung aus tolerantem XML
