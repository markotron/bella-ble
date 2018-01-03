package com.markotron.ble.bluetooth

import android.util.Log
import com.polidea.rxandroidble.RxBleConnection
import com.polidea.rxandroidble.exceptions.BleDisconnectedException
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import org.notests.sharedsequence.*
import org.notests.sharedsequence.api.debug
import java.nio.charset.Charset
import java.util.UUID.fromString

val CHARACTERISTIC_RX = fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
val CHARACTERISTIC_TX = fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

sealed class State {
  data class NotConnected(val retryNo: Int = 0) : State()
  data class Connected(val connection: BleConnection,
                       val request: String? = null,
                       val response: String? = null) : State()

  data class Error(val error: Throwable) : State()
}

sealed class Command {
  data class Connected(val connection: BleConnection) : Command()
  object Disconnected : Command()
  data class SendRequest(val data: String) : Command()
  data class Response(val data: String) : Command()
  data class Error(val error: Throwable) : Command()
}

class CommandExecutor(private val device: BleDevice) {

  private val commands = PublishSubject.create<Command>()
  private val replay = ReplaySubject.createWithSize<State>(1)

  private val state = Signal.merge(listOf(
    commands.asSignal(Signal.empty()),
    connectFeedback(replay.asSignal(Signal.empty())),
    requestFeedback(replay.asSignal(Signal.empty())),
    responseFeedback(replay.asSignal(Signal.empty())))
  )
    .doOnNext { Log.d("COMMAND", it.toString()) }
    .scan<Command, State>(State.NotConnected()) { s, c ->
      when (c) {
        is Command.Connected -> State.Connected(c.connection)
        is Command.Disconnected -> when (s) {
          is State.NotConnected -> s.copy(s.retryNo + 1)
          is State.Connected -> State.NotConnected()
          is State.Error -> State.Error(s.error)
        }
        is Command.SendRequest -> when (s) {
          is State.Connected -> s.copy(request = c.data, response = null)
          is State.NotConnected -> State.NotConnected()
          is State.Error -> State.Error(s.error)
        }
        is Command.Response -> when (s) {
          is State.Connected -> s.copy(request = null, response = c.data)
          is State.NotConnected -> State.NotConnected()
          is State.Error -> State.Error(s.error)
        }
        is Command.Error -> State.Error(c.error)
      }
    }
    .doOnNext { Log.d("STATE", it.toString()) }
    .doOnNext { replay.onNext(it) }
//    .share()

  private fun connectFeedback(state: Signal<State>): Signal<Command> =
    state
      .filter { it is State.NotConnected }
      .debug("After Connection Feedback") { Log.d("After", it) }
      .switchMapSignal { s ->
        device
          // kada koristim Interop s observable-om koji nije establishConnection onda radi.
          // Sljedeci korak je da pokusam rekreirati minimalan program koji ne radi i vidim zasto.
          // Dakle, izbaci drivere, izbaci signale, sve izbaci. Samo zavrepaj library u rxjavu 2 i
          // provjeri radi li ovaj switch map
          .establishConnection()
          .debug("After Establish Connection") { Log.d("After", it) }
          .map<Command> { Command.Connected(it) }
          .asSignal {
            when (it) {
              is BleDisconnectedException -> {
                Log.w("BLE DISCONNECTED", it)
                if ((s as State.NotConnected).retryNo < 3)
                  Signal.just<Command>(Command.Disconnected)
                else Signal.just<Command>(Command.Error(it))
              }
              else -> Signal.just(Command.Error(it))
            }
          }
      }

  private fun requestFeedback(state: Signal<State>): Signal<Command> =
    state
      .filter { it is State.Connected && it.request != null && it.response == null }
      .map { it as State.Connected }
      .flatMapSignal { s ->
        s.request?.let {
          s
            .connection
            .writeCharacteristic(CHARACTERISTIC_RX, it.toByteArray())
            .ignoreElements()
            .andThen(Observable.empty<Command>())
            .asSignal(Signal.empty())
        } ?: Signal.empty<Command>()
      }

  private fun responseFeedback(state: Signal<State>): Signal<Command> =
    state
      .distinctUntilChanged { s -> s is State.Connected }
      .switchMapSignal<State, Command> {
        if (it is State.Connected) {
          it
            .connection
            .setupNotification(CHARACTERISTIC_TX)
            .asSignal(Signal.empty())
            .doOnNext { Log.d("NOTIFICATIONS", it.toString()) }
            .flatMapSignal { it.asSignal(Signal.empty()) }
            .map { String(it, Charset.forName("US-ASCII")) }
            .map { Command.Response(it) }
        } else Signal.empty()
      }

  fun responses(): Signal<String> = state
    .filter { it is State.Connected && it.response != null }
    .map { (it as State.Connected).response ?: throw RuntimeException() }

  fun errors(): Signal<Throwable> = state
    .filter { it is State.Error }
    .map { (it as State.Error).error }

  fun sendRequest(request: String) = commands.onNext(Command.SendRequest(request))

  fun connectionState(): Signal<RxBleConnection.RxBleConnectionState> =
    device.observeConnectionStateChanges().asSignal(Signal.empty())

}