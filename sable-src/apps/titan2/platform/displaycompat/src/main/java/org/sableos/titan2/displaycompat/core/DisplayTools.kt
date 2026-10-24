package org.sableos.titan2.displaycompat.core

/** The platform side of the display tools; the Android glue reflects into WindowManager. */
interface DisplayPort {
    /** Size and density of the default display before any forced value, natural orientation. */
    fun initialSize(): SizePx
    fun initialDensity(): Int
    fun current(): CurrentDisplay

    /** Throws when the platform refuses; the caller keeps the originals so it can retry. */
    fun run(step: DisplayStep)
}

/** Display tools settings. [held] must be written synchronously (it is read back after a crash). */
interface ToolsSettings {
    var systemRotation: SystemRotation
    var favourites: Set<TallCanvas>
    var held: HeldState
}

/** What the glue reads about the moment: gate, setup, screen and the package in front. */
data class Moment(
    val gateOpen: Boolean,
    val setupComplete: Boolean,
    val interactive: Boolean,
    val frontPackage: String?
)

sealed interface CycleOutcome {
    data object GateClosed : CycleOutcome
    data object NoApp : CycleOutcome
    data object NoFavourites : CycleOutcome
    data class Blocked(val reason: String) : CycleOutcome
    data object NeedsConfirmation : CycleOutcome
    data object StorageFailed : CycleOutcome
    data class Changed(val canvas: TallCanvas, val label: String) : CycleOutcome
}

/**
 * Tall canvas, the resolution hotkey and forced rotation. Every change goes through [reconcile]:
 * the [DisplayPolicy] target, a [Reconciler] plan against what is held, then the steps. A failed
 * step keeps the originals of that plan so the next reconcile (or the next boot) restores them.
 */
class DisplayTools(
    private val port: DisplayPort,
    private val settings: ToolsSettings,
    private val store: ProfileStore,
    private val controller: ProfileController,
    private val traitsOf: (String) -> AppTraits
) {
    /** The user's own density: the forced one we hold does not count. */
    fun facts(now: CurrentDisplay = port.current()): DisplayFacts {
        val held = settings.held.density
        val density = when {
            held != null -> held.value ?: port.initialDensity()
            else -> now.forcedDensity ?: port.initialDensity()
        }
        return DisplayFacts(port.initialSize(), density)
    }

    fun situation(m: Moment): Situation {
        val front = m.frontPackage?.takeIf { PackageNames.isValid(it) }
        val profile = front?.let { pkg ->
            if (store.get(
                    pkg
                ).isNative
            ) {
                DisplayProfile.NATIVE
            } else {
                controller.load(traitsOf(pkg)).profile
            }
        } ?: DisplayProfile.NATIVE
        return Situation(
            gateOpen = m.gateOpen,
            setupComplete = m.setupComplete,
            interactive = m.interactive,
            frontPackage = front,
            frontProfile = profile,
            systemRotation = settings.systemRotation,
            anyAspectProfile = store.packages().any { DisplayPolicy.usesAspectRatio(store.get(it)) }
        )
    }

    /** Brings the display to what [m] calls for. Returns the steps that failed (empty: all done). */
    fun reconcile(m: Moment): List<DisplayStep> {
        val now = port.current()
        val target = DisplayPolicy.target(situation(m), facts(now))
        return execute(Reconciler.plan(target, settings.held, now))
    }

    /** Gives every held setting back (boot, safe mode, "Restore display now"). */
    fun restoreAll(): List<DisplayStep> =
        PlanRunner.restoreAll(port, settings.held) { settings.held = it }

    /** The service is needed only for profiles that change while their app is in front. */
    fun needsService(gateOpen: Boolean): Boolean = gateOpen &&
        DisplayPolicy.needsForegroundTracking(store.packages().map { store.get(it) })

    /** The resolution hotkey: next favourite for the app in front, saved to its profile. */
    fun cycle(m: Moment): CycleOutcome {
        val pkg = m.frontPackage
        return when {
            !m.gateOpen -> CycleOutcome.GateClosed
            pkg == null || !m.interactive || !m.setupComplete -> CycleOutcome.NoApp
            else -> cycleFor(pkg, m)
        }
    }

    private fun cycleFor(pkg: String, m: Moment): CycleOutcome {
        val traits = traitsOf(pkg)
        val loaded = controller.load(traits)
        val native = port.initialSize()
        val next = CanvasCycle.next(settings.favourites, loaded.profile.canvas, native)
        return when {
            loaded.state == StoredState.STORED_BLOCKED_NOT_EFFECTIVE ->
                CycleOutcome.Blocked(loaded.reason.orEmpty())

            next == null -> CycleOutcome.NoFavourites

            else -> saveCanvas(traits, loaded.profile.copy(canvas = next), native, m)
        }
    }

    private fun saveCanvas(
        traits: AppTraits,
        candidate: DisplayProfile,
        native: SizePx,
        m: Moment
    ): CycleOutcome {
        val next = candidate.canvas
        val outcome = controller.save(
            traits,
            candidate,
            native,
            advanced = candidate.profile == AspectProfile.Custom,
            acknowledged = false
        )
        return when (outcome.status) {
            SaveStatus.BLOCKED -> CycleOutcome.Blocked(
                outcome.validation.issues.firstOrNull { it.severity == Severity.Blocked }?.message
                    .orEmpty()
            )

            SaveStatus.NEEDS_ACK -> CycleOutcome.NeedsConfirmation

            SaveStatus.STORAGE_FAILED -> CycleOutcome.StorageFailed

            SaveStatus.STORED -> {
                reconcile(m)
                CycleOutcome.Changed(next, next.label(native))
            }
        }
    }

    private fun execute(plan: DisplayPlan): List<DisplayStep> =
        PlanRunner.run(plan, port, settings.held) { settings.held = it }
}

/** Runs a plan: originals are saved before the steps, and let go only when every step worked. */
object PlanRunner {
    fun run(
        plan: DisplayPlan,
        port: DisplayPort,
        held: HeldState,
        save: (HeldState) -> Unit
    ): List<DisplayStep> {
        if (plan.steps.isEmpty()) {
            if (plan.heldAfter != held) save(plan.heldAfter)
            return emptyList()
        }
        save(plan.heldDuring)
        val failed = plan.steps.filter { step -> runCatching { port.run(step) }.isFailure }
        if (failed.isEmpty()) save(plan.heldAfter)
        return failed
    }

    /** Boot and safe mode: give back everything [held] lists. */
    fun restoreAll(
        port: DisplayPort,
        held: HeldState,
        save: (HeldState) -> Unit
    ): List<DisplayStep> {
        if (held.isEmpty) return emptyList()
        return run(Reconciler.restoreAll(held, port.current()), port, held, save)
    }
}

/** Toast and status wording for the hotkey. */
object CycleText {
    fun message(o: CycleOutcome, appLabel: String): String = when (o) {
        CycleOutcome.GateClosed -> "Display tools are off on this build."

        CycleOutcome.NoApp -> "Open an app first, then press Alt+Shift+R."

        CycleOutcome.NoFavourites ->
            "Choose favourite sizes in Display compatibility > Display tools."

        is CycleOutcome.Blocked -> "Tall canvas is not available for $appLabel. ${o.reason}".trim()

        CycleOutcome.NeedsConfirmation ->
            "Confirm the warnings for $appLabel in Display compatibility first."

        CycleOutcome.StorageFailed -> "Not changed: the profile could not be stored."

        is CycleOutcome.Changed -> o.label + " · " + appLabel
    }
}
