// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.elevated

/** Privileges for features the user has already enabled, never a blanket grant. */
internal object ElevatedGrantPolicy {
    data class Consent(
        val wirelessPaired: Boolean = false,
        val callControl: Boolean = false,
        val notificationAccess: Boolean = false,
        val bankingApps: Boolean = false,
    )

    const val SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    const val CALLS = "MANAGE_ONGOING_CALLS"
    const val SENSITIVE_NOTIFICATIONS = "RECEIVE_SENSITIVE_NOTIFICATIONS"
    const val USAGE_STATS = "GET_USAGE_STATS"

    private const val NOTIFICATION_LISTENER = "app.nectarlink.android.notifications.NotificationListener"

    fun commands(pkg: String, sdk: Int, consent: Consent): List<String> = buildList {
        // Wireless reconnection and the chosen banking-app pause need this;
        // merely starting through root, Shizuku or USB does not.
        if (consent.wirelessPaired || consent.bankingApps) add("pm grant $pkg $SECURE_SETTINGS")
        if (sdk >= 31 && consent.callControl) add("appops set $pkg $CALLS allow")
        if (sdk >= 35 && consent.notificationAccess) {
            add("appops set $pkg $SENSITIVE_NOTIFICATIONS allow")
            add("cmd notification allow_listener $pkg/$NOTIFICATION_LISTENER")
        }
        if (consent.bankingApps) add("appops set $pkg $USAGE_STATS allow")
    }

    /** The exact grant commands accepted by the helper and Shizuku service. */
    fun isGrant(command: String, pkg: String): Boolean =
        command in commands(pkg, 35, Consent(true, true, true, true))
}

/** Avoid retry loops while allowing a feature to be disabled and enabled again. */
internal class ElevatedGrantRequests {
    private val requested = mutableSetOf<String>()

    @Synchronized
    fun next(commands: List<String>): List<String> {
        requested.retainAll(commands.toSet())
        return commands.filter { requested.add(it) }
    }

    @Synchronized
    fun retry(commands: List<String>) {
        requested.removeAll(commands.toSet())
    }

    @Synchronized
    fun clear() {
        requested.clear()
    }
}
