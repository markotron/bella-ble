package com.markotron.ble.scanning

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.markotron.ble.BellaBleApp
import com.markotron.ble.R
import com.markotron.ble.device.DeviceActivity
import com.markotron.ble.settings.SettingsActivity
import com.polidea.rxandroidble.RxBleClient.State.*
import com.polidea.rxandroidble.scan.ScanResult
import com.polidea.rxandroidble.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import kotlinx.android.synthetic.main.activity_scanning.*
import org.notests.sharedsequence.*
import java.util.concurrent.TimeUnit

sealed class State {
  object StartScanning : State()
  object BluetoothNotAvailable : State()
  object LocationPermissionNotGranted : State()
  object BluetoothNotEnabled : State()
  object LocationServicesNotEnabled : State()
  data class BluetoothReady(val devices: List<ScanResult> = listOf(), val filter: String = "SPRING_LEAF") : State()
}

sealed class Command {
  object Refresh : Command()
  data class NewScanResult(val scanResult: ScanResult) : Command()
  data class SetBleState(val state: State) : Command()
  data class Filter(val value: String) : Command()
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

  private val bleClient = getApplication<BellaBleApp>().bleClient
  private val devices = bleClient.scanBleDevices(ScanSettings.Builder().build())

  private val userCommands = PublishSubject.create<Command>()
  private val replay = ReplaySubject.createWithSize<State>(1)

  private val prefs = PreferenceManager.getDefaultSharedPreferences(app)

  // API
  val state: Driver<State> = Driver
    .merge(listOf(
      userCommandsFeedback(),
      scanningFeedback(replay.asDriverCompleteOnError()),
      bleStateFeedback(),
      filterFeedback())
    )
    .scan<Command, State>(State.StartScanning) { state, command ->
      when (command) {
        is Command.Refresh ->
          if (state is State.BluetoothReady) state.copy(devices = listOf()) else state
        is Command.SetBleState -> command.state
        is Command.NewScanResult ->
          if (state is State.BluetoothReady)
            state.copy(devices = updateScanResultList(state.devices, command.scanResult))
          else state
        is Command.Filter ->
          if (state is State.BluetoothReady) state.copy(filter = command.value) else state
      }
    }
    .doOnNext { replay.onNext(it) }

  fun sendCommand(c: Command) = userCommands.onNext(c)

  // FEEDBACKS
  private fun userCommandsFeedback() = userCommands.asDriverCompleteOnError()

  private fun scanningFeedback(state: Driver<State>) =
    state
      .distinctUntilChanged { s -> (s as? State.BluetoothReady)?.filter ?: "" }
      .switchMapDriver { s ->
        if (s is State.BluetoothReady)
          devices
            .asDriver { logErrorAndComplete("Error while scanning!", it) }
            .filter { filterOnlySelectedDevices(it.bleDevice.name, s.filter) }
        else Driver.empty()
      }
      .map { Command.NewScanResult(it) }

  private fun bleStateFeedback() =
    Driver.defer {
      bleClient
        .observeStateChanges()
        .asDriverCompleteOnError()
        .startWith(bleClient.state)
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
    }

  private fun filterFeedback() =
    Observable.create<Command> { emitter ->
      val listener: (SharedPreferences, String) -> Unit = { sp, k ->
        if (k == "device_types_to_scan")
          emitter.onNext(Command.Filter(sp.getString(k, "")))
      }
      prefs.registerOnSharedPreferenceChangeListener(listener)
      emitter.setCancellable {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
      }
    }
      .startWith(Observable.fromCallable<Command> {
        Command.Filter(prefs.getString("device_types_to_scan", ""))
      })
      .asDriverCompleteOnError()

  // HELPERS
  private fun updateScanResultList(currentResults: List<ScanResult>, newResult: ScanResult) =
    currentResults
      .minus(currentResults
        .filter { it.bleDevice.macAddress == newResult.bleDevice.macAddress })
      .plus(newResult)
      .sortedByDescending { it.rssi }

  private fun logErrorAndComplete(msg: String, t: Throwable): Driver<ScanResult> {
    Log.d("SCAN VIEW MODEL", msg, t)
    return Driver.empty()
  }

  private fun filterOnlySelectedDevices(name: String?, selection: String): Boolean {
    return if (name == null)
      false
    else when (selection) {
      "LEAF" -> name.startsWith("Leaf")
      "SPRING" -> name.startsWith("Spring")
      "SPRING_LEAF" -> name.startsWith("Leaf") || name.startsWith("Spring")
      else -> throw RuntimeException("No devices like this: $selection")
    }
  }

}

class ScanActivity : AppCompatActivity() {

  private lateinit var adapter: ScanResultAdapter
  private lateinit var viewModel: ScanViewModel

  private val disposableBag = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_scanning)

    viewModel = ViewModelProviders.of(this).get(ScanViewModel::class.java)

    adapter = ScanResultAdapter(this::openDeviceActivity)
    bluetooth_scan_result_rv.adapter = adapter
    bluetooth_scan_result_rv.layoutManager = LinearLayoutManager(this)

    swipe_refresh_layout.setOnRefreshListener(this::onRefreshListener)
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.settings, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    startActivity(Intent(this, SettingsActivity::class.java))
    return true
  }

  override fun onResume() {
    super.onResume()
    disposableBag.add(adapter.observe(scannedDevices()))

    disposableBag.add(viewModel
      .state
      .drive {
        when (it) {
          is State.BluetoothNotEnabled -> showBluetoothDisableSnackbar()
          is State.BluetoothNotAvailable -> showBluetoothNotAvailableSnackbar()
          is State.LocationPermissionNotGranted -> showLocationPermissionNotGrantedSnackbar()
          is State.LocationServicesNotEnabled -> showLocationServiceNotEnabledSnackbar()
        }
      }
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

