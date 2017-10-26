package com.markotron.ble

import android.app.Application
import com.polidea.rxandroidble.RxBleClient

/**
 * Created by markotron on 25/10/2017.
 */
class BellaBleApp : Application() {

  lateinit var bleClient : RxBleClient

  override fun onCreate() {
    super.onCreate()
    bleClient = RxBleClient.create(this)
  }

}