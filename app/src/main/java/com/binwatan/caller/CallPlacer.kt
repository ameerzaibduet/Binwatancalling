package com.binwatan.caller

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

// Places a call from a specific SIM slot (0 = SIM slot 1).
object CallPlacer {

    @SuppressLint("MissingPermission")
    fun findPhoneAccount(context: Context, slotIndex: Int): PhoneAccountHandle? {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
        val subscriptions = context.getSystemService(SubscriptionManager::class.java) ?: return null
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null

        val subscription = subscriptions.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
            ?: return null

        val accounts = telecom.callCapablePhoneAccounts

        // 1) Match the calling account to the SIM's subscription id.
        for (handle in accounts) {
            val subscriptionId = try {
                telephony.createForPhoneAccountHandle(handle)?.subscriptionId ?: -1
            } catch (e: Exception) {
                -1
            }

            if (subscriptionId == subscription.subscriptionId) {
                return handle
            }
        }

        // 2) Fall back to matching the SIM card number (ICCID).
        val iccId = subscription.iccId

        if (!iccId.isNullOrEmpty()) {
            for (handle in accounts) {
                if (handle.id == iccId || handle.id.contains(iccId)) {
                    return handle
                }
            }
        }

        return null
    }

    // Returns false (and does NOT call) when the SIM slot cannot be found,
    // so a customer is never called from the wrong SIM.
    @SuppressLint("MissingPermission")
    fun placeCall(context: Context, number: String, slotIndex: Int): Boolean {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        val handle = findPhoneAccount(context, slotIndex) ?: return false

        val extras = Bundle()
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)

        telecom.placeCall(Uri.fromParts("tel", number, null), extras)

        return true
    }
}
