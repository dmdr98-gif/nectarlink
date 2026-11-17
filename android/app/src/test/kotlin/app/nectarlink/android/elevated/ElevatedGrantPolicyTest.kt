// SPDX-License-Identifier: GPL-3.0-or-later
package app.nectarlink.android.elevated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevatedGrantPolicyTest {
    private val pkg = "io.github.dmdr98gif.everywhere"

    @Test
    fun startingAHelperAloneDoesNotGrantAnyAppPrivileges() {
        assertTrue(ElevatedGrantPolicy.commands(pkg, 35, ElevatedGrantPolicy.Consent()).isEmpty())
    }

    @Test
    fun wirelessPairingDoesNotGrantUnrelatedFeatures() {
        assertEquals(
            listOf("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"),
            ElevatedGrantPolicy.commands(pkg, 35, ElevatedGrantPolicy.Consent(wirelessPaired = true)),
        )
    }

    @Test
    fun callAndNotificationPrivilegesFollowTheirSeparatePermissions() {
        val calls = ElevatedGrantPolicy.Consent(callControl = true)
        assertTrue(ElevatedGrantPolicy.commands(pkg, 30, calls).isEmpty())
        assertEquals(listOf("appops set $pkg MANAGE_ONGOING_CALLS allow"), ElevatedGrantPolicy.commands(pkg, 31, calls))
        val notifications = ElevatedGrantPolicy.Consent(notificationAccess = true)
        assertTrue(ElevatedGrantPolicy.commands(pkg, 34, notifications).isEmpty())
        assertEquals(
            listOf(
                "appops set $pkg RECEIVE_SENSITIVE_NOTIFICATIONS allow",
                "cmd notification allow_listener $pkg/app.nectarlink.android.notifications.NotificationListener",
            ),
            ElevatedGrantPolicy.commands(pkg, 35, notifications),
        )
    }

    @Test
    fun selectingBankingAppsGrantsOnlyTheirRequiredPrivileges() {
        assertEquals(
            listOf(
                "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
                "appops set $pkg GET_USAGE_STATS allow",
            ),
            ElevatedGrantPolicy.commands(pkg, 35, ElevatedGrantPolicy.Consent(bankingApps = true)),
        )
    }

    @Test
    fun permissionChangesAndReconnectAllowNewRequestsWithoutFailureLoops() {
        val requests = ElevatedGrantRequests()
        val calls = ElevatedGrantPolicy.commands(pkg, 35, ElevatedGrantPolicy.Consent(callControl = true))
        assertEquals(calls, requests.next(calls))
        // A failed grant remains missing: the completion callback must not retry forever.
        assertTrue(requests.next(calls).isEmpty())
        // Success, or revoking feature consent, no longer asks for this grant.
        assertTrue(requests.next(emptyList()).isEmpty())
        assertEquals(calls, requests.next(calls))
        requests.clear()
        assertEquals(calls, requests.next(calls))
        requests.retry(calls)
        assertEquals(calls, requests.next(calls))
    }

    @Test
    fun everyConsentCombinationHasOnlyExactKnownCommands() {
        for (mask in 0..15) {
            val consent = ElevatedGrantPolicy.Consent(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, mask and 8 != 0)
            val commands = ElevatedGrantPolicy.commands(pkg, 35, consent)
            assertEquals(commands.size, commands.toSet().size)
            commands.forEach { command ->
                assertTrue(command, ElevatedGrantPolicy.isGrant(command, pkg))
                assertFalse(command, ElevatedGrantPolicy.isGrant(command, "com.example.other"))
                assertFalse(command, ElevatedGrantPolicy.isGrant("$command; reboot", pkg))
            }
            assertEquals(consent.callControl, commands.any { "MANAGE_ONGOING_CALLS" in it })
            assertEquals(consent.notificationAccess, commands.any { "RECEIVE_SENSITIVE_NOTIFICATIONS" in it })
            assertEquals(consent.bankingApps, commands.any { "GET_USAGE_STATS" in it })
            assertFalse(commands.any { "PROJECT_MEDIA" in it || "READ_CLIPBOARD_IN_BACKGROUND" in it || "WRITE_SETTINGS allow" in it || "allow_dnd" in it })
        }
    }
}
