package de.codinix.videoeditor

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * BabaCut Pro über Google Play Billing (Abo `babacut_pro` mit Plänen `monthly` / `yearly`).
 * Der Pro-Status wird lokal mit Kulanzfrist gespeichert, damit die App offline weiterläuft;
 * bei jeder Verbindung mit Play wird er neu bestätigt.
 */
class Pro(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val SUB_ID = "babacut_pro"
        const val PLAN_MONTHLY = "monthly"
        const val PLAN_YEARLY = "annual"
        private const val TAG = "Pro"
        private const val GRACE_MS = 3L * 86_400_000     // offline 3 Tage weiter Pro
        private const val PREF = "pro_prefs"
        private const val KEY_UNTIL = "pro_valid_until"
        private const val KEY_DEBUG = "pro_debug_override"

        /** Schneller, synchroner Status für Schranken in der Oberfläche. */
        fun isActive(ctx: Context): Boolean {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            if (BuildConfig.DEBUG && p.getBoolean(KEY_DEBUG, false)) return true
            return p.getLong(KEY_UNTIL, 0L) > System.currentTimeMillis()
        }
        fun setDebugOverride(ctx: Context, on: Boolean) {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_DEBUG, on).apply()
        }
    }

    private val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private var client: BillingClient? = null
    var product: ProductDetails? = null
        private set
    /** Wird nach Statusänderungen und nach dem Laden der Preise aufgerufen (UI-Thread nicht garantiert). */
    var onChanged: (() -> Unit)? = null
    var lastError: String? = null
        private set

    fun connect() {
        if (client?.isReady == true) { refresh(); return }
        val c = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(com.android.billingclient.api.PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) { lastError = null; refresh() }
                else { lastError = result.debugMessage; Log.w(TAG, "Billing setup: ${result.debugMessage}") }
            }
            override fun onBillingServiceDisconnected() { Log.w(TAG, "Billing disconnected") }
        })
    }

    /** Preise laden und vorhandene Käufe prüfen. */
    fun refresh() {
        val c = client ?: return
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(SUB_ID).setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        c.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = queryResult.productDetailsList.firstOrNull(); onChanged?.invoke()
                if (product == null) Log.w(TAG, "Produkt nicht gefunden: ${queryResult.unfetchedProductList}")
            } else { lastError = result.debugMessage; Log.w(TAG, "Products: ${result.debugMessage}") }
        }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) applyPurchases(purchases, fromPlay = true)
        }
    }

    /** Preisangaben für die Oberfläche. */
    data class Plan(val planId: String, val price: String, val periodIso: String, val hasTrial: Boolean, val trialDays: Int, val offerToken: String)

    fun plans(): List<Plan> {
        val offers = product?.subscriptionOfferDetails ?: return emptyList()
        return listOf(PLAN_MONTHLY, PLAN_YEARLY).mapNotNull { planId ->
            val forPlan = offers.filter { it.basePlanId == planId }
            if (forPlan.isEmpty()) return@mapNotNull null
            // Bevorzugt ein Angebot mit kostenloser Testphase (Preis 0 in erster Phase)
            val best = forPlan.firstOrNull { o -> o.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L } ?: forPlan.first()
            val phases = best.pricingPhases.pricingPhaseList
            val trial = phases.firstOrNull()?.takeIf { it.priceAmountMicros == 0L }
            val paid = phases.lastOrNull { it.priceAmountMicros > 0L } ?: phases.last()
            Plan(planId, paid.formattedPrice, paid.billingPeriod, trial != null, trial?.let { isoDays(it.billingPeriod) } ?: 0, best.offerToken)
        }
    }

    private fun isoDays(iso: String): Int = when {
        iso.startsWith("P") && iso.endsWith("D") -> iso.drop(1).dropLast(1).toIntOrNull() ?: 0
        iso.startsWith("P") && iso.endsWith("W") -> (iso.drop(1).dropLast(1).toIntOrNull() ?: 0) * 7
        else -> 0
    }

    fun buy(activity: Activity, planId: String): Boolean {
        val c = client ?: return false
        val pd = product ?: return false
        val plan = plans().firstOrNull { it.planId == planId } ?: return false
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).setOfferToken(plan.offerToken).build()
        )).build()
        val r = c.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) { lastError = r.debugMessage; return false }
        return true
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> applyPurchases(purchases ?: emptyList(), fromPlay = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> { lastError = result.debugMessage; Log.w(TAG, "Purchase: ${result.debugMessage}") }
        }
    }

    private fun applyPurchases(purchases: List<Purchase>, fromPlay: Boolean) {
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains(SUB_ID) }
        active.forEach { p -> if (!p.isAcknowledged) acknowledge(p) }
        val now = System.currentTimeMillis()
        if (active.isNotEmpty()) prefs.edit().putLong(KEY_UNTIL, now + GRACE_MS).apply()
        else if (fromPlay) prefs.edit().putLong(KEY_UNTIL, 0L).apply()   // Play sagt: kein aktives Abo
        onChanged?.invoke()
    }

    private fun acknowledge(p: Purchase) {
        client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "Acknowledge: ${r.debugMessage}")
        }
    }

    fun release() { client?.endConnection(); client = null }
}
