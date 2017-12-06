package com.markotron.ble.bluetooth

import com.polidea.rxandroidble.RxBleClient
import com.polidea.rxandroidble.RxBleConnection
import com.polidea.rxandroidble.RxBleDevice
import com.polidea.rxandroidble.scan.ScanResult
import com.polidea.rxandroidble.scan.ScanSettings
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.Observable
import java.util.*

/**
 * Created by markotron on 12/6/17.
 */
class BleClient private constructor(val rxBleClient: RxBleClient) {

  companion object {
    private var bleClient: BleClient? = null
    fun create(rxBleClient: RxBleClient): BleClient =
      if (bleClient == null)
        BleClient(rxBleClient)
      else
        bleClient ?: throw RuntimeException("BleClient is changed on a different thread. This should never happen!")
  }

  val state: RxBleClient.State get() = rxBleClient.state

  fun scanBleDevices(scanSettings: ScanSettings): Observable<ScanResult> =
    RxJavaInterop.toV2Observable(rxBleClient.scanBleDevices(scanSettings))

  fun observeStateChanges(): Observable<RxBleClient.State> =
    RxJavaInterop.toV2Observable(rxBleClient.observeStateChanges())

  fun getBleDevice(macAdress: String): BleDevice =
    BleDevice(rxBleClient.getBleDevice(macAdress))

}

class BleDevice(val rxBleDevice: RxBleDevice) {

  val name: String? get() = rxBleDevice.name

  fun observeConnectionStateChanges(): Observable<RxBleConnection.RxBleConnectionState> =
    RxJavaInterop.toV2Observable(rxBleDevice.observeConnectionStateChanges())

  fun establishConnection() : Observable<BleConnection> =
    RxJavaInterop
      .toV2Observable(rxBleDevice.establishConnection(false))
      .map { BleConnection(it) }
}

class BleConnection(val rxBleConnection: RxBleConnection) {

  fun writeCharacteristic(uuid: UUID, data: ByteArray) : Observable<ByteArray> =
    RxJavaInterop.toV2Observable(rxBleConnection.writeCharacteristic(uuid, data))

  fun setupNotification(uuid: UUID) : Observable<Observable<ByteArray>> =
    RxJavaInterop
      .toV2Observable(
        rxBleConnection.setupNotification(uuid).map { RxJavaInterop.toV2Observable(it) }
      )
}