package dev.samrat.wallet

import android.content.Context
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import dev.samrat.wallet.paymentsdk.WalletPayClient
import dev.samrat.wallet.paymentsdk.WalletPayConfig
import org.json.JSONArray

internal object GooglePay {
    private val isProduction = BuildConfig.GOOGLE_PAY_ENVIRONMENT.equals("PRODUCTION", ignoreCase = true)
    private lateinit var sdk: WalletPayClient

    val allowedPaymentMethods: JSONArray
        get() = sdk.allowedPaymentMethods

    fun createPaymentsClient(context: Context): PaymentsClient {
        sdk = WalletPayClient(
            context,
            WalletPayConfig(
                production = isProduction,
                merchantId = BuildConfig.GOOGLE_PAY_MERCHANT_ID,
                merchantName = BuildConfig.GOOGLE_PAY_MERCHANT_NAME,
                gateway = BuildConfig.GOOGLE_PAY_GATEWAY,
                gatewayMerchantId = BuildConfig.GOOGLE_PAY_GATEWAY_MERCHANT_ID,
                currencyCode = BuildConfig.GOOGLE_PAY_CURRENCY_CODE,
                countryCode = BuildConfig.GOOGLE_PAY_COUNTRY_CODE,
                backendUrl = BuildConfig.PAYMENT_BACKEND_URL,
            ),
        )
        return sdk.paymentsClient
    }

    fun configurationError(): String? = sdk.configurationError()

    fun isReadyToPayRequest(): IsReadyToPayRequest = sdk.isReadyToPayRequest()

    fun paymentDataRequest(amount: String): PaymentDataRequest = sdk.paymentDataRequest(amount)

    fun submitToBackend(paymentData: PaymentData, amount: String, description: String): BackendPaymentResult {
        val result = sdk.submit(paymentData, amount, description)
        return BackendPaymentResult(result.successful, result.message, result.processorResponse)
    }
}

internal data class BackendPaymentResult(
    val successful: Boolean,
    val message: String,
    val processorResponse: String = "",
)
