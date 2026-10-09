package org.sableos.titan2.displaycompat.core

/** Per-user per-package storage. Native profiles are never stored: absence means "do nothing". */
interface ProfileStore {
    fun get(pkg: String): DisplayProfile
    fun put(pkg: String, p: DisplayProfile)
    fun reset(pkg: String)
    fun resetAll()
    fun packages(): Set<String>
}

class InMemoryProfileStore : ProfileStore {
    private val map = linkedMapOf<String, DisplayProfile>()
    override fun get(pkg: String) = map[pkg] ?: DisplayProfile.NATIVE
    override fun put(pkg: String, p: DisplayProfile) {
        if (p.isNative) {
            map.remove(pkg)
        } else {
            map[pkg] =
                p
        }
    }
    override fun reset(pkg: String) {
        map.remove(pkg)
    }
    override fun resetAll() = map.clear()
    override fun packages(): Set<String> = map.keys.toSet()
}

/** Result of asking a backend to apply a profile. Never throws into the UI. */
sealed interface ApplyResult {
    data object NoOp : ApplyResult
    data class Applied(val detail: String) : ApplyResult
    data class Unsupported(val reason: String) : ApplyResult
}

interface DisplayBackend {
    val name: String
    val availability: BackendAvailability
    fun apply(pkg: String, plan: CanvasPlan, p: DisplayProfile): ApplyResult
    fun clear(pkg: String): ApplyResult
}

/** N0 default: stores state and validates only. N0_CAN_FORCE_ASPECT_RATIO_WITHOUT_VALIDATION=NO. */
object NoOpBackend : DisplayBackend {
    override val name = "no-op (N0)"
    override val availability = BackendAvailability.CLOSED
    override fun apply(pkg: String, plan: CanvasPlan, p: DisplayProfile) = ApplyResult.NoOp
    override fun clear(pkg: String) = ApplyResult.NoOp
}

/** Orchestrates store, validation and backend. A profile that fails validation is never saved or applied. */
class ProfileController(
    private val store: ProfileStore,
    private val backend: DisplayBackend,
    private val miniSize: SizePx? = null
) {
    data class Outcome(
        val validation: Validation,
        val saved: Boolean,
        val result: ApplyResult?,
        val status: SaveStatus = if (saved) SaveStatus.STORED else SaveStatus.BLOCKED,
        val nativeProfile: Boolean = false
    ) {
        /** Never [EnforcementState]-claims more than the backend reported. */
        val enforcement: EnforcementState get() = EnforcementState.of(nativeProfile, result)

        private fun firstBlockedMessage(): String =
            validation.issues.firstOrNull { it.severity == Severity.Blocked }?.message.orEmpty()

        fun message(): String = when (status) {
            SaveStatus.BLOCKED -> "Not saved: blocked. " + firstBlockedMessage()

            SaveStatus.NEEDS_ACK -> "Not saved: acknowledge the warnings first."

            SaveStatus.STORAGE_FAILED ->
                "Not saved: the profile could not be stored. Nothing was changed."

            SaveStatus.STORED -> "Saved. " + EnforcementText.describe(enforcement, result)
        }
    }

    val backendAvailability: BackendAvailability get() = backend.availability

    fun save(
        traits: AppTraits,
        p: DisplayProfile,
        display: SizePx,
        advanced: Boolean,
        acknowledged: Boolean
    ): Outcome {
        val v = validate(traits, p, advanced)
        val refusal = refusal(v, acknowledged)
        val stored = refusal == null && persist(traits.packageName, p)
        return when {
            refusal != null -> Outcome(v, false, null, refusal)

            !stored -> Outcome(v, false, null, SaveStatus.STORAGE_FAILED)

            else -> Outcome(
                v,
                true,
                backendCall(traits.packageName, p, display),
                SaveStatus.STORED,
                p.isNative
            )
        }
    }

    private fun validate(traits: AppTraits, p: DisplayProfile, advanced: Boolean): Validation =
        if (PackageNames.isValid(traits.packageName)) {
            ProfileValidator.validate(traits, p, advanced)
        } else {
            Validation(
                listOf(Issue(Severity.Blocked, "PACKAGE_INVALID", "The package name is not valid."))
            )
        }

    private fun refusal(v: Validation, acknowledged: Boolean): SaveStatus? = when {
        v.blocked -> SaveStatus.BLOCKED
        v.ackRequired.isNotEmpty() && !acknowledged -> SaveStatus.NEEDS_ACK
        else -> null
    }

    private fun backendCall(pkg: String, p: DisplayProfile, display: SizePx): ApplyResult =
        guarded {
            if (p.isNative) {
                backend.clear(
                    pkg
                )
            } else {
                backend.apply(pkg, CanvasPlanner.plan(display, p, miniSize), p)
            }
        }

    /** The stored profile as it must be treated now; a stored profile that is no longer allowed is not effective. */
    fun load(traits: AppTraits): LoadedProfile {
        val p = store.get(traits.packageName)
        if (p.isNative) return LoadedProfile(DisplayProfile.NATIVE, StoredState.NONE, null)
        // A stored Custom profile was accepted with advanced profiles on, so it is checked with them on.
        val blocked = ProfileValidator.validate(traits, p, advancedEnabled = true).issues
            .firstOrNull { it.severity == Severity.Blocked }
        return if (blocked == null) {
            LoadedProfile(p, StoredState.STORED, null)
        } else {
            LoadedProfile(
                DisplayProfile.NATIVE,
                StoredState.STORED_BLOCKED_NOT_EFFECTIVE,
                blocked.message
            )
        }
    }

    /** Reset works without launching the app (RESET_OUTSIDE_APP). */
    fun reset(pkg: String): ApplyResult {
        store.reset(pkg)
        return guarded { backend.clear(pkg) }
    }

    /** Like [reset], but reports whether the store and the platform were both cleared. */
    fun resetChecked(pkg: String): ResetOutcome {
        store.reset(pkg)
        val cleared = store.get(pkg).isNative
        return ResetOutcome(cleared, guarded { backend.clear(pkg) })
    }

    fun resetAll(): Int {
        val pk = store.packages()
        pk.forEach { guarded { backend.clear(it) } }
        store.resetAll()
        return pk.size
    }

    /** Like [resetAll], but lists the apps whose platform override could not be cleared. */
    fun resetAllChecked(): ResetAllOutcome {
        val pk = store.packages().sorted()
        val failures = pk.filter { guarded { backend.clear(it) } is ApplyResult.Unsupported }
        store.resetAll()
        return ResetAllOutcome(pk.size, failures)
    }

    private fun persist(pkg: String, p: DisplayProfile): Boolean = runCatching {
        store.put(pkg, p)
        store.get(pkg) == p
    }.getOrDefault(false)

    private fun guarded(call: () -> ApplyResult): ApplyResult = runCatching(call).getOrElse {
        ApplyResult.Unsupported("backend error: " + (it.message ?: it.javaClass.simpleName))
    }
}
