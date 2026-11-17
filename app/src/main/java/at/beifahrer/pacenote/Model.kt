package at.beifahrer.pacenote

/** Ein Routenpunkt in lokalen Metern (x = Osten, y = Norden), z = Höhe, s = Meter ab Start. */
data class RoutePoint(val x: Double, val y: Double, val z: Double = 0.0, val s: Double = 0.0)

/** Eine gemeldete Gefahr, schon auf die Route projiziert. */
data class RouteHazard(val s: Double, val label: String, val fromSensor: Boolean = false)

enum class NoteType { CURVE, VERTICAL, HAZARD }
enum class Dir { LEFT, RIGHT }
enum class Link { INTO, AND }
enum class CallStyle { RALLY, PLAIN }

data class PaceNote(
    val type: NoteType,
    val s: Double,          // Beginn (Meter ab Start)
    val sApex: Double,
    val sEnd: Double,
    val dir: Dir = Dir.LEFT,
    val grade: Int = 0,     // 1 sehr eng ... 6 sehr schnell
    val hairpin: Boolean = false,
    val square: Boolean = false,
    val radius: Int = 0,
    val angle: Int = 0,
    val slope: Double = 0.0,
    val mods: MutableList<String> = mutableListOf(),
    val kind: String = "",  // für Kuppe/Senke/Gefahr
    val strength: Double = 0.0,
    val sensor: Boolean = false,
    var danger: Boolean = false,
    var dangerReasons: List<String> = emptyList(),
    var blindCrest: Boolean = false,
    var link: Link? = null,
    var distAfter: Int? = null,
    var id: Int = 0,
)

data class PaceBook(val points: List<RoutePoint>, val notes: List<PaceNote>, val length: Double)

/** Texte für Anzeige und Sprachausgabe. */
object PaceText {
    private val PLAIN = mapOf(1 to "Sehr scharfe", 2 to "Scharfe", 3 to "Mittlere", 4 to "Zügige", 5 to "Leichte", 6 to "Sehr leichte")

    fun side(n: PaceNote) = if (n.dir == Dir.LEFT) "Links" else "Rechts"

    /** Kurzer Titel für die große Karte, z. B. "Rechts 3" oder "Scharfe Rechtskurve". */
    fun title(n: PaceNote, style: CallStyle): String = when (n.type) {
        NoteType.HAZARD -> n.kind
        NoteType.VERTICAL -> n.kind.replaceFirstChar { it.uppercase() }
        NoteType.CURVE -> if (style == CallStyle.RALLY) when {
            n.hairpin -> "${side(n)} Haarnadel"
            n.square -> "${side(n)} Quadrat"
            else -> "${side(n)} ${n.grade}"
        } else when {
            n.hairpin -> "Haarnadel ${side(n).lowercase()}"
            n.square -> "${side(n)}kurve 90°"
            else -> "${PLAIN[n.grade]} ${side(n)}kurve"
        }
    }

    /** Ganzer Satz für die Stimme. */
    fun spoken(n: PaceNote, style: CallStyle): String {
        val sb = StringBuilder()
        if (n.danger) sb.append(if (style == CallStyle.RALLY) "Vorsicht! " else "Achtung, ")
        when (n.type) {
            NoteType.HAZARD -> sb.append(n.kind)
            NoteType.VERTICAL -> sb.append(if (style == CallStyle.PLAIN && n.kind == "Kuppe") "Kuppe, Sicht eingeschränkt" else n.kind)
            NoteType.CURVE -> {
                sb.append(title(n, style))
                if (n.mods.isNotEmpty()) sb.append(if (style == CallStyle.RALLY) " " else ", ").append(n.mods.joinToString(if (style == CallStyle.RALLY) " " else ", "))
            }
        }
        when {
            n.link == Link.INTO -> sb.append(if (style == CallStyle.RALLY) " in" else ", direkt danach")
            n.link == Link.AND -> sb.append(if (style == CallStyle.RALLY) " und" else ", dann")
            n.distAfter != null && style == CallStyle.RALLY -> sb.append(if (n.distAfter!! >= 500) ", lange Gerade" else ", ${n.distAfter}")
            n.distAfter != null && n.distAfter!! >= 500 -> sb.append(", danach lange Gerade")
        }
        return sb.toString()
    }

    /**
     * Satz flüssiger machen: Pausen mit Komma statt harter Ausrufezeichen, kurze Pause vor "in"/"und".
     * "Vorsicht! Links 4 in Rechts 2 zieht zu, 150" → "Vorsicht, Links 4, in Rechts 2 zieht zu, 150."
     */
    fun smooth(t: String): String = t
        .replace(Regex("!\\s*"), ", ")
        .replace(Regex(" (in|und) (Links|Rechts)"), ", $1 $2")
        .replace(Regex("\\s+,"), ",")
        .trimEnd(',', ' ')
        .let { if (it.endsWith(".")) it else "$it." }

    /** Kurzes Etikett für Vorschau-Band und Kartenmarker: "L 3", "R HN", "Kuppe", "!". */
    fun short(n: PaceNote): String = when (n.type) {
        NoteType.HAZARD -> "!"
        NoteType.VERTICAL -> if ("Kuppe" in n.kind) "Kuppe" else n.kind
        NoteType.CURVE -> (if (n.dir == Dir.LEFT) "L " else "R ") + when {
            n.hairpin -> "HN"; n.square -> "Q"; else -> n.grade.toString()
        }
    }
}
