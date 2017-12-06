package com.markotron.ble.bluetooth

import android.util.Log
import com.polidea.rxandroidble.RxBleConnection
import com.polidea.rxandroidble.exceptions.BleDisconnectedException
import io.reactivex.Observable
import io.reactivex.functions.Function
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import java.nio.charset.Charset
import java.util.UUID.fromString

val CHARACTERISTIC_RX = fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
val CHARACTERISTIC_TX = fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

sealed class State {
  data class NotConnected(val retryNo: Int = 0) : State()
  data class Connected(val connection: BleConnection,
                       val request: String? = null,
                       val response: String? = null) : State()
}

sealed class Command {
  data class Connected(val connection: BleConnection) : Command()
  object Disconnected : Command()
  data class SendRequest(val data: String) : Command()
  data class Response(val data: String) : Command()
}

class CommandExecutor(private val device: BleDevice) {

  private val commands = PublishSubject.create<Command>()
  private val replay = ReplaySubject.createWithSize<State>(1)

  private val state = Observable.merge(
    commands,
    connectFeedback(replay),
    requestFeedback(replay),
    responseFeedback(replay)
  )
    .doOnNext { Log.d("COMMAND", it.toString()) }
    .scan<State>(State.NotConnected()) { s, c ->
      when (c) {
        is Command.Connected -> State.Connected(c.connection)
        is Command.Disconnected -> when (s) {
          is State.NotConnected -> s.copy(s.retryNo + 1)
          is State.Connected -> State.NotConnected()
        }
        is Command.SendRequest -> when (s) {
          is State.Connected -> s.copy(request = c.data, response = null)
          is State.NotConnected -> State.NotConnected()
        }
        is Command.Response -> when (s) {
          is State.Connected -> s.copy(request = null, response = c.data)
          is State.NotConnected -> State.NotConnected()
        }
      }
    }
    .doOnNext { Log.d("STATE", it.toString()) }
    .doOnNext { replay.onNext(it) }
    .share()

  private fun connectFeedback(state: Observable<State>): Observable<Command> =
    state
      .filter { it is State.NotConnected }
      .switchMap { s ->
        device
          .establishConnection()
          .map<Command> { Command.Connected(it) }
          .onErrorResumeNext(Function {
            if (it is BleDisconnectedException) {
              Log.w("BLE DISCONNECTED", it)
              if ((s as State.NotConnected).retryNo < 3)
                Observable.just(Command.Disconnected)
              else Observable.error(it)
            } else Observable.error(it)
          })
      }

  private fun requestFeedback(state: Observable<State>): Observable<Command> =
    state
      .filter { it is State.Connected && it.request != null && it.response == null }
      .map { it as State.Connected }
      .flatMap { s ->
        s.request?.let {
          s
            .connection
            .writeCharacteristic(CHARACTERISTIC_RX, it.toByteArray())
            .ignoreElements()
            .andThen(Observable.empty<Command>())
        } ?: Observable.empty<Command>()
      }

  private fun responseFeedback(state: Observable<State>): Observable<Command> =
    state
      .distinctUntilChanged { s -> s is State.Connected }
      .switchMap {
        if (it is State.Connected) {
          it
            .connection
            .setupNotification(CHARACTERISTIC_TX)
            .doOnNext { Log.d("NOTIFICATIONS", it.toString()) }
            .flatMap { it }
            .map { String(it, Charset.forName("US-ASCII")) }
            .map { Command.Response(it) }
            .onErrorResumeNext(Function {
              Log.w("SETUP NOTIFICATION", it)
              Observable.empty()
            })
        } else Observable.empty<Command>()
      }

  fun responses(): Observable<String> = state
    .filter { it is State.Connected && it.response != null }
    .map { (it as State.Connected).response ?: throw RuntimeException() }

  fun sendRequest(request: String) = commands.onNext(Command.SendRequest(request))

  fun connectionState(): Observable<RxBleConnection.RxBleConnectionState> =
    device.observeConnectionStateChanges()

}