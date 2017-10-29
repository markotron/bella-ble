package com.markotron.ble.device

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import com.jakewharton.rxbinding.view.RxView
import com.markotron.ble.BellaBleApp
import com.markotron.ble.R
import com.polidea.rxandroidble.RxBleClient
import com.polidea.rxandroidble.RxBleConnection
import com.polidea.rxandroidble.RxBleDevice
import kotlinx.android.synthetic.main.activity_device.*
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subscriptions.CompositeSubscription
import java.nio.charset.Charset
import java.util.UUID.fromString

class DeviceActivity : AppCompatActivity() {

  lateinit var device: RxBleDevice
  lateinit var bleClient: RxBleClient

  val disposableBag = CompositeSubscription()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_device)

    val macAddress = intent.extras.getString("macAddress")
    bleClient = (application as BellaBleApp).bleClient
    device = bleClient.getBleDevice(macAddress)
  }

  override fun onStart() {
    super.onStart()

    supportActionBar?.title = device.name ?: "No name"

    val connection = device
        .establishConnection(false)
        .retry(3)
        .replay(1)
        .refCount()


    disposableBag.add(
        device.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ status.text = it.name }, { it.printStackTrace() }))


    disposableBag.add(
        connection.flatMap { readCommand(it) }
            .subscribe({}, { it.printStackTrace() })
    )

    disposableBag.add(Observable.combineLatest(RxView.clicks(send_button),
                                               connection) { o1, o2 -> o2 }
                          .flatMap {
                            val command = device_request_tv.text.toString()
                            writeCommand(it, command)
                          }
                          .subscribe({}, { it.printStackTrace() }))

    //    device.establishConnection(false)
    //        .flatMap { con ->
    //          RxView
    //              .clicks(send_button)
    //              .flatMap {
    //                val request = device_request_tv.text.toString()
    //                Observable.merge(
    //                    readCommand(con),
    //                    writeCommand(con, request)
    //                )
    //              }
    //              .subscribeOn(AndroidSchedulers.mainThread())
    //        }
    //        .subscribe({}, {it.printStackTrace()})
  }

  private fun writeCommand(connection: RxBleConnection, command: String) =
      connection
          .writeCharacteristic(CHARACTERISTIC_RX, command.toByteArray())

  private fun readCommand(connection: RxBleConnection) =
      connection
          .setupNotification(CHARACTERISTIC_TX)
          .flatMap { it }
          .map { String(it, Charset.forName("US-ASCII")) }
          .observeOn(AndroidSchedulers.mainThread())
          .doOnNext { Log.d("ON NEXT", it.toString()) }
          .doOnNext {
            device_response_tv.text = device_response_tv.text.toString() + it + "\n"
            scroll_view.post { scroll_view.fullScroll(View.FOCUS_DOWN) }
          }

  override fun onStop() {
    super.onStop()
    disposableBag.clear()
  }

}

val DEFAULT_DFU_SERVICE_UUID = fromString("0000fe59-0000-1000-8000-00805f9b34fb")

val SERVICE_DEVICE_INFO = fromString("0000180a-0000-1000-8000-00805f9b34fb")
val CHARACTERISTIC_FW_INFO = fromString("00002a26-0000-1000-8000-00805f9b34fb")

val SERVICE_RX = fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
val CHARACTERISTIC_RX = fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // write
val CHARACTERISTIC_TX = fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

val DESCRIPTOR_CLIENT_CONFIG = fromString("00002902-0000-1000-8000-00805f9b34fb")

