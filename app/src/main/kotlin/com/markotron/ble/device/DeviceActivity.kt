package com.markotron.ble.device

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.markotron.ble.BellaBleApp
import com.markotron.ble.R
import com.polidea.rxandroidble.RxBleClient
import kotlinx.android.synthetic.main.activity_device.*
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription

class DeviceActivity : AppCompatActivity() {

  lateinit var bleClient: RxBleClient
  lateinit var macAddress: String

  val disposableBag = CompositeSubscription()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_device)

    bleClient = (application as BellaBleApp).bleClient
    macAddress = intent.extras.getString("macAddress")
  }

  override fun onStart() {
    super.onStart()

    val device = bleClient.getBleDevice(macAddress)
    disposableBag.add(device.establishConnection(false).subscribe({}, { it.printStackTrace() }))

    disposableBag.add(device.observeConnectionStateChanges()
                          .observeOn(AndroidSchedulers.mainThread())
                          .subscribe({ status.text = it.name }, { it.printStackTrace() }))

  }

  override fun onStop() {
    super.onStop()
    disposableBag.clear()
  }


}
