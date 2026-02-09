package com.example.photocleanup.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing for the one-time premium purchase.
 *
 * Lifecycle: created once in PhotoCleanupApp and lives for the app's lifetime.
 * Call [startConnection] from MainActivity.onCreate() to connect to Play Store.
 * Call [launchPurchase] when the user taps "Unlock Premium".
 * Call [restorePurchase] when the user taps "Restore Purchase".
 */
class BillingManager(
    context: Context,
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "BillingManager"

        // Must match the product ID created in Google Play Console
        const val PRODUCT_ID = "premium_unlock"
    }

    /** UI-observable billing state for showing feedback. */
    sealed class BillingState {
        data object Idle : BillingState()
        data object Loading : BillingState()
        data class Error(val message: String) : BillingState()
        data object PurchaseComplete : BillingState()
    }

    private val _billingState = MutableStateFlow<BillingState>(BillingState.Idle)
    val billingState: StateFlow<BillingState> = _billingState.asStateFlow()

    // Cached product details, fetched on connection
    private var productDetails: ProductDetails? = null

    // Called whenever a purchase changes state (new purchase, pending → completed, etc.)
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase -> handlePurchase(purchase) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled purchase")
                _billingState.value = BillingState.Idle
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned")
                appPreferences.isPremium = true
                _billingState.value = BillingState.PurchaseComplete
            }
            else -> {
                Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
                _billingState.value = BillingState.Error(
                    "Purchase failed. Please try again."
                )
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    /**
     * Connect to Google Play Billing.
     * Call from MainActivity.onCreate(). Non-blocking.
     */
    fun startConnection() {
        if (billingClient.isReady) {
            queryExistingPurchases()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    queryExistingPurchases()
                    queryProductDetails()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    /**
     * Check if the user already owns premium (handles reinstalls, new devices).
     * Only grants access, never revokes — if offline, the SharedPreferences cache
     * keeps them unlocked.
     */
    private fun queryExistingPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val ownsPremium = purchases.any { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (ownsPremium) {
                    appPreferences.isPremium = true
                    // Acknowledge any unacknowledged purchases (safety net)
                    purchases.filter { !it.isAcknowledged }.forEach { acknowledgePurchase(it) }
                }
            }
        }
    }

    /** Pre-fetch product details so the purchase dialog launches instantly. */
    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()
        ) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = productDetailsList.firstOrNull()
                if (productDetails == null) {
                    Log.w(TAG, "Product '$PRODUCT_ID' not found in Play Console")
                }
            } else {
                Log.e(TAG, "Failed to query products: ${billingResult.debugMessage}")
            }
        }
    }

    /**
     * Launch the Google Play purchase flow.
     * Must be called from the main thread with an Activity reference.
     */
    fun launchPurchase(activity: Activity) {
        _billingState.value = BillingState.Loading

        if (!billingClient.isReady) {
            _billingState.value = BillingState.Error(
                "Not connected to Google Play. Please check your internet connection and try again."
            )
            startConnection()
            return
        }

        val details = productDetails
        if (details == null) {
            _billingState.value = BillingState.Error(
                "Product information not available. Please try again in a moment."
            )
            queryProductDetails()
            return
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _billingState.value = BillingState.Error(
                "Could not start purchase. Please try again."
            )
        }
    }

    /**
     * Restore a previous purchase.
     * Queries Google Play for existing purchases and unlocks if found.
     */
    fun restorePurchase() {
        _billingState.value = BillingState.Loading

        if (!billingClient.isReady) {
            _billingState.value = BillingState.Error(
                "Not connected to Google Play. Please check your internet connection and try again."
            )
            startConnection()
            return
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val ownsPremium = purchases.any { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (ownsPremium) {
                    appPreferences.isPremium = true
                    purchases.filter { !it.isAcknowledged }.forEach { acknowledgePurchase(it) }
                    _billingState.value = BillingState.PurchaseComplete
                } else {
                    _billingState.value = BillingState.Error(
                        "No previous purchase found for this Google account."
                    )
                }
            } else {
                _billingState.value = BillingState.Error(
                    "Could not check purchases. Please try again."
                )
            }
        }
    }

    /** Process a completed or pending purchase. */
    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                appPreferences.isPremium = true
                _billingState.value = BillingState.PurchaseComplete
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                Log.d(TAG, "Purchase pending")
                _billingState.value = BillingState.Error(
                    "Your purchase is pending. Premium will unlock once payment completes."
                )
            }
            else -> {
                _billingState.value = BillingState.Idle
            }
        }
    }

    /**
     * Acknowledge a purchase — required within 3 days or Google auto-refunds it.
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged")
            } else {
                Log.e(TAG, "Failed to acknowledge: ${billingResult.debugMessage}")
            }
        }
    }

    /** Reset billing state to Idle. Call after showing feedback to the user. */
    fun resetState() {
        _billingState.value = BillingState.Idle
    }
}
