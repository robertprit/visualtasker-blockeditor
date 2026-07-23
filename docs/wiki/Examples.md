# Examples

Vorläufige Dokumentation fuer v2.1 / Meilenstein 2.

## Login-Macro

```text
ON_START
  SCAN_ELEMENT_TREE
  CLICK_TEXT "Login"
  SCREENSHOT "/sdcard/screen.png"
```

## Legacy Mapping

| Legacy Block | Canonical Command | Bedeutung |
|---|---|---|
| `em_on_start` | `EVENT.ON_START` | Start-Event |
| `em_scan_element_tree` | `ACCESSIBILITY.SCAN_ELEMENT_TREE` | UI-Baum erfassen |
| `em_click_text` | `UI.CLICK_TEXT` | Textelement anklicken |
| `em_screenshot` | `VISION.SCREENSHOT` | Screenshot erzeugen |
| `em_text` | `TEXT_LITERAL` | Textwert |

Dieses Beispiel ist eine Dokumentationsdarstellung. v2.1 fuehrt daraus keine neue Runtime-Ausfuehrung ein.
