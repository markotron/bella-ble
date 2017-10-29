package com.markotron.ble.scanning

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import com.markotron.ble.BellaBleApp
import com.markotron.ble.R
import com.markotron.ble.device.DeviceActivity
import com.polidea.rxandroidble.RxBleClient.State.*
import com.polidea.rxandroidble.scan.ScanResult
import com.polidea.rxandroidble.scan.ScanSettings
import kotlinx.android.synthetic.main.activity_scanning.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.PublishSubject
import rx.subjects.ReplaySubject
import rx.subscriptions.CompositeSubscription
import java.util.concurrent.TimeUnit

sealed class State {
  object Start : State()
  object BluetoothNotAvailable : State()
  object LocationPermissionNotGranted : State()
  object BluetoothNotEnabled : State()
  object LocationServicesNotEnabled : State()
  data class BluetoothReady(val devices: List<ScanResult> = listOf()) : State()
}

sealed class Command {
  object Refresh : Command()
  data class NewScanResult(val scanResult: ScanResult) : Command()
  data class SetBleState(val state: State) : Command()
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

  private val bleClient = getApplication<BellaBleApp>().bleClient
  private val devices = bleClient.scanBleDevices(ScanSettings.Builder().build())

  private val commands = PublishSubject.create<Command>()
  private val replay = ReplaySubject.createWithSize<State>(1)

  // API
  val state: Observable<State> = Observable
      .merge(commands, scanningFeedback(replay), bleStateFeedback())
      .doOnNext { Log.d("COMMAND", it.toString()) }
      .scan<State>(State.Start) { state, command ->
        when (command) {
          is Command.Refresh ->
            if (state is State.BluetoothReady) State.BluetoothReady()
            else state
          is Command.SetBleState -> command.state
          is Command.NewScanResult ->
            if (state is State.BluetoothReady)
              State.BluetoothReady(updateScanResultList(state.devices, command.scanResult))
            else state
        }
      }
      .doOnNext { replay.onNext(it) }
      .doOnNext { Log.d("STATE", it.toString()) }
      .replay(1)
      .refCount()

  fun sendCommand(c: Command) = commands.onNext(c)

  // FEEDBACKS
  private fun scanningFeedback(state: Observable<State>) =
      state
          .map { it is State.BluetoothReady }
          .distinctUntilChanged()
          .switchMap {
            if (it == true)
              devices
                  .filter { filterOnlyBellabeatDevices(it.bleDevice.name) }
                  .onErrorResumeNext { logErrorAndComplete("Error while scanning!", it) }
            else Observable.empty()
          }
          .map { Command.NewScanResult(it) }

  private fun bleStateFeedback() = bleClient
      .observeStateChanges()
      .startWith(Observable.fromCallable { bleClient.state })
      .distinctUntilChanged()
      .map {
        when (it) {
          READY -> Command.SetBleState(State.BluetoothReady(listOf()))
          BLUETOOTH_NOT_AVAILABLE -> Command.SetBleState(State.BluetoothNotAvailable)
          LOCATION_PERMISSION_NOT_GRANTED -> Command.SetBleState(State.LocationPermissionNotGranted)
          LOCATION_SERVICES_NOT_ENABLED -> Command.SetBleState(State.LocationServicesNotEnabled)
          BLUETOOTH_NOT_ENABLED -> Command.SetBleState(State.BluetoothNotEnabled)
          null -> throw RuntimeException("The bluetooth state enum is null!")
        }
      }

  // HELPERS
  private fun updateScanResultList(currentResults: List<ScanResult>, newResult: ScanResult) =
      currentResults
          .minus(currentResults
                     .filter { it.bleDevice.macAddress == newResult.bleDevice.macAddress })
          .plus(newResult)
          .sortedByDescending { it.rssi }

  private fun logErrorAndComplete(msg: String, t: Throwable): Observable<ScanResult> {
    Log.d("SCAN VIEW MODEL", msg, t)
    return Observable.empty()
  }

  private fun filterOnlyBellabeatDevices(name: String?) =
      name != null && (name.startsWith("Leaf") || name.startsWith("Spring"))

}

class ScanActivity : AppCompatActivity() {

  lateinit var adapter: ScanResultAdapter
  lateinit var viewModel: ScanViewModel

  private val disposableBag = CompositeSubscription()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_scanning)

    viewModel = ViewModelProviders.of(this).get(ScanViewModel::class.java)

    adapter = ScanResultAdapter(this::openDeviceActivity)
    bluetooth_scan_result_rv.adapter = adapter
    bluetooth_scan_result_rv.layoutManager = LinearLayoutManager(this)

    swipe_refresh_layout.setOnRefreshListener(this::onRefreshListener)
  }

  override fun onResume() {
    super.onResume()
    disposableBag.add(adapter.observe(scannedDevices()))

    disposableBag.add(viewModel
                          .state
                          .observeOn(AndroidSchedulers.mainThread())
                          .subscribe({
                                       when (it) {
                                         is State.BluetoothNotEnabled -> showBluetoothDisableSnackbar()
                                         is State.BluetoothNotAvailable -> showBluetoothNotAvailableSnackbar()
                                         is State.LocationPermissionNotGranted -> showLocationPermissionNotGrantedSnackbar()
                                         is State.LocationServicesNotEnabled -> showLocationServiceNotEnabledSnackbar()
                                       }
                                     }, { it.printStackTrace() })
    )
  }

  override fun onPause() {
    super.onPause()
    disposableBag.clear()
  }

  private fun scannedDevices() =
      viewModel
          .state
          .filter { it is State.BluetoothReady }
          .map {
            (it as State.BluetoothReady)
                .devices
          }
          .observeOn(AndroidSchedulers.mainThread())

  private fun showBluetoothDisableSnackbar() {
    shortSnackbar("Bluetooth is disabled!")
  }

  private fun showBluetoothNotAvailableSnackbar() {
    shortSnackbar("Bluetooth not available!")
  }

  private fun showLocationPermissionNotGrantedSnackbar() {
    shortSnackbar("Location permission not granted!")
  }

  private fun showLocationServiceNotEnabledSnackbar() {
    shortSnackbar("Location service not enabled!")
  }

  private fun openDeviceActivity(macAddress: String) {
    val intent = Intent(this, DeviceActivity::class.java)
    intent.putExtra("macAddress", macAddress)
    startActivity(intent)
  }

  private fun onRefreshListener() {
    viewModel.sendCommand(Command.Refresh)
    Observable.timer(2, TimeUnit.SECONDS)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { swipe_refresh_layout.isRefreshing = false }
  }

  private fun shortSnackbar(message: String) {
    Snackbar
        .make(scanning_linear_layout, message, Snackbar.LENGTH_LONG)
        .show()
  }

}

