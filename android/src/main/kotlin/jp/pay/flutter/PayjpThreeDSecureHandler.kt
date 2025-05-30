package jp.pay.flutter

import android.app.Activity
import android.content.Intent
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import jp.pay.android.Payjp
import jp.pay.android.model.TokenId

internal class PayjpThreeDSecureHandler(
    private val channel: MethodChannel
) : PluginRegistry.ActivityResultListener {

    private val requestCodeThreeDSecure = 13016
    var binding: ActivityPluginBinding? = null
        set(value) {
            field = value
            value?.addActivityResultListener(this)
        }

    private fun currentActivity(): Activity? = binding?.activity

    fun startThreeDSecureWithResourceId(result: MethodChannel.Result, resourceId: String) {
        // TODO: 3D Secure functionality will be implemented when PAY.JP SDK supports it
        result.error("NOT_IMPLEMENTED", "3D Secure with resource ID not yet implemented", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != requestCodeThreeDSecure) {
            return false
        }

        // TODO: 3D Secure result handling will be implemented when PAY.JP SDK supports it
        // Payjp.threeDSecure().handleResult(data)?.let { threeDSecureResult ->
        //     if (threeDSecureResult.isSuccess()) {
        //         channel.invokeMethod(ChannelContracts.ON_CARD_FORM_COMPLETED, null)
        //     } else if (threeDSecureResult.isCanceled()) {
        //         channel.invokeMethod(ChannelContracts.ON_CARD_FORM_CANCELED, null)
        //     }
        //     return true
        // }
        return false
    }
} 