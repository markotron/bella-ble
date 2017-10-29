package com.markotron.ble.device

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.jakewharton.rxbinding.view.RxView
import com.markotron.ble.BellaBleApp
import com.markotron.ble.R
import com.markotron.ble.bluetooth.CommandExecutor
import com.polidea.rxandroidble.RxBleClient
import com.polidea.rxandroidble.RxBleConnection
import com.polidea.rxandroidble.RxBleDevice
import kotlinx.android.synthetic.main.activity_device.*
import rx.android.schedulers.AndroidSchedulers.mainThread
import rx.subscriptions.CompositeSubscription

class DeviceActivity : AppCompatActivity() {

  lateinit var device: RxBleDevice
  lateinit var bleClient: RxBleClient
  lateinit var commandExecutor: CommandExecutor

  val disposableBag = CompositeSubscription()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_device)

    val macAddress = intent.extras.getString("macAddress")
    bleClient = (application as BellaBleApp).bleClient
    device = bleClient.getBleDevice(macAddress)
    commandExecutor = CommandExecutor(device)

    if (device.name == null) finish()
    supportActionBar?.title = device.name
  }

  override fun onStart() {
    super.onStart()

    fun printStacktraceAndFinish(t: Throwable) {
      t.printStackTrace()
      finish()
    }

    fun showResponse(response: String) {
      device_response_tv.text = device_response_tv.text.toString() + response + "\n"
      scroll_view.post { scroll_view.fullScroll(View.FOCUS_DOWN) }
      device_request_tv.setText("")
    }

    fun showStatusAndEnableButton(connState: RxBleConnection.RxBleConnectionState) {
      status.text = connState.name
      send_button.isEnabled = connState.name == "CONNECTED"
    }

    disposableBag.add(
        commandExecutor
            .connectionState()
            .observeOn(mainThread())
            .subscribe(::showStatusAndEnableButton, ::printStacktraceAndFinish)
    )

    disposableBag.add(
        commandExecutor
            .responses()
            .observeOn(mainThread())
            .subscribe(::showResponse, ::printStacktraceAndFinish)
    )

    RxView
        .clicks(send_button)
        .subscribe {
          val request = device_request_tv.text.toString()
          commandExecutor.sendRequest(request)
        }
  }

  override fun onStop() {
    super.onStop()
    disposableBag.clear()
  }

}