//@Suppress("UNCHECKED_CAST")
//class DeviceViewModelFactory(val macAddress: String) : ViewModelProvider.Factory {
//
//  override fun <T : ViewModel> create(modelClass: Class<T>): T = DeviceViewModel(macAddress) as T
//
//}
//
//sealed class State {
//  object Start : State()
//  object BluetoothNotAvailable : State()
//  object LocationPermissionNotGranted : State()
//  object BluetoothNotEnabled : State()
//  object LocationServicesNotEnabled : State()
//  sealed class BluetoothReady : State() {
//    object ConnectionNotEstablished : BluetoothReady()
//    sealed class ConnectionEstablished(open val connection: RxBleConnection) : BluetoothReady() {
//      data class Idle(override val connection: RxBleConnection) : ConnectionEstablished(connection)
//      data class ExecutingCommand(override val connection: RxBleConnection,
//                                  val command: String) : ConnectionEstablished(connection)
//
//      data class CommandExecuted(override val connection: RxBleConnection,
//                                 val command: String,
//                                 val result: String) : ConnectionEstablished(connection)
//    }
//  }
//}
//
//sealed class Command {
//  data class SetBleState(val state: State) : Command()
//  data class ConnectionEstablished(val connection: RxBleConnection) : Command()
//  object ConnectionDisconnected : Command()
//  data class ExecuteCommand(val command: String) : Command()
//}
//
//class DeviceViewModel(macAddress: String, application: BellaBleApp) : ViewModel() {
//
//  private val bleClient = application.bleClient
//  private val device: RxBleDevice = bleClient.getBleDevice(macAddress)
//
//  private val commands = PublishSubject.create<Command>()
//  private val replay = ReplaySubject.createWithSize<State>(1)
//
//  // API
//  val state: Observable<State> = Observable
//      .merge(commands, bleStateFeedback(), connectionFeedback(replay))
//      .doOnNext { Log.d("COMMAND", it.toString()) }
//      .scan<State>(State.Start) { state, command ->
//        when (command) {
//          is Command.SetBleState -> command.state
//          is ConnectionEstablished -> when (state) {
//            is State.BluetoothReady -> State.BluetoothReady.ConnectionEstablished.Idle(command.connection) as State
//            else -> state
//          }
//          is ConnectionDisconnected -> State.BluetoothReady.ConnectionNotEstablished
//          is ExecuteCommand -> when (state) {
//            is State.BluetoothReady.ConnectionEstablished -> State.BluetoothReady.ConnectionEstablished.ExecutingCommand(
//                state.connection,
//                command.command) as State
//            else -> state
//          }
//        }
//      }
//      .doOnNext { replay.onNext(it) }
//      .doOnNext { Log.d("STATE", it.toString()) }
//      .replay(1)
//      .refCount()
//
//  fun sendCommand(c: Command) = commands.onNext(c)
//
//  // FEEDBACKS
//  private fun bleStateFeedback() = bleClient
//      .observeStateChanges()
//      .startWith(bleClient.state)
//      .distinctUntilChanged()
//      .map {
//        when (it) {
//          READY -> SetBleState(State.BluetoothReady.ConnectionNotEstablished)
//          BLUETOOTH_NOT_AVAILABLE -> SetBleState(State.BluetoothNotAvailable)
//          LOCATION_PERMISSION_NOT_GRANTED -> SetBleState(State.LocationPermissionNotGranted)
//          LOCATION_SERVICES_NOT_ENABLED -> SetBleState(State.LocationServicesNotEnabled)
//          BLUETOOTH_NOT_ENABLED -> SetBleState(State.BluetoothNotEnabled)
//          null -> throw RuntimeException("The bluetooth state enum is null!")
//        }
//      }
//
//  private fun connectionFeedback(state: Observable<State>) =
//      state
//          .filter { it is State.BluetoothReady.ConnectionNotEstablished }
//          .switchMap {
//            device
//                .establishConnection(false)
//                .map<Command> { ConnectionEstablished(it) }
//                .onErrorReturn { ConnectionDisconnected }
//          }
//
//  private fun executeCommandFeedback(state: Observable<State>) =
//      state
//          .filter { it is State.BluetoothReady.ConnectionEstablished.ExecutingCommand }
//          .flatMap {
//            val s = it as State.BluetoothReady.ConnectionEstablished.ExecutingCommand
//            s.connection.writeCharacteristic(CHARACTERISTIC_RX, s.command.toByteArray())
//            Observable.empty<Command>()
//          }
//
//}