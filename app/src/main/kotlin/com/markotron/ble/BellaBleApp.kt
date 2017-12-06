package com.markotron.ble

import android.app.Application
import com.crashlytics.android.Crashlytics
import com.markotron.ble.bluetooth.BleClient
import com.polidea.rxandroidble.RxBleClient
import io.fabric.sdk.android.Fabric

/**
 * Created by markotron on 25/10/2017.
 */
class BellaBleApp : Application() {

  lateinit var bleClient: BleClient

  override fun onCreate() {
    super.onCreate()
    bleClient = BleClient.create(RxBleClient.create(this))

    Fabric.with(this, Crashlytics())
  }

}