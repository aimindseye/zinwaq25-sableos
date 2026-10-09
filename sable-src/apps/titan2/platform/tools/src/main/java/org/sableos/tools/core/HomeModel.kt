package org.sableos.tools.core

data class HomeItem(val tool: Tool, val badge: Badge)

data class HomeGroup(val title: String, val section: Section?, val items: List<HomeItem>)

/** Inputs for one home render; everything the Android layer observed, nothing it decided. */
data class HomeInputs(
    val profile: ToolsDeviceProfile,
    val probes: Map<Hardware, Probe>,
    val developerMode: Boolean,
    val granted: Set<String> = emptySet(),
    val calibrated: Set<Capability> = emptySet(),
    val recents: List<Tool> = emptyList(),
    val filter: String = ""
)

/**
 * Sable Tools home:
 * ```
 * title + device-profile summary
 * recent/favorite tools, optional bounded row
 * Utilities
 * Diagnostics
 * Reports
 * ```
 * Only listed tools appear (no grid of unavailable hardware). Empty sections are dropped, except that Diagnostics
 * and Reports always have ungated entries.
 */
object HomeModel {
    const val MAX_RECENTS = 4
    private const val MIN_INFIX = 3

    fun isListed(tool: Tool, res: Map<Capability, Resolution>, developerMode: Boolean): Boolean {
        val cap = tool.capability
        return when {
            tool.developerOnly && !developerMode -> false
            cap == null -> true
            else -> CapabilityGate.listed(res.getValue(cap), developerMode)
        }
    }

    fun badgeFor(tool: Tool, res: Map<Capability, Resolution>, inputs: HomeInputs): Badge {
        val missing = PermissionPlan.missingFor(tool, inputs.granted).isNotEmpty()
        val cap = tool.capability
        return when {
            tool.developerOnly -> Badge.DEVELOPER_ONLY
            cap == null -> if (missing) Badge.NEEDS_PERMISSION else Badge.AVAILABLE
            else -> CapabilityGate.badge(res.getValue(cap), missing, cap in inputs.calibrated)
        }
    }

    fun build(inputs: HomeInputs): List<HomeGroup> {
        val res = CapabilityGate.resolveAll(inputs.profile, inputs.probes)
        val q = inputs.filter.trim().lowercase()
        fun item(t: Tool) = HomeItem(t, badgeFor(t, res, inputs))
        val listed = Tool.entries.filter {
            isListed(it, res, inputs.developerMode) && matches(it, q)
        }
        val groups = mutableListOf<HomeGroup>()
        if (q.isEmpty()) {
            val recents = inputs.recents.distinct().filter { it in listed }.take(MAX_RECENTS)
            if (recents.isNotEmpty()) groups.add(HomeGroup("Recent", null, recents.map(::item)))
        }
        Section.entries.forEach { s ->
            val items = listed.filter { it.section == s }.map(::item)
            if (items.isNotEmpty()) groups.add(HomeGroup(s.title, s, items))
        }
        return groups
    }

    /** Type-to-filter: every word of the query must start a word of the tool's title or keywords. */
    fun matches(tool: Tool, query: String): Boolean {
        if (query.isBlank()) return true
        val words = tool.searchText.split(' ', ',', '-', '/').filter { it.isNotEmpty() }
        return query.lowercase().split(' ').filter { it.isNotEmpty() }.all { q ->
            words.any { it.startsWith(q) } || (q.length >= MIN_INFIX && tool.searchText.contains(q))
        }
    }

    /** "Zinwa Q25 · zinwa-q25 · 3 utilities" style summary for the home title line. */
    fun profileSummary(inputs: HomeInputs, groups: List<HomeGroup>): String {
        val utilities = groups.firstOrNull { it.section == Section.UTILITIES }?.items?.size ?: 0
        val mode = if (inputs.developerMode) " · developer mode" else ""
        val u = if (utilities == 1) "1 utility" else "$utilities utilities"
        return "${inputs.profile.displayName} · ${inputs.profile.id} · $u$mode"
    }

    /** Recents list after opening [tool]: most recent first, bounded. Local view state only, not a policy store. */
    fun pushRecent(recents: List<Tool>, tool: Tool): List<Tool> = (
        listOf(tool) +
            recents.filter { it != tool }
        ).take(MAX_RECENTS)

    fun encodeRecents(recents: List<Tool>): String = recents.joinToString(",") { it.name }

    fun decodeRecents(s: String?): List<Tool> =
        s.orEmpty().split(',').mapNotNull { Tool.byName(it.trim()) }.distinct().take(MAX_RECENTS)
}
