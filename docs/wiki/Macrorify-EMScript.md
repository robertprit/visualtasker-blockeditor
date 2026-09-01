# Macrorify EMScript – vollständige Befehlsreferenz für eine eigene Engine

**Ziel:** Implementierungsreferenz für eine EMScript-kompatible Engine in VT Studio WSS  
**Quellenstand:** 1. September 2026  
**Primärquelle:** [Macrorify EMScript Documentation](https://www.kok-emm.com/docs/emscript)

Diese Referenz erfasst sämtliche im EMScript-Index aufgeführten API-Seiten: drei globale Funktionen und 45 Klassen beziehungsweise Typen. Die Beschreibungen sind auf Deutsch zusammengefasst; Namen, Signaturen und dokumentierte Standardwerte bleiben möglichst quellennah.

> **Wichtig für die Engine-Implementierung:** Die Macrorify-Dokumentation enthält einige offenkundige Tippfehler und widersprüchliche Rückgabetypen. Diese sind in Abschnitt 15 ausdrücklich markiert. Ein Parser sollte die dokumentierte Syntax akzeptieren; die Laufzeit sollte die semantisch plausiblen, normalisierten Typen verwenden.

## Inhaltsübersicht

1. Sprachkern und Syntax
2. Globale Automationsbefehle
3. Collections und Zeichenketten
4. Geometrie, Gesten und Touch
5. Bild-, Farb- und Texterkennung
6. Parameterobjekte
7. Einstellungen und Dialog-UI
8. Overlay und Bildschirmtext
9. Datum, Zeit, Mathematik und Versionen
10. Datei, Zwischenablage und Cache
11. System, Umgebung und Bildschirmaufnahme
12. Aufzeichnungen
13. Empfohlene Runtime-Aufteilung
14. Vollständigkeits-Checkliste
15. Dokumentationsfehler und Kompatibilitätsentscheidungen
16. Vergleich Macrorify ↔ VisualTasker
17. Try/Catch/Finally/Throw
18. Custom Chrome Tabs und Navigationsevents
19. Tasker: Events, Variablen, Aktionen und Profile
20. Shizuku
21. Termux
22. scrcpy
23. Charts
24. Gemeinsamer Event-/Callback-Vertrag
25. Capability-Matrix
26. Implementierungsdiagnosen

---

## 1. Sprachkern und Syntax

Quelle: [Basic Syntax](https://www.kok-emm.com/docs/emscript/basic)

EMScript ist eine dynamisch typisierte, C-ähnliche Skriptsprache. Variablen erhalten ihren Typ zur Laufzeit. Die Dokumentation unterscheidet primitive Werte (`number`, `string`, `bool`, `null`) und Referenzwerte wie `Array`, `Function` und Objekte.

### 1.1 Variablen

```emscript
var name;
var count = 3;
var enabled = true;
```

- Deklaration mit `var`.
- Variablen-Hoisting wird nicht unterstützt; eine Variable muss vor ihrer Verwendung deklariert sein.
- `null` repräsentiert keinen Wert beziehungsweise kein Objekt.

### 1.2 Arrays

```emscript
var values = [1, "two", true];
var first = values[0];
values[1] = 2;
```

- Arrays dürfen gemischte Werttypen enthalten.
- Zugriff und Zuweisung erfolgen über nullbasierte Indizes.

### 1.3 Bedingungen

```emscript
if (condition) {
    // ...
} else if (otherCondition) {
    // ...
} else {
    // ...
}

var result = condition ? valueA : valueB;
```

- `if`, `else if`, `else`.
- Ternärer Operator `?:` ab Macrorify 1.4.3.

### 1.4 Schleifen

```emscript
for (var i = 0; i < 10; i++) { }
while (condition) { }
do { } while (condition);
for (var item : array) { }
```

- Klassische `for`-, `while`- und `do … while`-Schleifen.
- `foreach`-Syntax `for (var item : array)` ab 1.4.3.

### 1.5 Funktionen und Lambdas

```emscript
fun add(a, b) {
    return a + b;
}

var operation = add;
var doubleValue = value => value * 2;
```

- Funktionen werden mit `fun` definiert.
- Funktionen sind Werte und können Variablen zugewiesen oder übergeben werden.
- Lambda-Kurzform mit `=>`.

### 1.6 Klassen

```emscript
class Example {
    var field;

    init(value) {
        field = value;
    }

    fun method() {
        return field;
    }

    static fun create(value) {
        return Example(value);
    }
}
```

- Konstruktor: `init(...)`.
- Instanzfelder und -methoden sowie statische Methoden werden unterstützt.
- `new` ist bei der Objekterzeugung optional.

### 1.7 Engine-Minimum

Für eine kompatible Basissprache sollte der Interpreter mindestens bereitstellen:

- dynamische Werte und `null`;
- lexikalische Variablenauflösung ohne Hoisting;
- Array- und Objektzugriff;
- Verzweigungen, Schleifen, `return` und Funktionsaufrufe;
- Funktionen als First-Class Values und Lambdas;
- Klassen, `init`, Instanz- und statische Member;
- Überladungsauflösung nach Argumentzahl und Laufzeittyp;
- optionale Argumente und die in dieser Referenz dokumentierten Standardwerte.

---

## 2. Globale Automationsbefehle

### 2.1 `click`

Quelle: [click](https://www.kok-emm.com/docs/reference/click)

```text
void click(point: Point, repeat: number | CParam)
```

Klickt auf einen Punkt. `repeat` kann eine Wiederholungszahl oder ein vollständig konfiguriertes `CParam` sein.

### 2.2 `swipe`

Quelle: [swipe](https://www.kok-emm.com/docs/reference/swipe)

```text
void swipe(swipePoints: SwipePoint[], repeat: number | SParam)
```

Führt eine Wischgeste entlang der übergebenen `SwipePoint`-Folge aus.

### 2.3 `wait`

Quelle: [wait](https://www.kok-emm.com/docs/reference/wait)

```text
void wait(time: number)
```

Pausiert das Skript für `time` Millisekunden.

---

## 3. Collections und Zeichenketten

### 3.1 `Array`

Quelle: [Array](https://www.kok-emm.com/docs/reference/array)

#### Feld

```text
size: number
```

#### Native Array-Operationen

```text
number push(element: any)
any    pop()
number unshift(element: any)
any    shift()
number insertAt(index: number, element: any)
number insertRange(index: number, elements: Array)
any    removeAt(index: number)
number removeRange(startIndex: number, endIndex: number)
Array  slice(startIndex: number, endIndex: number)
Array  concat(elements: Array)
number clear()
Array  clone()
```

- `push`/`unshift` geben die neue Länge zurück.
- `pop`/`shift` geben das entfernte Element zurück.
- `slice` verwendet ein exklusives Ende.
- `clone` erzeugt eine flache Kopie.
- Bei `removeAt` widersprechen sich Signatur und Beschreibung; siehe Abschnitt 15.

#### Lodash-/JavaScript-nahe Methoden, ab 1.4.3

```text
all(predicate)                 // entspricht Lodash every
any(predicate)                 // entspricht Lodash some
filter(predicate)
find(predicate)
findIndex(predicate)
findLast(predicate)
findLastIndex(predicate)
each(iteratee)
has(value)                     // entspricht includes
indexOf(value, fromIndex?)
lastIndexOf(value, fromIndex?)
join(separator?)
map(iteratee)
uniq()
reduce(iteratee, accumulator?)
reduceRight(iteratee, accumulator?)
sort(compareFunction?)
reverse()
fill(value, start?, end?)
```

Macrorify verweist für diese Gruppe auf Lodash 4.17.15 beziehungsweise für `sort` auf die JavaScript/MDN-Semantik. Die Engine sollte daher Callback-Parameter und Rückgabeverhalten entsprechend nachbilden.

### 3.2 `Map`

Quelle: [Map](https://www.kok-emm.com/docs/reference/map)

```text
Map()
```

Java-`Map`-Wrapper. Laut Dokumentation werden sämtliche Überladungen der folgenden Java-Map-Methoden unterstützt:

```text
clear()
containsKey(key)
containsValue(value)
get(key)
put(key, value)
putAll(map)
putIfAbsent(key, value)
remove(key)
remove(key, value)
replace(key, value)
replace(key, oldValue, newValue)
size()
isEmpty()
```

Zusätzliche EMScript-Methoden:

```text
any[] entries()
any[] keys()
any[] values()
```

### 3.3 `Str`

Quelle: [Str](https://www.kok-emm.com/docs/reference/str)

Java-String-Wrapper mit folgenden Methoden beziehungsweise Methodengruppen:

```text
charAt(index)
compare(value)                  // ab 1.4.3
concat(value)
contains(value)
endsWith(value)
startsWith(value)
toArray()
length()
indexOf(value, fromIndex?)
lastIndexOf(value, fromIndex?)
matches(regex)
replace(target, replacement)
replaceAll(regex, replacement)
replaceFirst(regex, replacement)
split(regex, limit?)
subString(beginIndex, endIndex?)
toLowerCase()
toUpperCase()
trim()
```

Statisch:

```text
Str.join(...)
Str.format(...)
number Str.lockMatch(string1: string, string2: string)
```

`lockMatch` liefert einen Ähnlichkeitswert von `0` bis `1`.

### 3.4 `Num`

Quelle: [Num](https://www.kok-emm.com/docs/reference/num)

```text
static number Num.parse(value: number | string, defaultValue: number = null)
```

Konvertiert eine Zahl oder Zeichenkette in `number`; bei Fehlschlag wird der Standardwert geliefert.

---

## 4. Geometrie, Gesten und Touch

### 4.1 `Point`

Quelle: [Point](https://www.kok-emm.com/docs/reference/point)

```text
Point(x: number = 0, y: number = 0)
```

Konstanten für skalierte Randbehandlung:

```text
Point.LEFT   = 1
Point.TOP    = 2
Point.RIGHT  = 4
Point.BOTTOM = 8
```

Methoden:

```text
static Point Point.scale(x: number, y: number, edges: number = 0)
number getX()
number getY()
Point offset(dx: number, dy: number = 0, noScale: bool = false)
Point noScale()
```

`edges` ist eine Bitmaske und darf durch Addition beziehungsweise bitweises ODER kombiniert werden.

### 4.2 `SwipePoint`

Quelle: [SwipePoint](https://www.kok-emm.com/docs/reference/swipepoint)

```text
SwipePoint(point: Point = Point(0, 0), hold: number = 0, speed: number = 20)
number getX()
number getY()
number getHold()
number getSpeed()
```

- `hold`: Haltezeit am Punkt in Millisekunden.
- `speed`: Pixel relativ zu einer Referenzauflösung von 1280 × 720 je 12 ms.

### 4.3 `MultiSwipe`

Quelle: [MultiSwipe](https://www.kok-emm.com/docs/reference/multiswipe)

```text
static MultiSwipeBuilder MultiSwipe.builder()
void go()
```

Ein `MultiSwipe` wird nur über den Builder erzeugt und führt mehrere Gesten gemeinsam aus.

### 4.4 `MultiSwipeBuilder`

Quelle: [MultiSwipeBuilder](https://www.kok-emm.com/docs/reference/multiswipebuilder)

```text
MultiSwipeBuilder add(swipePoints: SwipePoint[])
MultiSwipeBuilder setSParam(param: SParam)
MultiSwipe build()
```

### 4.5 `Touch`

Quelle: [Touch](https://www.kok-emm.com/docs/reference/touch)

```text
static Touch Touch.single()
static Touch Touch.multi()

Touch down(point: Point, index: number = 0)
Touch move(point: Point, index: number = 0)
Touch up(index: number = 0)
Touch dispatch()
Touch reset()
Touch swipe(swipePoint: SwipePoint)
Point[] getBetween(swipePoint: SwipePoint, point: Point)
```

Laufzeitregeln:

- Fingerindizes: `0` bis `9`.
- `single()` sendet Touch-Ereignisse unmittelbar.
- `multi()` sammelt Ereignisse; `dispatch()` sendet sie gemeinsam.
- In einer Multi-Touch-Folge hebt `up()` ohne Argument alle Finger an.
- Es darf nur eine Geste gleichzeitig laufen, und die Ereignisfolge muss gültig sein (`down` vor `move`/`up`).
- Android 8+ verwendet Accessibility Touch; Android 7 benötigt laut Dokumentation den Native Service.

### 4.6 `Region`

Quelle: [Region](https://www.kok-emm.com/docs/reference/region)

#### Konstruktoren und Konstanten

```text
Region(x: number = 0, y: number = 0, w: number = 0, h: number = 0)
Region(region: Region)

Region.LEFT   = 1
Region.TOP    = 2
Region.RIGHT  = 4
Region.BOTTOM = 8
```

#### Statische Geometrie

```text
static Region Region.deviceReg(
    padLeftPercent: number = 0,
    padTopPercent: number = 0,
    padRightPercent: number = 0,
    padBottomPercent: number = 0
)

static Region Region.macroReg(
    padLeftPercent: number = 0,
    padTopPercent: number = 0,
    padRightPercent: number = 0,
    padBottomPercent: number = 0
)

static Region Region.scale(
    x: number, y: number, w: number, h: number,
    edges: number = 0
)

static void Region.highlightOff()
```

#### Getter, Setter und Basistransformationen

```text
number getX()
number getY()
number getW()
number getH()
Point getMiddlePoint()
Match getLastMatch()
Match[] getLastMatches()

Region setX(value: number)
Region setY(value: number)
Region setW(value: number)
Region setH(value: number)
Region noScale()
```

#### Prozentuale Teilregionen

Alle liefern eine neue Region.

```text
Region left(percent: number = 0.5)
Region top(percent: number = 0.5)
Region right(percent: number = 0.5)
Region bottom(percent: number = 0.5)
Region horizontal(percent: number = 0.5)
Region vertical(percent: number = 0.5)
Region middle(percent: number = 0.5)
Region pad(
    leftPercent: number,
    topPercent: number = 0,
    rightPercent: number = 0,
    bottomPercent: number = 0
)
```

#### Pixelbasierte Teilregionen

```text
Region leftPixel(pixels: number)
Region topPixel(pixels: number)
Region rightPixel(pixels: number)
Region bottomPixel(pixels: number)
Region horizontalPixel(pixels: number)
Region verticalPixel(pixels: number)
Region middlePixel(pixels: number)
Region padPixel(
    left: number,
    top: number = 0,
    right: number = 0,
    bottom: number = 0
)
Region offset(
    dx: number,
    dy: number = 0,
    w: number = null,
    h: number = null,
    noScale: bool = false
)
```

Die Originalseiten nennen bei `horizontalPixel`, `verticalPixel` und `middlePixel` teilweise den Parameternamen `percent`; die Beschreibung definiert ihn jedoch als Pixelzahl. `offset` wird in der Dokumentation irrtümlich als `Point` typisiert; semantisch ist das Ergebnis eine `Region`.

#### Veraltete Bewegungsmethoden

```text
Region moveLeft(percent: number)
Region moveUp(percent: number)
Region moveRight(percent: number)
Region moveDown(percent: number)
Region moveLeftPixel(pixels: number)
Region moveUpPixel(pixels: number)
Region moveRightPixel(pixels: number)
Region moveDownPixel(pixels: number)
```

Alle sind als veraltet markiert; neue Implementierungen sollen `offset` verwenden, die Namen aber aus Kompatibilitätsgründen weiter akzeptieren.

---

## 5. Bild-, Farb- und Texterkennung

### 5.1 Bildsuche und Bildaktionen auf `Region`

```text
Match find(image: string | Template, timeout: number | FParam)
Match[] findMulti(image: string | Template, timeout: number | FParam)
Match[] findAll(images: string[] | Template[], timeout: number | FParam)
Match[] findAny(images: string[] | Template[], timeout: number | FParam)

Match click(
    image: string | Template,
    timeout: number | FParam,
    repeat: number | CParam
)
Match[] clickMulti(
    image: string | Template,
    timeout: number | FParam,
    repeat: number | CParam
)
Match[] clickAll(
    images: string[] | Template[],
    timeout: number | FParam,
    repeat: number | CParam
)
Match[] clickAny(
    images: string[] | Template[],
    timeout: number | FParam,
    repeat: number | CParam
)

bool wait(image: string | Template, timeout: number | FParam)
bool waitAll(images: string[] | Template[], timeout: number | FParam)
```

Semantik:

- `find`: erstes passendes Ergebnis.
- `findMulti`: mehrere Treffer desselben Templates.
- `findAll`: Erfolg nur, wenn alle gesuchten Bilder vorhanden sind.
- `findAny`: Erfolg, wenn mindestens eines vorhanden ist; Ergebnispositionen dürfen `null` enthalten.
- `click*`: jeweilige Suche plus Klick.
- `wait`/`waitAll`: wartet, bis das Bild beziehungsweise alle Bilder verschwunden sind.

### 5.2 Texterkennung und Textaktionen auf `Region`

```text
MatchText findText(text: string | Template, timeout: number | TParam)
MatchText[] findMultiText(text: string | Template, timeout: number | TParam)
MatchText[] findAllText(texts: string[] | Template[], timeout: number | TParam)
MatchText[] findAnyText(texts: string[] | Template[], timeout: number | TParam)

MatchText clickText(
    text: string | Template,
    timeout: number | TParam,
    repeat: number | CParam
)
MatchText[] clickMultiText(
    text: string | Template,
    timeout: number | TParam,
    repeat: number | CParam
)
MatchText[] clickAllText(
    texts: string[] | Template[],
    timeout: number | TParam,
    repeat: number | CParam
)
MatchText[] clickAnyText(
    texts: string[] | Template[],
    timeout: number | TParam,
    repeat: number | CParam
)

bool waitText(text: string | Template, timeout: number | TParam)
bool waitAllText(texts: string[] | Template[], timeout: number | TParam)
```

Die `All`-/`Any`-Semantik entspricht der Bildsuche.

### 5.3 OCR-Lesen, Capture und Hervorhebung auf `Region`

```text
Region highlight(
    color: string | number | Color = "#e74c3c",
    width: number,
    opacity: number = 1
)
Region highlightOff()

bool capture(imageName: string | Template)

MatchText[] read(param: string | TParam)
string readPlain(param: string | TParam)                    // veraltet
string readAsString(param: string | TParam, defaultValue: any)
number readAsNumber(param: string | TParam, defaultValue: any)
```

- `capture` speichert einen Bildausschnitt im Arbeitsspeicher unter einem Namen beziehungsweise Template.
- `read` liefert erkannte Textsegmente.
- `readPlain` ist veraltet; `readAsString` verwenden.
- Für Touch-Passthrough bei einer Hervorhebung nennt die Dokumentation unter Android 12 eine Deckkraft unter `0.8`.

### 5.4 `Match`

Quelle: [Match](https://www.kok-emm.com/docs/reference/match)

```text
Region getRegion()
Point getPoint()
number getScore()
void click(param: CParam)
```

Wird von den bildbasierten `Region.find*`-Methoden erzeugt.

### 5.5 `MatchText`

Quelle: [MatchText](https://www.kok-emm.com/docs/reference/matchtext)

```text
Region getRegion()
Point getPoint()
string getText()
void click(param: CParam)
```

Wird durch OCR-/Textsuchmethoden erzeugt.

### 5.6 `Template`

Quelle: [Template](https://www.kok-emm.com/docs/reference/template)

Templates werden über einen `TemplateBuilder` erstellt.

```text
static TemplateBuilder Template.image(value: string)
static TemplateBuilder Template.color(value: string | number | Color)
static TemplateBuilder Template.text(value: string)

static void Template.setDefaultScale(scale: number)
static void Template.setDefaultScaleX(scaleX: number)
static void Template.setDefaultScaleY(scaleY: number)

number getW()                  // kann null sein
number getH()                  // kann null sein
Template load(reload: bool)
Template clear()
TemplateBuilder mutate()
```

### 5.7 `TemplateBuilder`

Quelle: [TemplateBuilder](https://www.kok-emm.com/docs/reference/templatebuilder)

Alle Konfigurationsmethoden sind fluent und geben den Builder zurück.

```text
TemplateBuilder value(value: string)
TemplateBuilder mScore(score: number)
TemplateBuilder method(method: number)
TemplateBuilder segment(segment: number)
TemplateBuilder mask(mask: string | Template)
TemplateBuilder offset(dx: number, dy: number, noScale: bool = false)
TemplateBuilder width(width: number)
TemplateBuilder height(height: number)
TemplateBuilder scale(scale: number)
TemplateBuilder scaleX(scaleX: number)
TemplateBuilder scaleY(scaleY: number)
TemplateBuilder rotate(angle: number)
TemplateBuilder gray(enabled: bool)
TemplateBuilder threshold(value: number)
Template build()
```

Wertebereiche:

- Bildvergleich `method`: `0 = CCOEFF`, `1 = CCORR`, `2 = SQDIFF`.
- Textsegment `segment`: `0 = Wort`, `1 = Zeile`, `2 = Absatz`.
- `rotate`: Winkel im Bogenmaß.
- `threshold`: `0` bis `255`.

### 5.8 `Color`

Quelle: [Color](https://www.kok-emm.com/docs/reference/color)

```text
Color(value: number | string | Color)
```

Zeichenkettenformate: `#RRGGBB` oder `#FFRRGGBB`.

Konstanten:

```text
Color.CIE76   = 0
Color.CIE94   = 1
Color.CIE2000 = 2
Color.RGBA    = 3
```

Statisch:

```text
static Color Color.get(point: Point)
static Color[] Color.getAll(pointsOrRegion: Point[] | Region)
static number Color.compare(color1: Color, color2: Color)
static number Color.compareRGB(color1: Color, color2: Color)
static number Color.deltaE(color1: Color, color2: Color, method: number = 0)
```

Instanzmethoden:

```text
bool isSame(color: Color, method: number = 0)
bool isExact(color: Color, method: number = 0)
number value()
string valueString()
number red()
number green()
number blue()
number alpha()
```

- `isSame` verwendet laut Dokumentation eine Delta-E-Schwelle von höchstens `10`.
- `isExact` verlangt Delta E `0`.
- CIE94 und CIE2000 werden laut Dokumentation gegenwärtig nicht unterstützt, obwohl Konstanten existieren.
- Die Originalseite überschreibt im `getAll`-Signaturblock versehentlich den Namen mit `get`; für eine Engine ist `getAll` der sinnvolle öffentliche Name.

---

## 6. Parameterobjekte

Parameterobjekte unterstützen jeweils einen Konstruktor, eine Kopierkonstruktion, statische Kurzfabriken und gleichnamige fluent Instanzmethoden.

### 6.1 `CParam` – Klickparameter

Quelle: [CParam](https://www.kok-emm.com/docs/reference/cparam)

```text
CParam(
    repeat: number = 1,
    hold: number = 0,
    delay: number = 0,
    waitNext: number = 100,
    random: number = 0
)
CParam(param: CParam)
```

Statische Fabriken und Instanzsetter:

```text
CParam.repeat(value)
CParam.hold(value)
CParam.delay(value)
CParam.waitNext(value)
CParam.random(value)

param.repeat(value)
param.hold(value)
param.delay(value)
param.waitNext(value)
param.random(value)
```

### 6.2 `SParam` – Wischparameter

Quelle: [SParam](https://www.kok-emm.com/docs/reference/sparam)

```text
SParam(
    repeat: number = 1,
    delay: number = 0,
    waitNext: number = 100,
    random: number = 0
)
SParam(param: SParam)
```

Statische und Instanzmethoden:

```text
repeat(value)
delay(value)
waitNext(value)
random(value)
```

### 6.3 `FParam` – Bildsuche

Quelle: [FParam](https://www.kok-emm.com/docs/reference/fparam)

```text
FParam(timeout: number = 0, sRate: number = 3, mScore: number = 0.7)
FParam(param: FParam)
```

Statische und Instanzmethoden:

```text
timeout(value)
sRate(value)
mScore(value)
method(value)
```

`method`: `0 = CCOEFF`, `1 = CCORR`, `2 = SQDIFF`. Die Dokumentation bietet `method(...)`, führt den Wert aber nicht im Konstruktor auf.

### 6.4 `TParam` – OCR-/Textsuche

Quelle: [TParam](https://www.kok-emm.com/docs/reference/tparam)

```text
TParam(
    timeout: number = 0,
    sRate: number = 3,
    mScore: number = 0.7,
    scale: number = 1,
    mode: number = 0,
    whitelist: string = null,
    blacklist: string = null,
    caseSensitive: bool = false
)
TParam(param: TParam)
```

Konstanten:

```text
TParam.WORD  = 0
TParam.LINE  = 1
TParam.PARA  = 2
TParam.AUTO  = 3
TParam.REGEX = 4
```

Statische und Instanzmethoden:

```text
timeout(value)
sRate(value)
mScore(value)
scale(value)
mode(value)
whitelist(value)
blacklist(value)
case(value)
```

### 6.5 `RParam` – Wiedergabeparameter

Quelle: [RParam](https://www.kok-emm.com/docs/reference/rparam)

```text
RParam(
    repeat: number = 1,
    delay: number = 0,
    waitNext: number = 100,
    random: number = 0
)
RParam(param: RParam)
```

Statische und Instanzmethoden:

```text
repeat(value)
delay(value)
waitNext(value)
random(value)
```

---

## 7. Einstellungen und Dialog-UI

### 7.1 `Setting`

Quelle: [Setting](https://www.kok-emm.com/docs/reference/setting)

```text
static SettingBuilder Setting.builder()
static any Setting.get(key: string, defaultValue: any)
static void Setting.set(key: string, value: any)
static void Setting.remove(key: string)
static void Setting.clear()
static void Setting.save()
static void Setting.loadVars()
static void Setting.setDialog(dialog: Dialog)
static number Setting.show()
```

`Setting.show()` zeigt den konfigurierten Dialog. Die Ergebnissemantik folgt `Dialog.show()`.

### 7.2 `SettingBuilder`

Quelle: [SettingBuilder](https://www.kok-emm.com/docs/reference/settingbuilder)

```text
SettingBuilder add(key: string, view: View)
SettingBuilder group()
SettingBuilder groupEnd()
SettingBuilder setTitle(title: string)
SettingBuilder setPositiveButton(text: string)
SettingBuilder setNegativeButton(text: string)
Dialog build()
```

`View` ist hier der gemeinsame konzeptuelle Basistyp der folgenden UI-Elemente; eine eigene Engine kann ihn als internes Interface modellieren.

### 7.3 `Dialog`

Quelle: [Dialog](https://www.kok-emm.com/docs/reference/dialog)

```text
number show()
void preview()
```

- `show()` liefert `1` bei Speichern/Bestätigen und `null` bei Abbruch.
- Bei `preview()` widersprechen sich Signatur und Beschreibung; für die Runtime wird `void` empfohlen.

### 7.4 `TextView`

Quelle: [TextView](https://www.kok-emm.com/docs/reference/textview)

```text
TextView(text: string = null)
TextView id(value: string)
TextView text(value: string)
```

### 7.5 `EditText`

Quelle: [EditText](https://www.kok-emm.com/docs/reference/edittext)

```text
EditText(
    text: string = null,
    inputMethod: number = 0,
    hint: string = null,
    helper: string = null
)

EditText id(value: string)
EditText text(value: string)
EditText method(value: number)
EditText hint(value: string)
EditText helper(value: string)
```

Konstanten:

```text
EditText.TXT = 0
EditText.NUM = 1
```

### 7.6 `Checkbox`

Quelle: [Checkbox](https://www.kok-emm.com/docs/reference/checkbox)

```text
Checkbox(checked: bool = false, text: string = null)
Checkbox id(value: string)
Checkbox checked(value: bool)
Checkbox text(value: string)
```

### 7.7 `RadioGroup`

Quelle: [RadioGroup](https://www.kok-emm.com/docs/reference/radiogroup)

```text
RadioGroup(radios: string | string[] = null, selected: number = 0)
RadioGroup id(value: string)
RadioGroup radio(value: string | string[])
RadioGroup select(index: number)
RadioGroup dropdown(value: bool)
```

### 7.8 `ImagePicker`

Quelle: [ImagePicker](https://www.kok-emm.com/docs/reference/imagepicker)

```text
ImagePicker(name: string = null, label: string = null)
ImagePicker id(value: string)
ImagePicker name(value: string)
ImagePicker label(value: string)
```

### 7.9 `Recorder`

Quelle: [Recorder](https://www.kok-emm.com/docs/reference/recorder)

```text
Recorder(name: string = null, label: string = null)
Recorder id(value: string)
Recorder name(value: string)
Recorder label(value: string)
```

### 7.10 `TabLayout`

Quelle: [TabLayout](https://www.kok-emm.com/docs/reference/tablayout)

```text
TabLayout()
TabLayout id(value: string)
TabLayout tab(label: string, visibleIds: string[], hiddenIds: string[])
TabLayout select(index: number)
TabLayout saveSelected(value: bool)
```

---

## 8. Overlay und Bildschirmtext

### 8.1 `OnScreenText`

Quelle: [OnScreenText](https://www.kok-emm.com/docs/reference/onscreentext)

Konstruktoren:

```text
OnScreenText(
    x: number = 0,
    y: number = 0,
    w: number = -1,
    h: number = -1,
    fullscreen: bool = false
)
OnScreenText(region: Region, fullscreen: bool = false)
```

Statisch:

```text
static void OnScreenText.off()
```

Getter:

```text
number getX()
number getY()
number getW()
number getH()
string getText()
number getTextColor()
number getBackgroundColor()
Region getRegion()
number getClickState()
number getMoveState()
number getResizeState()
```

Statuswerte:

- Klick: `0 = nicht geklickt`, `1 = geklickt`; Status wird beim Lesen zurückgesetzt.
- Bewegung: `0 = nicht bewegbar`, `1 = bewegbar/unverändert`, `2 = bewegt`; Bewegungsstatus wird beim Lesen zurückgesetzt.
- Größenänderung: `0 = unverändert`, `1 = geändert`; Status wird beim Lesen zurückgesetzt.

Fluent Setter:

```text
OnScreenText setX(value: number)
OnScreenText setY(value: number)
OnScreenText setW(value: number)
OnScreenText setH(value: number)
OnScreenText setText(value: string)
OnScreenText setTextColor(value: number | string | Color)
OnScreenText setBackgroundColor(value: number | string | Color)
OnScreenText setBackgroundImage(name: string)
OnScreenText setTextSize(dp: number)
OnScreenText moveable(value: bool)
OnScreenText clickable(value: bool)
OnScreenText resizable(value: bool)
OnScreenText noScale()
OnScreenText hidden(value: bool)
```

Aktionen:

```text
void show()
void off()
```

`setText` unterstützt laut Dokumentation grundlegendes HTML.

### 8.2 `Overlay`

Quelle: [Overlay](https://www.kok-emm.com/docs/reference/overlay)

```text
static void Overlay.setOpacity(value: number)
static number Overlay.getState()
static void Overlay.setState(state: number)
static Region Overlay.getRegion()
static void Overlay.move(point: Point)
static void Overlay.spin(start: bool = true)
```

- Deckkraft: `0.1` bis `1`.
- Status: `0 = komprimiert`, `1 = erweitert`.

---

## 9. Datum, Zeit, Mathematik und Versionen

### 9.1 `Math`

Quelle: [Math](https://www.kok-emm.com/docs/reference/math)

Java-Math-Wrapper mit Konstanten und Funktionen:

```text
Math.E
Math.PI

cos(x)      sin(x)      tan(x)
acos(x)     asin(x)     atan(x)     atan2(y, x)
sqrt(x)     cbrt(x)     hypot(x, y)
ceil(x)     floor(x)    round(x)
exp(x)      pow(a, b)
log(x)      log10(x)    log1p(x)
abs(x)      max(a, b)   min(a, b)
random()
toDegrees(radians)
toRadians(degrees)
```

Zusätzlich:

```text
static number Math.randomRange(from: number, to: number)
```

### 9.2 `DateTime`

Quelle: [DateTime](https://www.kok-emm.com/docs/reference/datetime)

```text
DateTime()                              // aktueller UTC-Zeitpunkt
DateTime(value: DateTime)
DateTime(
    year: number,
    month: number = 0,
    day: number = 0,
    hour: number = 0,
    minute: number = 0,
    second: number = 0,
    millis: number = 0
)
```

Statisch:

```text
static DateTime DateTime.parse(iso8601: string)
static DateTime DateTime.fromUnixMillis(value: number)
static number DateTime.timeZoneOffset()
```

Getter:

```text
date()
year()
month()
day()
dayOfWeek()
dayOfYear()
hour()
minute()
second()
millis()
totalMillis()
```

Unveränderliche Arithmetik – jede Methode liefert ein neues Objekt:

```text
DateTime add(value: TimeSpan)
DateTime addYears(value: number)
DateTime addMonths(value: number)
DateTime addDays(value: number)
DateTime addHours(value: number)
DateTime addMinutes(value: number)
DateTime addSeconds(value: number)
DateTime addMillis(value: number)
DateTime sub(value: TimeSpan)
TimeSpan sub(value: DateTime)
string format(pattern: string)
```

Bei `format(null)` wird ISO 8601 verwendet.

### 9.3 `TimeSpan`

Quelle: [TimeSpan](https://www.kok-emm.com/docs/reference/timespan)

Konstruktoren:

```text
TimeSpan(value: TimeSpan)
TimeSpan(millis: number)
TimeSpan(seconds: number, millis: number)
TimeSpan(minutes: number, seconds: number, millis: number)
TimeSpan(hours: number, minutes: number, seconds: number, millis: number)
TimeSpan(days: number, hours: number, minutes: number, seconds: number, millis: number)
```

Statisch:

```text
static TimeSpan TimeSpan.fromDays(value: number)
static TimeSpan TimeSpan.fromHours(value: number)
static TimeSpan TimeSpan.fromMinutes(value: number)
static TimeSpan TimeSpan.fromSeconds(value: number)
```

Getter:

```text
days()
hours()
minutes()
seconds()
millis()
totalDays()
totalHours()
totalMinutes()
totalSeconds()
totalMillis()
bool isNegative()
bool isZero()
```

Unveränderliche Arithmetik:

```text
TimeSpan add(value: TimeSpan)
TimeSpan addDays(value: number)
TimeSpan addHours(value: number)
TimeSpan addMinutes(value: number)
TimeSpan addSeconds(value: number)
TimeSpan addMillis(value: number)
TimeSpan sub(value: TimeSpan)
TimeSpan mul(multiplicand: TimeSpan)
TimeSpan div(divisor: TimeSpan)
string format(pattern: string)
```

Die Dokumentation typisiert die Operanden von `mul` und `div` als `TimeSpan`; eine kompatible Implementierung sollte diese Form zunächst akzeptieren. Bei `format(null)` ist das Standardformat `(dd.hh:mm:ss)`.

### 9.4 `Stopwatch`

Quelle: [Stopwatch](https://www.kok-emm.com/docs/reference/stopwatch)

```text
Stopwatch(start: bool = false)
void start()
void stop()
void reset()
void restart()
number elapsed()
bool isRunning()
bool isElapsed(time: number)
```

Zeitwerte sind Millisekunden.

### 9.5 `Version`

Quelle: [Version](https://www.kok-emm.com/docs/reference/version)

```text
Version(
    major: number = 0,
    minor: number = 0,
    build: number = 0,
    revision: number = 0
)
Version(value: string)

number major()
number minor()
number build()
number revision()
number compare(value: Version | string)
```

`compare` liefert einen negativen Wert, `0` oder einen positiven Wert entsprechend der Versionsreihenfolge.

---

## 10. Datei, Zwischenablage und Cache

### 10.1 `File`

Quelle: [File](https://www.kok-emm.com/docs/reference/file)

Alle Pfade sind relativ zu:

```text
Android/data/com.kok_emm.mobile/files
```

API:

```text
static bool File.writeText(path: string, content: string)
static bool File.writeLines(path: string, lines: string[])
static bool File.appendText(path: string, content: string)
static bool File.appendLines(path: string, lines: string[])
static string File.readText(path: string)
static string[] File.readLines(path: string)
static string File.separator()
static bool File.copy(source: string, destination: string, overwrite: bool = false)
static bool File.delete(path: string)
static bool File.exists(path: string)
static bool File.isDir(path: string)
static bool File.mkdirs(path: string)
static string[] File.list(path: string)
```

### 10.2 `Clipboard`

Quelle: [Clipboard](https://www.kok-emm.com/docs/reference/clipboard)

```text
static void Clipboard.clear()
static void Clipboard.copy(text: string)
static string Clipboard.paste(text: string)
```

Die Zwischenablage arbeitet laut Dokumentation über das fokussierte Accessibility-Element. Die Seite ist bei Rückgabetyp und Optionalität von `copy`/`paste` nicht konsistent; siehe Abschnitt 15.

### 10.3 `Cache`

Quelle: [Cache](https://www.kok-emm.com/docs/reference/cache)

Bildschirm-Cache:

```text
static void Cache.screen()
static void Cache.screenOn()
static void Cache.screenOff()
static void Cache.screenRefresh()
```

Region-Cache:

```text
static void Cache.region(region: Region)
static void Cache.regionOn(region: Region)
static void Cache.regionOff()
static void Cache.regionRefresh()
```

Weitere Cache-Befehle:

```text
static void Cache.clearImage(name: string)
static void Cache.clearRegion(region: Region)
static void Cache.loadColor(color: number | string | Color, name: string = null)
static void Cache.loadVars()
```

---

## 11. System, Umgebung und Bildschirmaufnahme

### 11.1 `Sys`

Quelle: [Sys](https://www.kok-emm.com/docs/reference/sys)

#### Benachrichtigung, Medien und Ausgabe

```text
static void Sys.noti(name: string = null)
static void Sys.playMedia(
    name: string,
    duration: number = -1,
    interval: number = 0,
    repeat: number = 1
)
static void Sys.toast(message: string)
static void Sys.alert(message: string)
static void Sys.log(message: string)
static void Sys.err(message: string)
```

- `log` ist für Debug-Ausgabe.
- `err` zeigt einen Fehler und beendet das Skript.

#### Zeit, Version und Gerätebasisdaten

```text
static number Sys.currentTime()
static number Sys.elapsedTime()
static string Sys.currentVersion()
static number Sys.sdk()
static string Sys.lang()
static number Sys.dpi()
static bool Sys.darkMode()
static any Sys.info(property: string)
```

- `currentTime`: Unix-/Wanduhrzeit in Millisekunden.
- `elapsedTime`: Millisekunden seit Gerätestart.

#### `Sys.info`-Eigenschaften

| Schlüssel | Bedeutung / Wert |
|---|---|
| `dev.isEmulator` | Emulatorstatus, `bool` |
| `dev.darkmode` | Dark-Mode-Status |
| `dev.language` | Gerätesprache |
| `dev.sdk` | Android-SDK-Level |
| `dev.touch` | `0 = Accessibility`, `1 = Native` |
| `dev.capture` | `0 = MediaProjection`, `1 = Native` |
| `battery.level` | Akkustand |
| `battery.charge` | Ladezustand |
| `battery.ac` | Laden per Netzteil |
| `battery.usb` | Laden per USB |
| `screen.dpi` | logische DPI |
| `screen.xdpi` | horizontale physische DPI |
| `screen.ydpi` | vertikale physische DPI |
| `screen.state` | Bildschirmstatus |
| `screen.timeout` | Bildschirm-Timeout |
| `screen.cutouts` | Bildschirm-Cutouts |
| `screen.realCutouts` | reale Cutouts |
| `screen.insets` | System-Inset-Werte |
| `screen.rotation` | `0..3`, gegen den Uhrzeigersinn |
| `screen.smallestWidth` | kleinste Bildschirmbreite |
| `memory.total` | gesamter Speicher |
| `memory.free` | freier Speicher |
| `memory.threshold` | Low-Memory-Schwelle |
| `memory.low` | Low-Memory-Status, semantisch `bool` |

#### Globale Geräteaktionen und Apps

```text
static void Sys.globalAction(action: string)
static void Sys.openApp(packageName: string, activity: string = null)
static void Sys.openUrl(url: string, newTab: bool)
static void Sys.toggleFeature(feature: number, enable: bool)
static void Sys.wake()
static void Sys.stop()
```

Unterstützte `globalAction`-Werte:

```text
back
home
recent
noti
lockscreen
powerdialog
quicksetting
screenshot
```

`toggleFeature`:

- `0`: `Color.get` aktivieren/deaktivieren.
- `1`: `Color.getAll` aktivieren/deaktivieren.

Android 11+ kann bei `openApp` wegen Package Visibility die Angabe der Activity erfordern.

#### Veraltet

```text
static void Sys.setControlOpacity(value: number)
```

Stattdessen `Overlay.setOpacity` verwenden.

### 11.2 `Env`

Quelle: [Env](https://www.kok-emm.com/docs/reference/env)

Koordinaten, Auflösungen und Skalierung:

```text
static number Env.macroX()
static void Env.setMacroX(value: number)
static number Env.macroY()
static void Env.setMacroY(value: number)
static number Env.deviceX()
static number Env.deviceY()
static number Env.deviceW()
static number Env.deviceH()
static number Env.scale()
static void Env.setScale(value: number)
static void Env.setCompareWidth(value: number)
```

Debug und Abschlussmeldungen:

```text
static bool Env.isDebug()
static void Env.setDebug(value: bool)
static void Env.setMessageDone(message: string)
static void Env.setMessageStop(message: string)
static void Env.setMessageError(message: string)
```

Cutouts:

```text
static void Env.setMacroCutouts(
    left: number | number[], top: number, right: number, bottom: number
)
static void Env.setDeviceCutouts(
    left: number | number[], top: number, right: number, bottom: number
)
static number[] Env.cutouts()           // veraltet
```

Für Cutout-Abfragen `Sys.info(...)` verwenden. Die Originalsignatur von `Env.scale()` nennt irrtümlich `void`; die Beschreibung definiert eine Getter-Funktion.

### 11.3 `ScreenCapture`

Quelle: [ScreenCapture](https://www.kok-emm.com/docs/reference/screencapture)

```text
static bool ScreenCapture.stop()
static bool ScreenCapture.isRunning()
```

### 11.4 `Con`

Quelle: [Con](https://www.kok-emm.com/docs/reference/console)

```text
static void Con.out(message: string)
static string Con.in()
```

Konsolen-Ausgabe und -Eingabe.

---

## 12. Aufzeichnungen

### 12.1 `Record`

Quelle: [Record](https://www.kok-emm.com/docs/reference/record)

```text
static void Record.play(recordName: string, param: RParam)
```

Spielt eine zuvor erstellte Aufzeichnung mit `RParam`-Konfiguration ab.

---

## 13. Empfohlene Runtime-Aufteilung

Für VT Studio WSS bietet sich eine klare Trennung an:

| Schicht | Typen/Befehle | Implementierungsart |
|---|---|---|
| Sprachkern | Variablen, Kontrollfluss, Funktionen, Klassen | Parser, AST/Bytecode, Interpreter |
| Reine Standardbibliothek | `Array`, `Map`, `Str`, `Num`, `Math`, `DateTime`, `TimeSpan`, `Stopwatch`, `Version` | plattformunabhängig |
| Wertobjekte | `Point`, `SwipePoint`, `Region`, `Color`, `Match`, `MatchText` | immutable oder kontrolliert mutierbar |
| Builder/Parameter | `CParam`, `SParam`, `FParam`, `TParam`, `RParam`, `TemplateBuilder`, `MultiSwipeBuilder`, `SettingBuilder` | fluent API, Copy-on-write optional |
| Android-Hostbindung | `click`, `swipe`, `Touch`, `Sys`, `Env`, `Overlay`, `ScreenCapture`, `Clipboard` | Adapter/Capabilities |
| Vision/OCR | `Template`, `Region.find*`, `Region.read*`, `Color.get*`, `Cache` | OpenCV/OCR/Screenshot-Provider |
| Persistenz | `File`, `Setting`, `Record` | Sandbox-Dateisystem/Storage-Adapter |
| UI | `Dialog`, `TextView`, `EditText`, `Checkbox`, `RadioGroup`, `ImagePicker`, `Recorder`, `TabLayout`, `OnScreenText` | Compose-/Overlay-Adapter |

### 13.1 Empfohlene Aufrufauflösung

1. Member anhand von statischem/instanzbezogenem Empfänger suchen.
2. Kandidaten nach Argumentzahl einschließlich Standardargumenten filtern.
3. Union-Typen nach Laufzeitwert auflösen, beispielsweise `number | CParam`.
4. Kopierkonstruktoren vor generischen Konvertierungen bevorzugen.
5. `null` nur dort akzeptieren, wo Standardwert oder Dokumentation dies zulässt.
6. Host-Funktionen über Capability-Interfaces ausführen, damit Desktop-Tests ohne Android möglich bleiben.

### 13.2 Sinnvolle Host-Interfaces

```text
GestureHost
ScreenCaptureHost
VisionHost
OcrHost
OverlayHost
AccessibilityHost
SystemHost
FileHost
SettingsHost
RecordHost
```

So bleibt die Sprachruntime testbar, während Android-/Compose-spezifische Funktionen austauschbar sind. Praktischer Nebeneffekt: Nicht jede Unit-Test-Ausführung muss so tun, als wäre sie ein Telefon.

---

## 14. Vollständigkeits-Checkliste

Die folgende Liste entspricht sämtlichen API-Einträgen des Macrorify-EMScript-Index:

- [x] `click`
- [x] `swipe`
- [x] `wait`
- [x] `Array`
- [x] `Map`
- [x] `Point`
- [x] `SwipePoint`
- [x] `MultiSwipe`
- [x] `MultiSwipeBuilder`
- [x] `Touch`
- [x] `Region`
- [x] `Match`
- [x] `MatchText`
- [x] `Template`
- [x] `TemplateBuilder`
- [x] `CParam`
- [x] `SParam`
- [x] `FParam`
- [x] `TParam`
- [x] `RParam`
- [x] `Setting`
- [x] `SettingBuilder`
- [x] `Dialog`
- [x] `TextView`
- [x] `EditText`
- [x] `Checkbox`
- [x] `RadioGroup`
- [x] `ImagePicker`
- [x] `Recorder`
- [x] `TabLayout`
- [x] `Record`
- [x] `OnScreenText`
- [x] `Con`
- [x] `Str`
- [x] `Num`
- [x] `Color`
- [x] `DateTime`
- [x] `TimeSpan`
- [x] `Stopwatch`
- [x] `Clipboard`
- [x] `Overlay`
- [x] `File`
- [x] `Sys`
- [x] `Cache`
- [x] `ScreenCapture`
- [x] `Env`
- [x] `Math`
- [x] `Version`

---

## 15. Dokumentationsfehler und Kompatibilitätsentscheidungen

Diese Abweichungen sollten in einer eigenen Compatibility-Matrix festgehalten und durch Tests abgesichert werden:

| Stelle | Dokumentierte Unstimmigkeit | Empfohlene Engine-Semantik |
|---|---|---|
| `Array.removeAt` | Signatur nennt `any`, Beschreibung eine neue Länge | Laufzeitverhalten des Originals testen; bis dahin `any` öffentlich beibehalten |
| `Region.offset` | Rückgabetyp wird als `Point` gezeigt | `Region` |
| `Region.horizontalPixel`, `verticalPixel`, `middlePixel` | Parameter heißt teils `percent`, Beschreibung sagt Pixel | Pixelwert |
| `Region.waitAll`, `waitAllText` | Einzelne Signaturblöcke zeigen `Match[]` | `bool` gemäß Beschreibung und Methodenfamilie |
| `FParam` | `method(...)` vorhanden, aber nicht im Konstruktor | Feld unterstützen; Konstruktor ohne zusätzliches Positionsargument |
| `Setting.set` | Signatur nennt teils `any`, Wirkung ist Setter | `void`; Rückgabe optional als Kompatibilitätsdetail |
| `Dialog.preview` | Signatur `void`, Text deutet Ergebnis an | `void` |
| `ImagePicker.label` | Rückgabetyp versehentlich `CheckBox` | `ImagePicker` |
| `Recorder.label` | Rückgabetyp versehentlich `CheckBox` | `Recorder` |
| `Color.getAll` | Überschrift `getAll`, Signatur wiederholt `get` | Methode `getAll` |
| `Env.scale` | Signatur nennt `void`, Beschreibung ist Getter | `number` |
| `Cache.regionOff` | Signaturblock wiederholt `screenOff` | Methode `regionOff` bereitstellen |
| `TimeSpan.isNegative`, `isZero` | Rückgabetyp als `number` angegeben | `bool` |
| `TimeSpan.mul`, `div` | Operand als `TimeSpan` dokumentiert | Dokumentierte Form akzeptieren; Originalverhalten vor Erweiterung auf `number` testen |
| `Clipboard.copy` | Seite deutet uneinheitlich einen String-Rückgabewert an | Seiteneffekt als Kern; `void` öffentlich |
| `Clipboard.paste` | Signatur verlangt `text`, Beschreibung lässt sinngemäß leeren/null-Wert zu | Optionales beziehungsweise `null`-Argument tolerant behandeln |
| `Sys.info("memory.low")` | Teilweise numerisch eingeordnet, semantisch Status | `bool` |

### 15.1 Kompatibilitätsstrategie

- **Strict Mode:** Nur normalisierte Signaturen, klare Typfehler bei falschen Argumenten.
- **Macrorify Mode:** Aliasnamen, veraltete Methoden und tolerante `null`-/Rückgabebehandlung zulassen.
- **Diagnostics Mode:** Bei einer dokumentierten Unstimmigkeit Warnung mit Quellmethode und gewählter Normalisierung ausgeben.
- **Golden Tests:** Kleine Referenzskripte in der Original-App ausführen und Ergebnis, Seiteneffekt sowie Fehlerverhalten gegen die eigene Engine vergleichen.

---

## 16. Macrorify EMScript und VisualTasker EMScript im Vergleich

Dieser Abschnitt trennt drei Dinge, die ähnlich heißen, aber technisch nicht identisch sind:

1. **Macrorify EMScript** ist die dynamisch typisierte, C-ähnliche Originalsprache.
2. **VisualTasker EMScript** ist eine eigene, IR-zentrierte Skript- und Projektionssprache.
3. **Macrorify-Kompatibilität** ist ein Import- und Adapterziel, keine Verpflichtung, interne Fehler oder jede Eigenheit der Originalruntime nachzubauen.

### 16.1 Statuslegende

| Status | Bedeutung |
|---|---|
| **Implementiert** | Vom aktuellen WSS-Parser erkannt und im Dry-Run ausgewertet |
| **Descriptor** | Als Block-/Import-Metadatum vorhanden, aber ohne freigegebene Runtime |
| **Projection** | Kann von einer Editor-/IR-Schicht ausgegeben werden; der Rückweg muss nicht vollständig sein |
| **Geplant** | Zielvertrag für Parser, IR, Blockeditor und Runtime |
| **Adapter** | Benötigt eine externe App, einen Dienst, Hostprozess oder eine Berechtigung |
| **Macrorify only** | Bestandteil der Original-API, noch nicht als native VisualTasker-Semantik übernommen |

### 16.2 Grundlegende Unterschiede

| Thema | Macrorify | VisualTasker |
|---|---|---|
| Sprachstil | C-ähnlich, `var`, `{ }`, `fun`, Klassen | aktuell zeilenorientiert, `LET`, `SET`, `IF … END IF` |
| Typisierung | dynamisch | Runtime-Werte dynamisch, IR-/Blockports möglichst typisiert |
| Wahrheit | Script steuert die Macrorify-Runtime | `ScriptIr`/Workflow-Domain ist semantische Wahrheit; Text, Blocks und Flowchart sind Projektionen |
| Variablen | `var name = value` | `LET name = value`, danach `SET name = value` |
| Kontrollfluss | Klammerblöcke | Abschluss mit `END IF`, `END LOOP`, `END WHILE` |
| Funktionsaufrufe | Objekt-/Methoden-API | gegenwärtig nur kleine Funktionsfamilie; Namespaces sind Zielvertrag |
| Events | Makrostart und App-interne Abläufe | explizite Event-Einstiegspunkte geplant |
| Bildsuche | hauptsächlich `Region.find*` | begriffliche Trennung `match`, `find`, `search` |
| Gesten | `Point`, `SwipePoint`, `Touch`, `MultiSwipe` | `click`, `swipe`, `path`, `touch`; `path` bleibt eigenständig |
| Import | native Macrorify-Sprache | toleranter Legacy-Import plus kanonisches Mapping; keine erfundene Semantik |
| Runtime | Android-Automationsruntime vorhanden | Real-Run weiterhin Capability-gesteuert; vieles derzeit Dry-Run oder geplant |

### 16.3 Tatsächlich im aktuellen WSS-Parser vorhanden

```text
LET name = expression
SET name = expression

WAIT 1000
wait(1000)
CLICK "Login"
click("Login")
OUTPUT expression
log(expression)
beep(frequency, durationMs, volume)
vibrate(ms1, ms2, ...)

IF condition
  ...
ELSEIF condition
  ...
ELSE
  ...
END IF

LOOP count
  ...
END LOOP

WHILE condition
  ...
END WHILE
```

Aktuell ausgewertete Literale und Operatoren:

```text
number, string, bool
+  -  *  /  %
==  !=  <  <=  >  >=
```

`LET`, `SET`, `WAIT`, Text-`CLICK`, `OUTPUT`, `BEEP`, `VIBRATE`, `IF`, `ELSEIF`, `ELSE`, `LOOP` und `WHILE` funktionieren im Parser-/Dry-Run-Slice. Das ist **keine** Aussage, dass Android-Real-Run für diese Befehle vollständig freigegeben ist.

### 16.4 Korrekturen gegenüber älteren Wiki-Entwürfen

- `TRY`, `CATCH`, `FINALLY`, `THROW` und `END TRY` sind bisher nur Syntax-Highlighter-/Planungsbegriffe; Parser, IR und Dry-Run besitzen dafür noch keine Nodes.
- `EVENT.ON_START`, `ACCESSIBILITY.SCAN_ELEMENT_TREE`, `UI.CLICK_TEXT`, `VISION.SCREENSHOT` und `TEXT_LITERAL` existieren als kanonische Legacy-Descriptoren. Descriptor bedeutet nicht Runtime.
- `AND` und `OR` können von einer Blockeditor-Projektion ausgegeben werden, fehlen aber im aktuellen WSS-Parser-Slice. Das ist eine echte Dialektlücke.
- `BREAK`, `CONTINUE`, Funktionen, Klassen, Arrays und Maps gehören noch nicht zum aktuellen WSS-Parser-Slice.
- `click(x, y)` und die vollständigen Gestenverträge sind Zielsyntax; implementiert ist gegenwärtig nur der Textklick `click("Text")` beziehungsweise `CLICK "Text"`.
- Macrorify-`var` darf beim Import nicht stillschweigend mit VisualTasker-`LET` gleichgesetzt werden, wenn Gültigkeitsbereich oder Lebensdauer nicht nachweisbar identisch sind.
- Ein unbekannter Macrorify-Block bleibt `unknown/legacy`; er wird nicht durch kreative Zuversicht plötzlich ausführbar. Computer sind in dieser Hinsicht angenehm unbeeindruckt von Optimismus.

### 16.5 Kompatibilitätsregel

```text
Macrorify source
    -> Macrorify parser/importer
    -> source-preserving compatibility IR
    -> explicit normalization
    -> VisualTasker ScriptIr
```

Native VisualTasker-Skripte nehmen den direkten Weg:

```text
VisualTasker EMScript
    -> VisualTasker parser
    -> validated ScriptIr
    -> Blockeditor / Flowchart / Runtime projections
```

---

## 17. VisualTasker-Erweiterung: `TRY`, `CATCH`, `FINALLY` und `THROW`

**Status: geplant.** Der folgende Vertrag korrigiert die bisher nur lose aufgelisteten Schlüsselwörter.

### 17.1 Syntax

```text
TRY
  statement...
CATCH error
  statement...
FINALLY
  statement...
END TRY
```

`CATCH` und `FINALLY` sind jeweils optional, mindestens eines muss vorhanden sein.

```text
THROW "Fehlermeldung"
THROW errorValue
```

### 17.2 Fehlerobjekt

Statt einer Sammlung spezieller Getter verwendet VisualTasker die allgemeine Eigenschaftsabfrage:

```text
get(error, "code")
get(error, "message")
get(error, "source")
get(error, "command")
get(error, "recoverable")
get(error, "cause")
get(error, "line")
get(error, "column")
```

### 17.3 Laufzeitregeln

- `TRY` fängt Runtime-, Adapter-, Permission-, Timeout- und explizite `THROW`-Fehler.
- Parser- und Validierungsfehler entstehen vor der Ausführung und können nicht im selben ungültigen Skript gefangen werden.
- `FINALLY` läuft nach erfolgreichem `TRY`, nach `CATCH` und beim kontrollierten Abbruch.
- Ein Fehler im `FINALLY` ersetzt den ursprünglichen Fehler nicht kommentarlos; er wird als zusätzlicher `cause`/suppressed error protokolliert.
- `Sys.stop()` beziehungsweise ein harter Benutzerabbruch darf nicht beliebig verschluckt werden.
- Ein `CATCH` ohne erneutes `THROW` markiert den Fehler als behandelt.

### 17.4 Benötigte IR-Nodes

```text
IrTry(tryBody, catchVariable?, catchBody?, finallyBody?)
IrThrow(expression)
IrErrorValue(code, message, source, command, recoverable, cause)
```

---

## 18. VisualTasker-Erweiterung: Custom Chrome Tabs

**Status: geplanter AndroidX-Browser-Adapter.**  
Namespace: `ChromeTab`

Android Custom Tabs sind kein WebView und keine allgemeine Browser-Fernsteuerung. Sie bieten Sessions, Warm-up, Prefetch, PostMessage und definierte Callbacks. Es gibt keine verlässliche Standard-API für beliebiges `back()`, `forward()`, DOM-Zugriff oder das Erzwingen der aktuellen URL.

### 18.1 Session- und Startbefehle

```text
bool ChromeTab.isSupported(packageName: string = null)
bool ChromeTab.bind(packageName: string = null)
void ChromeTab.unbind()
bool ChromeTab.warmup()

ChromeTabSession ChromeTab.create(options: Map = null)
bool ChromeTab.mayLaunchUrl(
    session: ChromeTabSession,
    url: string,
    otherUrls: string[] = []
)
ChromeTabSession ChromeTab.open(
    url: string,
    options: Map = null
)
```

Empfohlene `options`-Schlüssel:

```text
packageName
toolbarColor
navigationBarColor
colorScheme
showTitle
urlBarHiding
shareState
instantApps
initialHeightPx
initialWidthPx
closeButtonPosition
activityHeightResizeBehavior
activitySideSheetPosition
activitySideSheetDecorationType
activitySideSheetRoundedCornersPosition
```

### 18.2 Nachrichten und Origin-Validierung

```text
bool ChromeTab.requestPostMessageChannel(
    session: ChromeTabSession,
    origin: string,
    targetOrigin: string = null
)
number ChromeTab.postMessage(
    session: ChromeTabSession,
    message: string,
    extras: Map = null
)
bool ChromeTab.validateRelationship(
    session: ChromeTabSession,
    relation: number,
    origin: string
)
```

PostMessage erfordert einen vorbereiteten Nachrichtenkanal und je nach Einsatz Digital Asset Links. Nachrichtenreihenfolge muss nach dem Empfang durch VisualTasker weiter bewahrt werden.

### 18.3 Navigationsevents

Offizielle Eventcodes:

```text
ChromeTab.NAVIGATION_STARTED  = 1
ChromeTab.NAVIGATION_FINISHED = 2
ChromeTab.NAVIGATION_FAILED   = 3
ChromeTab.NAVIGATION_ABORTED  = 4
ChromeTab.TAB_SHOWN           = 5
ChromeTab.TAB_HIDDEN          = 6
```

Geplante VisualTasker-Handler:

```text
ON ChromeTab.NavigationStarted
ON ChromeTab.NavigationFinished
ON ChromeTab.NavigationFailed
ON ChromeTab.NavigationAborted
ON ChromeTab.TabShown
ON ChromeTab.TabHidden
ON ChromeTab.WarmupCompleted
ON ChromeTab.MessageChannelReady
ON ChromeTab.PostMessage
ON ChromeTab.RelationshipValidated
ON ChromeTab.ActivityResized
ON ChromeTab.ActivityLayoutChanged
ON ChromeTab.Minimized
ON ChromeTab.Unminimized
ON ChromeTab.ServiceConnected
ON ChromeTab.ServiceDisconnected
```

Jeder Handler erhält `event`. Typische Eigenschaften:

```text
get(event, "session")
get(event, "navigationEvent")
get(event, "message")
get(event, "origin")
get(event, "validated")
get(event, "width")
get(event, "height")
get(event, "bounds")
get(event, "layoutState")
get(event, "extras")
```

Die Navigationsevents garantieren nicht, dass `extras` die tatsächlich geladene URL enthält. Ein `NavigationFinished` bedeutet daher „Laden beendet“, nicht „wir besitzen jetzt eine geheime Mini-Chrome-DevTools-Schnittstelle“.

---

## 19. VisualTasker-Erweiterung: Tasker

**Status: geplanter externer Adapter.**  
Namespace: `Tasker`

Tasker unterscheidet **Profile**, **Kontexte/Events/States**, **Tasks**, **Actions** und **Variablen**. VisualTasker muss diese Ebenen erhalten, statt sie zu einer universellen „Tasker macht irgendwas“-Methode einzuschmelzen.

### 19.1 Verfügbarkeit und Tasks

```text
bool Tasker.isInstalled()
bool Tasker.isEnabled()
TaskerJob Tasker.runTask(
    name: string,
    parameters: Map = null,
    priority: number = 5,
    wait: bool = false,
    timeout: number = 0
)
bool Tasker.cancel(job: TaskerJob)
```

### 19.2 Variablen

```text
any Tasker.getVariable(name: string, defaultValue: any = null)
bool Tasker.setVariable(name: string, value: any)
bool Tasker.clearVariable(name: string)
Map Tasker.getVariables(names: string[])
```

Regeln:

- Tasker überträgt Variablen über die Plugin-/Intent-Grenze grundsätzlich als Strings.
- `%lowercase` ist Tasker-lokal, `%UPPERCASE` global; VisualTasker normalisiert das Prozentzeichen nicht weg.
- Arrays müssen als explizite Liste oder über Taskers indexierte Variablennamen abgebildet werden.
- Ein Adapter darf nicht behaupten, beliebige interne Tasker-Variablen lesen zu können, wenn Tasker sie nicht ausdrücklich übergibt.

### 19.3 Aktionen und Plugin-Aufrufe

```text
TaskerJob Tasker.action(
    action: string | number,
    arguments: Map = null,
    wait: bool = false,
    timeout: number = 0
)

TaskerJob Tasker.pluginAction(
    packageName: string,
    actionId: string,
    configuration: Map,
    timeout: number = 0
)

bool Tasker.emitEvent(name: string, payload: Map = null)
```

### 19.4 Profile

```text
bool Tasker.profileEnable(name: string)
bool Tasker.profileDisable(name: string)
bool Tasker.profileToggle(name: string)
string Tasker.profileState(name: string)
```

Diese Befehle benötigen eine ausdrücklich konfigurierte Tasker-Brücke. Ein Profil ist ein Satz von Kontexten plus Enter-/Exit-Tasks; es ist nicht dasselbe wie ein einzelnes Event.

### 19.5 Tasker-Ereignisse und Callbacks

```text
ON Tasker.EventReceived
ON Tasker.ActionRequested
ON Tasker.TaskStarted
ON Tasker.TaskFinished
ON Tasker.TaskFailed
ON Tasker.VariableChanged
ON Tasker.ProfileActivated
ON Tasker.ProfileDeactivated
```

Ereigniseigenschaften:

```text
get(event, "name")
get(event, "task")
get(event, "profile")
get(event, "variables")
get(event, "payload")
get(event, "result")
get(event, "error")
```

### 19.6 Sicherheits- und Ownership-Regeln

- Nur ausdrücklich freigegebene Tasks, Profile und Plugin-Actions dürfen aus EMScript aufgerufen werden.
- Eingehende Plugin-Events müssen Absender, Paketname und Schema validieren.
- VisualTasker-Variablen und Tasker-Variablen bleiben getrennte Namensräume.
- Zeitüberschreitungen müssen einen `TaskerJob` deterministisch abschließen; „Tasker wird schon irgendwann fertig“ ist keine Scheduling-Strategie.

---

## 20. VisualTasker-Erweiterung: Shizuku

**Status: geplanter privilegierter Adapter.**  
Namespace: `Shizuku`

Shizuku vermittelt Binder- beziehungsweise UserService-Aufrufe mit Shell- oder Root-Identität. Shizuku selbst erteilt nicht automatisch Rootrechte. Bei ADB-Start ist die typische UID `2000`, bei Root/Sui `0`; verfügbare Rechte hängen zusätzlich von Android-Version, Berechtigungen und SELinux ab.

### 20.1 Verfügbarkeit und Berechtigung

```text
bool Shizuku.isInstalled()
bool Shizuku.isAvailable()
number Shizuku.getUid()
string Shizuku.permissionState()
void Shizuku.requestPermission(requestCode: number = 0)
```

Empfohlene Permission-Zustände:

```text
UNAVAILABLE
NOT_REQUESTED
GRANTED
DENIED
DENIED_PERMANENTLY
```

### 20.2 UserService und Befehle

```text
ShizukuService Shizuku.bindUserService(
    serviceId: string,
    options: Map = null
)
bool Shizuku.unbindUserService(service: ShizukuService, remove: bool = false)

ProcessResult Shizuku.exec(
    command: string,
    args: string[] = [],
    env: Map = null,
    workingDir: string = null,
    stdin: string = null,
    timeout: number = 0
)

ProcessResult Shizuku.shell(commandLine: string, timeout: number = 0)
```

`exec(command,args)` ist gegenüber einer zusammengesetzten Shellzeile zu bevorzugen. `shell(...)` benötigt eine deutliche Warnung und darf keine ungeprüften Benutzereingaben konkatenieren.

### 20.3 Binder-/Systemservice-Zugriff

```text
BinderHandle Shizuku.systemService(name: string)
any Shizuku.call(
    handle: BinderHandle,
    interfaceName: string,
    method: string,
    arguments: any[] = []
)
```

`call` ist nur mit registrierten, typisierten AIDL-Adaptern zulässig. Ein beliebiger String ist kein sicherer Binder-Vertrag, auch wenn Reflection ihn sehr selbstbewusst aussehen lässt.

### 20.4 Ereignisse

```text
ON Shizuku.BinderReceived
ON Shizuku.BinderDead
ON Shizuku.PermissionResult
ON Shizuku.ServiceConnected
ON Shizuku.ServiceDisconnected
ON Shizuku.CommandStarted
ON Shizuku.CommandOutput
ON Shizuku.CommandError
ON Shizuku.CommandExited
```

### 20.5 Sicherheitsregeln

- Jeder privilegierte Befehl benötigt Capability, Benutzerfreigabe und Audit-Eintrag.
- Paketverwaltung, AppOps, Eingabeinjektion und Dateizugriff erhalten getrennte Capabilities.
- Root- und Shell-Backend dürfen nicht als gleichwertig behandelt werden.
- Secrets erscheinen weder in `OUTPUT` noch im Runtime-Log.
- Befehle werden als Argumentarray gespeichert; Shellquoting darf nicht erst im letzten Moment improvisiert werden.

---

## 21. VisualTasker-Erweiterung: Termux

**Status: geplanter externer Adapter.**  
Namespace: `Termux`

Der bevorzugte Android-Weg ist der offizielle `RUN_COMMAND`-Intent beziehungsweise ein Termux:Tasker-Pluginhost. Für Rückgabedaten über Java-Intents wird eine ausreichend neue Termux-Version benötigt. VisualTasker benötigt außerdem die `com.termux.permission.RUN_COMMAND`-Berechtigung und eine explizite Benutzerkonfiguration.

### 21.1 Prozessbefehle

```text
bool Termux.isInstalled()
bool Termux.canRunCommands()

TermuxJob Termux.run(
    path: string,
    args: string[] = [],
    workDir: string = null,
    stdin: string = null,
    background: bool = true,
    sessionAction: string = null,
    timeout: number = 0
)

TermuxJob Termux.shell(
    commandLine: string,
    workDir: string = null,
    background: bool = true,
    timeout: number = 0
)

bool Termux.writeStdin(job: TermuxJob, text: string)
bool Termux.cancel(job: TermuxJob)
any Termux.get(job: TermuxJob, property: string)
```

`Termux.get`-Eigenschaften:

```text
id
state
pid
stdout
stderr
exitCode
startedAt
finishedAt
timedOut
```

### 21.2 Termux:API

```text
TermuxJob Termux.api(command: string, args: string[] = [], timeout: number = 0)
```

Typische `command`-Werte des Adapters:

```text
battery-status
clipboard-get
clipboard-set
location
notification
sensor
camera-photo
torch
volume
wifi-connectioninfo
wifi-scaninfo
job-scheduler
share
open
```

Der generische Adapter bewahrt stdout/stderr und JSON-Antworten. Komfortfunktionen dürfen darauf aufbauen, aber nicht dieselbe Funktion zwanzigmal unter leicht anderem Namen verkleiden.

### 21.3 Ereignisse

```text
ON Termux.CommandStarted
ON Termux.Stdout
ON Termux.Stderr
ON Termux.CommandFinished
ON Termux.CommandFailed
ON Termux.CommandCancelled
ON Termux.CommandTimedOut
```

### 21.4 Regeln

- `run(path,args)` ist Standard; `shell(commandLine)` ist die bewusst unsichere Komfortvariante.
- Pfade, Argumente und Arbeitsverzeichnis werden separat serialisiert.
- Ausgaben erhalten Größenlimits und Backpressure.
- Hintergrundjobs überleben einen Editorwechsel, aber nicht automatisch einen Geräte-Neustart.
- Termux und Termux:API müssen aus derselben kompatiblen Installationsquelle stammen, wenn deren Signaturmodell dies verlangt.

---

## 22. VisualTasker-Erweiterung: scrcpy

**Status: geplanter Remote-Host-Adapter.**  
Namespace: `Scrcpy`

scrcpy ist primär ein Desktopprogramm, das ein per USB oder TCP/IP erreichbares Android-Gerät spiegelt und steuert. Es ist **keine** normale In-App-Android-Bibliothek. VisualTasker auf dem Telefon benötigt daher einen registrierten Desktop-/ADB-/SSH-Host, der den scrcpy-Prozess startet und kontrolliert.

### 22.1 Host und Geräte

```text
bool Scrcpy.hostAvailable(hostId: string = "default")
ScrcpyDevice[] Scrcpy.devices(hostId: string = "default")
bool Scrcpy.connect(device: string, hostId: string = "default")
bool Scrcpy.disconnect(device: string, hostId: string = "default")
```

### 22.2 Session

```text
ScrcpySession Scrcpy.start(
    device: string = null,
    options: Map = null,
    hostId: string = "default"
)
bool Scrcpy.stop(session: ScrcpySession)
bool Scrcpy.isRunning(session: ScrcpySession)
any Scrcpy.get(session: ScrcpySession, property: string)
```

Empfohlene `options`:

```text
videoCodec
audioCodec
maxSize
maxFps
videoBitRate
audioBitRate
noAudio
noVideo
noControl
record
recordFormat
turnScreenOff
stayAwake
showTouches
fullscreen
windowTitle
newDisplay
startApp
keyboard
mouse
gamepad
tcpip
otg
```

### 22.3 Kontrollbefehle

Diese Befehle benötigen einen eigenen Host-Control-Channel; sie sind keine beliebigen scrcpy-CLI-Schalter nach Sessionstart.

```text
bool Scrcpy.key(session: ScrcpySession, keyCode: string | number, action: string = "press")
bool Scrcpy.text(session: ScrcpySession, value: string)
bool Scrcpy.touch(session: ScrcpySession, action: string, point: Point, pointerId: number = 0)
bool Scrcpy.scroll(session: ScrcpySession, point: Point, dx: number, dy: number)
bool Scrcpy.setClipboard(session: ScrcpySession, value: string, paste: bool = false)
bool Scrcpy.setScreenPower(session: ScrcpySession, on: bool)
bool Scrcpy.rotate(session: ScrcpySession, direction: string = "right")
```

### 22.4 Ereignisse

```text
ON Scrcpy.HostConnected
ON Scrcpy.HostDisconnected
ON Scrcpy.DeviceConnected
ON Scrcpy.DeviceDisconnected
ON Scrcpy.SessionStarted
ON Scrcpy.VideoReady
ON Scrcpy.AudioReady
ON Scrcpy.RecordingStarted
ON Scrcpy.RecordingFinished
ON Scrcpy.ClipboardChanged
ON Scrcpy.Output
ON Scrcpy.Error
ON Scrcpy.SessionStopped
```

### 22.5 Abgrenzung

- `adb install`, `adb push`, `adb pull` und allgemeine Shellbefehle gehören in einen ADB-/Shizuku-/RemoteHost-Adapter, nicht in `Scrcpy`.
- scrcpy benötigt standardmäßig Android 5.0/API 21+, Audio-Weiterleitung Android 11+.
- OTG ist ein eigener Betriebsmodus und benötigt nicht denselben Debuggingpfad wie eine normale ADB-Session.
- Der Host muss die verwendete scrcpy-Version melden; Optionsnamen dürfen nicht blind versionsübergreifend angenommen werden.

---

## 23. VisualTasker-Erweiterung: Charts

**Status: geplanter nativer Compose-/Canvas-Renderer.**  
Namespace: `Chart`

Die API verwendet wenige Methodenfamilien und typisierte Datenmodelle. Jeder Diagrammtyp als eigene Parallelwelt wäre zwar eine solide Methode, die Toolbox in einen Baumarkt zu verwandeln, aber keine gute Engine-Architektur.

### 23.1 Diagrammtypen

```text
Chart.PIE
Chart.DONUT
Chart.BAR
Chart.STACKED_BAR
Chart.HORIZONTAL_BAR
Chart.LINE
Chart.AREA
Chart.SCATTER
Chart.BUBBLE
Chart.CANDLE
Chart.OHLC
Chart.HISTOGRAM
Chart.RADAR
Chart.HEATMAP
Chart.GAUGE
Chart.SPARKLINE
```

### 23.2 Erzeugung und Darstellung

```text
ChartHandle Chart.create(
    type: string,
    data: ChartData,
    options: Map = null
)
void Chart.show(chart: ChartHandle, target: string | Region = null)
void Chart.hide(chart: ChartHandle)
void Chart.remove(chart: ChartHandle)
bool Chart.exists(chart: ChartHandle)
```

### 23.3 Daten und Updates

```text
void Chart.setData(chart: ChartHandle, data: ChartData)
void Chart.setOptions(chart: ChartHandle, options: Map)
void Chart.add(chart: ChartHandle, series: string, value: any)
void Chart.update(chart: ChartHandle, selector: any, value: any)
void Chart.removeData(chart: ChartHandle, selector: any)
void Chart.clear(chart: ChartHandle)
any Chart.get(chart: ChartHandle, property: string)
```

### 23.4 Export

```text
Image Chart.capture(chart: ChartHandle, scale: number = 1)
bool Chart.export(
    chart: ChartHandle,
    path: string,
    format: string = "png",
    scale: number = 1
)
```

### 23.5 Datenmodelle

```text
ChartData(
    labels: string[] = [],
    series: ChartSeries[] = []
)

ChartSeries(
    name: string,
    values: any[],
    color: Color = null,
    options: Map = null
)

ChartPoint(x: number | DateTime, y: number, label: string = null)

Candle(
    time: DateTime | number,
    open: number,
    high: number,
    low: number,
    close: number,
    volume: number = null
)
```

Validierung für Candles:

```text
high >= max(open, close, low)
low  <= min(open, close, high)
```

### 23.6 Optionen

Gemeinsame Schlüssel:

```text
title
subtitle
legend
colors
background
padding
animation
duration
interactive
selectable
zoomable
pannable
minX
maxX
minY
maxY
xLabel
yLabel
grid
showValues
emptyText
```

Typspezifische Schlüssel bleiben im selben `options`-Objekt, werden aber anhand von `Chart.type` validiert: `innerRadius` für Donut, `stacked` für Bars, `smooth` für Lines, `candleWidth` und `volume` für Candles.

### 23.7 Ereignisse

```text
ON Chart.Rendered
ON Chart.Clicked
ON Chart.LongClicked
ON Chart.SelectionChanged
ON Chart.ZoomChanged
ON Chart.PanChanged
ON Chart.DataChanged
ON Chart.Error
```

Ereigniseigenschaften:

```text
get(event, "chart")
get(event, "series")
get(event, "index")
get(event, "value")
get(event, "label")
get(event, "point")
get(event, "viewport")
```

### 23.8 Renderer-Vertrag

- Datenmodell und Renderer bleiben getrennt.
- Der Renderer verwendet Compose/Canvas und Studio-Farbtokens.
- Rot/Grün darf bei Finanzcharts nicht das einzige Unterscheidungsmerkmal sein.
- Große Datenreihen werden gesampelt oder virtualisiert; das Originalmodell bleibt unverändert.
- Auswahl, Zoom und Pan erzeugen Events und verändern nicht stillschweigend die zugrunde liegenden Daten.

---

## 24. Gemeinsamer Event- und Callback-Vertrag

**Status: geplant.** Dieser Vertrag gilt für ChromeTab, Tasker, Shizuku, Termux, scrcpy und Chart.

### 24.1 Handler-Syntax

```text
ON Namespace.EventName
  statement...
END ON
```

Innerhalb des Handlers ist `event` eine unveränderliche strukturierte Variable.

```text
ON Termux.CommandFinished
  IF get(event, "exitCode") == 0
    OUTPUT get(event, "stdout")
  ELSE
    THROW get(event, "stderr")
  END IF
END ON
```

### 24.2 Gemeinsame Eventfelder

```text
get(event, "id")
get(event, "type")
get(event, "source")
get(event, "timestamp")
get(event, "correlationId")
get(event, "payload")
get(event, "error")
```

### 24.3 Dispatch-Regeln

- Events werden pro Quelle in Reihenfolge zugestellt.
- Jeder Handler erhält eine eigene unveränderliche Event-Snapshot-Instanz.
- Lang laufende Handler blockieren nicht den Adapterthread.
- Rekursive Eventkaskaden besitzen Tiefen- und Schrittlimits.
- Nicht behandelte Callbackfehler landen im strukturierten Runtime-Log.
- Externe Events werden erst nach Schema-, Absender- und Capability-Prüfung in `ScriptIr`-Events umgewandelt.

---

## 25. Erweiterte Capability-Matrix

| Familie | Parser | IR | Dry-Run | Real-Run | Abhängigkeit |
|---|---:|---:|---:|---:|---|
| `LET`/`SET`/Ausdrücke | ja | ja | ja | Scheduler offen | keine |
| `IF`/`LOOP`/`WHILE` | ja | ja | ja | Scheduler offen | keine |
| Text-`CLICK` | ja | ja | ja | blockiert | Accessibility/Shizuku |
| `BEEP`/`VIBRATE` | ja | ja | ja | blockiert | Feedback-Bridge |
| Macrorify-Komplett-API | Importziel | teilweise | nein | nein | Compatibility Layer |
| `TRY/CATCH` | nein | nein | nein | nein | Parser + IR + Runtime |
| `ChromeTab` | nein | nein | nein | nein | AndroidX Browser |
| `Tasker` | nein | nein | nein | nein | Tasker/Plugin-Bridge |
| `Shizuku` | nein | nein | nein | nein | Shizuku + Permission |
| `Termux` | nein | nein | nein | nein | Termux RUN_COMMAND |
| `Scrcpy` | nein | nein | nein | nein | Remote Host + scrcpy |
| `Chart` | nein | nein | nein | nein | Compose Renderer |

Für jede neue Familie ist dieselbe Reihenfolge verbindlich:

```text
Syntax -> AST -> ScriptIr -> Validator -> Blockdefinition -> Generator
       -> Dry-Run -> Capability Gate -> Real-Run Adapter -> Tests -> Dokumentation
```

Nicht umgekehrt. Ein hübscher Toolbox-Block ohne IR-Vertrag ist lediglich ein farbiges Versprechen.

---

## 26. Zusätzliche Implementierungsdiagnosen

```text
UNSUPPORTED_DIALECT_FEATURE
MACRORIFY_NORMALIZATION_REQUIRED
CALLBACK_NOT_SUPPORTED
EVENT_SCHEMA_MISMATCH
ADAPTER_NOT_INSTALLED
ADAPTER_NOT_CONNECTED
CAPABILITY_NOT_GRANTED
PERMISSION_DENIED
PRIVILEGE_LEVEL_INSUFFICIENT
REMOTE_HOST_UNAVAILABLE
COMMAND_TIMED_OUT
COMMAND_CANCELLED
OUTPUT_LIMIT_EXCEEDED
UNSAFE_SHELL_ARGUMENT
INVALID_CHART_DATA
UNSUPPORTED_CHART_OPTION
```

Jede Diagnose enthält mindestens:

```text
code
severity
message
sourceRange oder blockId
command
adapter
recoverable
suggestedAction
```

---

## Quellen

### Macrorify

- [Macrorify EMScript – Übersicht und vollständiger Referenzindex](https://www.kok-emm.com/docs/emscript)
- [Macrorify EMScript – Basic Syntax](https://www.kok-emm.com/docs/emscript/basic)
- Die jeweiligen offiziellen Referenzseiten sind in jedem Macrorify-Abschnitt direkt verlinkt.

### VisualTasker-Quellstand

- [VisualTasker Blockeditor – EMScript Generator](https://github.com/robertprit/visualtasker-blockeditor/blob/feature/public-host-api/blockeditor-emscript/src/main/kotlin/de/visualtasker/blockeditor/emscript/EmscriptGenerator.kt)
- [VisualTasker Studio WSS – Parser Slice](https://github.com/robertprit/VisualTasker-Studio-WSS/blob/main/app/src/main/java/com/visualtasker/wss/emscript/parser/EmscriptParserSlice.kt)
- [VisualTasker Studio WSS – Dry-Run Runtime](https://github.com/robertprit/VisualTasker-Studio-WSS/blob/main/app/src/main/java/com/visualtasker/wss/emscript/runtime/EmscriptDryRunRuntime.kt)
- [VisualTasker Studio WSS – Capability Gate](https://github.com/robertprit/VisualTasker-Studio-WSS/blob/main/app/src/main/java/com/visualtasker/wss/emscript/runtime/RuntimeCapabilityGate.kt)

### Externe Adapter

- [AndroidX `CustomTabsCallback`](https://developer.android.com/reference/androidx/browser/customtabs/CustomTabsCallback)
- [Tasker Plugin Introduction](https://tasker.joaoapps.com/plugins-intro.html)
- [Tasker Variables](https://tasker.joaoapps.com/userguide/en/variables.html)
- [Shizuku API](https://github.com/RikkaApps/Shizuku-API)
- [Termux `RUN_COMMAND` Intent](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Termux:API](https://github.com/termux/termux-api)
- [Termux:Tasker](https://github.com/termux/termux-tasker)
- [scrcpy](https://github.com/Genymobile/scrcpy)
