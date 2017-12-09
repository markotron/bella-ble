package com.markotron.ble.device

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.jakewharton.rxbinding2.view.RxView
import com.markotron.ble.BellaBleApp
import com.markotron.ble.R
import com.markotron.ble.bluetooth.BleClient
import com.markotron.ble.bluetooth.BleDevice
import com.markotron.ble.bluetooth.CommandExecutor
import com.polidea.rxandroidble.RxBleConnection
import io.reactivex.android.schedulers.AndroidSchedulers.mainThread
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_device.*

class DeviceActivity : AppCompatActivity() {

  lateinit var device: BleDevice
  lateinit var bleClient: BleClient
  lateinit var commandExecutor: CommandExecutor

  val disposableBag = CompositeDisposable()

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
        .asObservable()
        .subscribe(::showStatusAndEnableButton)
    )

    disposableBag.add(
      commandExecutor
        .responses()
        .asObservable()
        .subscribe(::showResponse)
    )

    disposableBag.add(
      commandExecutor
        .errors()
        .asObservable()
        .subscribe(::printStacktraceAndFinish)
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


