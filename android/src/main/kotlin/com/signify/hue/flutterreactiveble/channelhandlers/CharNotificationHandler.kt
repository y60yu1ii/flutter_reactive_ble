package com.signify.hue.flutterreactiveble.channelhandlers

import com.signify.hue.flutterreactiveble.ProtobufModel as pb
import com.signify.hue.flutterreactiveble.converters.ProtobufMessageConverter
import com.signify.hue.flutterreactiveble.converters.UuidConverter
import io.flutter.plugin.common.EventChannel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import timber.log.Timber

class CharNotificationHandler(private val bleClient: com.signify.hue.flutterreactiveble.ble.BleClient) : EventChannel.StreamHandler {

    private var charNotificationSink: EventChannel.EventSink? = null

    private val uuidConverter = UuidConverter()
    private val protobufConverter = ProtobufMessageConverter()
    private val subscriptionMap = mutableMapOf<pb.CharacteristicAddress, Disposable>()

    override fun onListen(objectSink: Any?, eventSink: EventChannel.EventSink?) {
        eventSink?.let {
            charNotificationSink = eventSink
        }
    }

    override fun onCancel(objectSink: Any?) {
        Timber.d("Listening to notifications cancelled")
        unsubscribeFromAllNotifications()
    }

    fun subscribeToNotifications(request: pb.NotifyCharacteristicRequest) {
        val charUuid = uuidConverter
                .uuidFromByteArray(request.characteristic.characteristicUuid.data.toByteArray())
        val subscription = bleClient.setupNotification(request.characteristic.deviceId, charUuid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ value ->
                    handleNotificationValue(request.characteristic, value)
                },
                { throwable ->
                    Timber.d("whoops: ${throwable.message}")
                }
            )
        subscriptionMap[request.characteristic] = subscription
    }

    fun unsubscribeFromNotifications(request: pb.NotifyNoMoreCharacteristicRequest) {
        subscriptionMap.remove(request.characteristic)?.dispose()
    }

    fun addSingleReadToStream(charInfo: pb.CharacteristicValueInfo) {
        handleNotificationValue(charInfo.characteristic, charInfo.value.toByteArray())
    }

    fun addSingleErrorToStream(subscriptionRequest: pb.CharacteristicAddress, error: String) {
        val convertedMsg = protobufConverter.convertCharacteristicError(subscriptionRequest, error)
        charNotificationSink?.success(convertedMsg.toByteArray())
    }

    private fun unsubscribeFromAllNotifications() {
        charNotificationSink = null
        subscriptionMap.forEach { it.value.dispose() }
    }

    private fun handleNotificationValue(subscriptionRequest: pb.CharacteristicAddress, value: ByteArray) {
        val convertedMsg = protobufConverter.convertCharacteristicInfo(subscriptionRequest, value)
        charNotificationSink?.success(convertedMsg.toByteArray())
    }
}