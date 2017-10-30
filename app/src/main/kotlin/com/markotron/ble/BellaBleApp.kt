package com.markotron.ble

import android.app.Application
import com.crashlytics.android.Crashlytics
import com.polidea.rxandroidble.RxBleClient
import io.fabric.sdk.android.Fabric

/**
 * Created by markotron on 25/10/2017.
 */
class BellaBleApp : Application() {

  lateinit var bleClient : RxBleClient

  override fun onCreate() {
    super.onCreate()
    bleClient = RxBleClient.create(this)

    Fabric.with(this, Crashlytics())
  }

}