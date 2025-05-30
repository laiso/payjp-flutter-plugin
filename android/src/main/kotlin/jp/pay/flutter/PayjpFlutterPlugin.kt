/*
 *
 * Copyright (c) 2020 PAY, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package jp.pay.flutter

import android.content.Context
import androidx.core.os.LocaleListCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import jp.pay.android.Payjp
import jp.pay.android.PayjpCardForm
import jp.pay.android.PayjpConfiguration
import jp.pay.android.cardio.PayjpCardScannerPlugin
import jp.pay.android.model.ClientInfo
import jp.pay.android.model.ExtraAttribute
import jp.pay.android.model.TenantId
import java.util.Locale

class PayjpFlutterPlugin: MethodCallHandler, FlutterPlugin, ActivityAware {
  companion object {
    private const val CHANNEL_NAME = "payjp"
  }

  private var channel: MethodChannel? = null
  private var applicationContext: Context? = null
  private var cardFormModule: CardFormModule? = null
  private var threeDSecureHandler: PayjpThreeDSecureHandler? = null

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    setUpChannel(binding.applicationContext, binding.binaryMessenger)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    applicationContext = null
    channel?.setMethodCallHandler(null)
    channel = null
    cardFormModule = null
    threeDSecureHandler = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    cardFormModule?.binding = binding
    threeDSecureHandler = PayjpThreeDSecureHandler(channel!!)
    threeDSecureHandler?.binding = binding
  }

  override fun onDetachedFromActivity() {
    cardFormModule?.binding = null
    threeDSecureHandler?.binding = null
    threeDSecureHandler = null
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  /**
   * compatible embedding before Flutter v1.12
   *
   */
  private fun setUpChannel(
    applicationContext: Context,
    messenger: BinaryMessenger
  ) {
    this.applicationContext = applicationContext
    channel = MethodChannel(messenger, CHANNEL_NAME).also {
      it.setMethodCallHandler(this)
      cardFormModule = CardFormModule(it)
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      ChannelContracts.INITIALIZE -> {
        val publicKey = checkNotNull(call.argument<String>("publicKey"))
        val debugEnabled = checkNotNull(call.argument<Boolean>("debugEnabled"))
        val locale = call.argument<String>("locale")?.let { tag ->
          LocaleListCompat.forLanguageTags(tag).takeIf { it.size() > 0 }?.get(0)
        } ?: Locale.getDefault()
        val clientInfo = ClientInfo.Builder()
          .setPlugin("jp.pay.flutter/${BuildConfig.VERSION_NAME}")
          .setPublisher("payjp")
          .build()
        val tdsRedirectKey = call.argument<String>("threeDSecureRedirectKey")
        Payjp.init(PayjpConfiguration.Builder(publicKey = publicKey)
          .setDebugEnabled(debugEnabled)
          .setTokenBackgroundHandler(cardFormModule)
          .setLocale(locale)
          .setCardScannerPlugin(PayjpCardScannerPlugin)
          .setClientInfo(clientInfo)
          .setThreeDSecureRedirectName(tdsRedirectKey)
          .build())
        result.success(null)
      }
      ChannelContracts.START_CARD_FORM -> {
        val tenantId = call.argument<String>("tenantId")?.let { TenantId(it) }
        val cardFormType = call.argument<String>("cardFormType")?.let {
          when (it) {
            "multiLine" -> PayjpCardForm.FACE_MULTI_LINE
            "singleLine" -> PayjpCardForm.FACE_MULTI_LINE // TODO: Update when FACE_SINGLE_LINE is available
            else -> PayjpCardForm.FACE_MULTI_LINE
          }
        } ?: PayjpCardForm.FACE_MULTI_LINE
        val extraAttributes = mutableListOf<ExtraAttribute<*>>()
        if (call.argument<Boolean>("extraAttributesEmailEnabled") == true) {
          extraAttributes.add(ExtraAttribute.Email(call.argument<String>("extraAttributesEmailPreset")))
        }
        if (call.argument<Boolean>("extraAttributesPhoneEnabled") == true) {
          extraAttributes.add(ExtraAttribute.Phone(
            call.argument<String>("extraAttributesPhonePresetRegion") ?: "JP",
            call.argument<String>("extraAttributesPhonePresetNumber")
          ))
        }
        val useThreeDSecure = call.argument<Boolean>("useThreeDSecure") ?: false
        cardFormModule?.startCardForm(
          result,
          tenantId,
          cardFormType,
          extraAttributes.toTypedArray(),
          useThreeDSecure
        )
      }
      ChannelContracts.START_THREE_D_SECURE_WITH_RESOURCE_ID -> {
        val resourceId = call.argument<String>("resourceId")
        if (resourceId == null) {
          result.error("INVALID_ARGUMENT", "resourceId is required", null)
          return
        }
        threeDSecureHandler?.startThreeDSecureWithResourceId(result, resourceId)
      }
      ChannelContracts.COMPLETE_CARD_FORM -> {
        cardFormModule?.completeCardForm(result)
      }
      ChannelContracts.SHOW_TOKEN_PROCESSING_ERROR -> {
        val message = call.argument<String>("message")
        if (message == null) {
          result.error("INVALID_ARGUMENT", "message is required", null)
          return
        }
        cardFormModule?.showTokenProcessingError(result, message)
      }
      else -> result.notImplemented()
    }
  }
}
