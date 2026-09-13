package dev.samrat.wallet.paymentsdk

import android.content.Context
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class WalletPayConfig(
    val production: Boolean,
    val merchantId: String,
    val merchantName: String,
    val gateway: String,
    val gatewayMerchantId: String,
    val currencyCode: String,
    val countryCode: String,
    val backendUrl: String,
)

data class WalletPayResult(
    val successful: Boolean,
    val message: String,
    val processorResponse: String = "",
)

class WalletPayClient(context: Context, private val config: WalletPayConfig) {
    val paymentsClient: PaymentsClient = Wallet.getPaymentsClient(
        context.applicationContext,
        Wallet.WalletOptions.Builder()
            .setEnvironment(if (config.production) WalletConstants.ENVIRONMENT_PRODUCTION else WalletConstants.ENVIRONMENT_TEST)
            .build(),
    )

    private val baseCardPaymentMethod: JSONObject
        get() = JSONObject()
            .put("type", "CARD")
            .put(
                "parameters",
                JSONObject()
                    .put("allowedAuthMethods", JSONArray(listOf("PAN_ONLY", "CRYPTOGRAM_3DS")))
                    .put("allowedCardNetworks", JSONArray(listOf("AMEX", "DISCOVER", "INTERAC", "JCB", "MASTERCARD", "VISA"))),
            )

    val allowedPaymentMethods: JSONArray
        get() = JSONArray().put(baseCardPaymentMethod)

    fun configurationError(): String? {
        if (!config.production) return null
        return when {
            config.merchantId.isBlank() -> "Google Pay merchant ID is missing"
            config.gateway.isBlank() || config.gateway == "example" -> "Payment gateway is missing"
            config.gatewayMerchantId.isBlank() || config.gatewayMerchantId == "exampleGatewayMerchantId" -> "Gateway merchant ID is missing"
            config.backendUrl.isBlank() -> "Payment backend URL is missing"
            !config.backendUrl.startsWith("https://") -> "Payment backend URL must use HTTPS"
            else -> null
        }
    }

    fun isReadyToPayRequest(): IsReadyToPayRequest = IsReadyToPayRequest.fromJson(
        JSONObject()
            .put("apiVersion", 2)
            .put("apiVersionMinor", 0)
            .put("allowedPaymentMethods", allowedPaymentMethods)
            .toString(),
    )

    fun paymentDataRequest(amount: String): PaymentDataRequest {
        val cardPaymentMethod = baseCardPaymentMethod.put(
            "tokenizationSpecification",
            JSONObject()
                .put("type", "PAYMENT_GATEWAY")
                .put(
                    "parameters",
                    JSONObject()
                        .put("gateway", config.gateway)
                        .put("gatewayMerchantId", config.gatewayMerchantId),
                ),
        )
        val merchantInfo = JSONObject().put("merchantName", config.merchantName)
        if (config.production) merchantInfo.put("merchantId", config.merchantId)

        return PaymentDataRequest.fromJson(
            JSONObject()
                .put("apiVersion", 2)
                .put("apiVersionMinor", 0)
                .put("allowedPaymentMethods", JSONArray().put(cardPaymentMethod))
                .put("merchantInfo", merchantInfo)
                .put(
                    "transactionInfo",
                    JSONObject()
                        .put("totalPriceStatus", "FINAL")
                        .put("totalPrice", amount)
                        .put("currencyCode", config.currencyCode)
                        .put("countryCode", config.countryCode),
                )
                .toString(),
        )
    }

    fun submit(paymentData: PaymentData, amount: String, description: String): WalletPayResult {
        val methodData = JSONObject(paymentData.toJson()).getJSONObject("paymentMethodData")
        val token = methodData.getJSONObject("tokenizationData").getString("token")
        val info = methodData.optJSONObject("info")

        if (config.backendUrl.isBlank()) {
            return WalletPayResult(false, if (config.production) "Payment backend is not configured" else "TEST credential received; no charge was created")
        }

        val requestBody = JSONObject()
            .put("idempotencyKey", UUID.randomUUID().toString())
            .put("paymentToken", token)
            .put("amount", amount)
            .put("currencyCode", config.currencyCode)
            .put("description", description)
            .put("cardNetwork", info?.optString("cardNetwork"))
            .put("cardDetails", info?.optString("cardDetails"))
            .toString()
        val connection = (URL(config.backendUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText().take(500) }.orEmpty()
            WalletPayResult(status in 200..299, if (status in 200..299) "Payment approved" else "Processor rejected payment (HTTP $status)", response)
        } catch (error: Exception) {
            WalletPayResult(false, "Payment service error: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }
}
