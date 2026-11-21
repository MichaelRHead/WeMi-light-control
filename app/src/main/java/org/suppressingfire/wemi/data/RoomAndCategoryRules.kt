package org.suppressingfire.wemi.data

/**
 * Declarative keyword tables and pre-compiled word-boundary regular expressions backing
 * [normalizeDeviceNameForInference], [classifyDeviceCategory], [isHolidayLightDevice], and
 * [inferRoomFromName] in [DeviceEntities.kt].
 */
internal object RoomAndCategoryRules {

    val ACRONYM_CAMEL_BOUNDARY_REGEX = Regex("([A-Z]+)([A-Z][a-z])")
    val CAMEL_CASE_BOUNDARY_REGEX = Regex("([a-z])([A-Z])")
    val DELIMITER_NORMALIZE_REGEX = Regex("[_\\-/.]+")
    val MULTI_SPACE_REGEX = Regex("\\s+")

    private val WORD_TREE_REGEX = Regex("\\btrees?\\b")
    private val WORD_FIR_REGEX = Regex("\\bfir\\b")
    private val WORD_FAN_REGEX = Regex("\\bfans?\\b")
    private val WORD_VENT_REGEX = Regex("\\bvents?\\b")
    private val WORD_LED_REGEX = Regex("\\bleds?\\b")

    private val CHRISTMAS_TREE_KEYWORDS = listOf(
        "xmas", "evergreen", "spruce", "garland", "wreath", "tinsel", "noel"
    )
    private val CHRISTMAS_TREE_WORD_REGEXES = listOf(WORD_TREE_REGEX, WORD_FIR_REGEX)
    private val CHRISTMAS_LIGHT_EXCLUSIONS = listOf("candle", "lamp", "sconce", "lantern")

    val HOLIDAY_LIGHT_KEYWORDS = listOf(
        "holiday", "christmas", "xmas", "diwali", "dewali", "deepavali",
        "hanukkah", "chanukah", "menorah", "kwanzaa", "festive", "festival",
        "halloween", "spooky", "pumpkin", "lunar", "new year", "valentine",
        "easter", "twinkle", "fairy", "icicle", "string light"
    )

    private val FAN_KEYWORDS = listOf("blower", "exhaust", "ventilat", "circulator")
    private val FAN_WORD_REGEXES = listOf(WORD_FAN_REGEX, WORD_VENT_REGEX)

    private val LIGHT_KEYWORDS = listOf(
        "lamp", "light", "bulb", "candle", "sconce", "chandelier", "lantern", "pendant",
        "flood", "spot", "downlight", "uplight", "strip", "neon", "torch", "dimmer", "fixture"
    )
    private val LIGHT_WORD_REGEXES = listOf(WORD_LED_REGEX)

    val MOTION_KEYWORDS = listOf("motion", "sensor")

    fun matchesChristmasTree(normalized: String): Boolean =
        CHRISTMAS_TREE_KEYWORDS.any { it in normalized } ||
            CHRISTMAS_TREE_WORD_REGEXES.any { it.containsMatchIn(normalized) } ||
            ("christmas" in normalized && CHRISTMAS_LIGHT_EXCLUSIONS.none { it in normalized })

    fun matchesFan(normalized: String): Boolean =
        FAN_KEYWORDS.any { it in normalized } ||
            FAN_WORD_REGEXES.any { it.containsMatchIn(normalized) }

    fun matchesLight(normalized: String): Boolean =
        LIGHT_KEYWORDS.any { it in normalized } ||
            LIGHT_WORD_REGEXES.any { it.containsMatchIn(normalized) }

    fun matchesMotion(normalized: String): Boolean =
        MOTION_KEYWORDS.any { it in normalized }

    fun matchesHolidayLight(normalized: String): Boolean =
        HOLIDAY_LIGHT_KEYWORDS.any { it in normalized } ||
            CHRISTMAS_TREE_KEYWORDS.any { it in normalized } ||
            CHRISTMAS_TREE_WORD_REGEXES.any { it.containsMatchIn(normalized) }

    private val WORD_BED_REGEX = Regex("\\bbed\\b")
    private val WORD_GIRL_REGEX = Regex("\\bgirl\\b")
    private val WORD_BOY_REGEX = Regex("\\bboy\\b")
    private val WORD_KID_REGEX = Regex("\\bkid\\b")
    private val WORD_HALL_REGEX = Regex("\\bhall\\b")
    private val WORD_STAIR_PREFIX_REGEX = Regex("\\bstair")
    private val WORD_PIER_REGEX = Regex("\\bpier\\b")
    private val WORD_SHED_REGEX = Regex("\\bshed\\b")
    private val WORD_DEN_REGEX = Regex("\\bden\\b")
    private val WORD_WC_REGEX = Regex("\\bwc\\b")

    data class RoomInferenceRule(
        val roomName: String,
        val keywords: List<String>,
        val wordRegexes: List<Regex> = emptyList()
    ) {
        fun matches(normalized: String): Boolean =
            keywords.any { it in normalized } || wordRegexes.any { it.containsMatchIn(normalized) }
    }

    val ROOM_INFERENCE_RULES = listOf(
        RoomInferenceRule(
            roomName = "Kitchen",
            keywords = listOf(
                "kitchen", "pantry", "island", "breakfast", "nook",
                "undercabinet", "under cabinet", "countertop"
            )
        ),
        RoomInferenceRule(
            roomName = "Dining Room",
            keywords = listOf("dining", "buffet", "hutch", "china cabinet")
        ),
        RoomInferenceRule(
            roomName = "Ensuite",
            keywords = listOf(
                "ensuite", "en suite", "bath", "powder", "vanity", "restroom", "lavatory", "shower"
            ),
            wordRegexes = listOf(WORD_WC_REGEX)
        ),
        RoomInferenceRule(
            roomName = "Hallway & Landing",
            keywords = listOf(
                "hallway", "landing", "foyer", "entry",
                "vestibule", "mudroom", "mud room", "corridor", "breezeway"
            ),
            wordRegexes = listOf(WORD_HALL_REGEX, WORD_STAIR_PREFIX_REGEX)
        ),
        RoomInferenceRule(
            roomName = "Bedroom",
            keywords = listOf(
                "bedroom", "bedside", "nightstand", "night stand", "headboard", "dresser", "wardrobe",
                "crib", "cradle", "loft", "master", "primary", "nursery", "guest", "bunk", "dorm",
                "girls", "boys", "kids", "children"
            ),
            wordRegexes = listOf(WORD_BED_REGEX, WORD_GIRL_REGEX, WORD_BOY_REGEX, WORD_KID_REGEX)
        ),
        RoomInferenceRule(
            roomName = "Outdoor",
            keywords = listOf(
                "patio", "porch", "veranda", "lanai", "outdoor", "outside", "exterior", "garden",
                "dock", "yard", "deck", "flood", "walkway", "sidewalk", "pathway", "balcony",
                "terrace", "gazebo", "pergola", "trellis", "pool", "fountain", "landscape"
            ),
            wordRegexes = listOf(WORD_PIER_REGEX)
        ),
        RoomInferenceRule(
            roomName = "Garage",
            keywords = listOf(
                "garage", "workshop", "workbench", "driveway", "carport", "toolshed", "woodshed", "barn"
            ),
            wordRegexes = listOf(WORD_SHED_REGEX)
        ),
        RoomInferenceRule(
            roomName = "Basement",
            keywords = listOf(
                "basement", "cellar", "laundry", "utility", "boiler", "furnace", "sump", "crawlspace",
                "rec room", "game room", "media room", "theater", "theatre"
            )
        ),
        RoomInferenceRule(
            roomName = "Office",
            keywords = listOf("office", "study", "desk", "workstation", "studio", "library")
        ),
        RoomInferenceRule(
            roomName = "Living Room",
            keywords = listOf(
                "living", "family", "lounge", "great room", "parlor", "parlour", "sitting",
                "sunroom", "sun room", "solarium", "mantel", "hearth", "fireplace", "couch", "sofa"
            ),
            wordRegexes = listOf(WORD_DEN_REGEX)
        ),
        RoomInferenceRule(
            roomName = "Hallway & Landing",
            keywords = listOf("upstairs", "downstairs")
        )
    )
}
