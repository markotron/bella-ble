package com.markotron.ble.bluetooth

import android.util.Log
import com.polidea.rxandroidble.RxBleConnection
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import org.notests.sharedsequence.Signal
import org.notests.sharedsequence.api.debug
import org.notests.sharedsequence.asSignal
import org.notests.sharedsequence.empty
import org.notests.sharedsequence.map

/**
 * Created by markotron on 1/3/18.
 */

sealed class SimpleState {
  data class Connect(val retryNo: Int = 3) : SimpleState()
  object Connected : SimpleState()
}

sealed class SimpleCommand {
  object Disconnected : SimpleCommand()
  object Connected : SimpleCommand()
}

class SimpleCommandExecutor(private val device: BleDevice) {

  private val commands = PublishSubject.create<SimpleCommand>()
  private val replayState = ReplaySubject.create<SimpleState>(1)

  private val state =
    Observable.merge(commands, connectionFeedback(replayState))
      .debug("COMMANDS") { Log.d("SimpleCommandExecutor", it) }
      .scan<SimpleState>(SimpleState.Connect()) { s, c ->
        when (c) {
          is SimpleCommand.Disconnected -> when (s) {
            is SimpleState.Connect ->
              if (s.retryNo == 0)
                throw RuntimeException("Cannot connect")
              else
                s.copy(s.retryNo - 1)
            is SimpleState.Connected -> SimpleState.Connect()
          }
          is SimpleCommand.Connected -> SimpleState.Connected
        }
      }
      .debug("STATE") { Log.d("SimpleCommandExecutor", it) }
      .doOnNext { replayState.onNext(it) }

  private fun connectionFeedback(state: Observable<SimpleState>): Observable<SimpleCommand> =
    state
      .filter { it is SimpleState.Connect }
      .switchMap {
        device
          .establishConnection()
          .debug("DEVICE CONNECTION") { Log.d("SimpleCommandExecutor", it) }
          .map<SimpleCommand> { SimpleCommand.Connected }
          .onErrorReturn { SimpleCommand.Disconnected }
      }

  fun responses(): Signal<String> = state.asSignal { Signal.empty() }.map { it.toString() }

  fun errors(): Signal<Throwable> = Signal.empty()

  fun sendRequest(request: String) = Unit

  fun connectionState(): Signal<RxBleConnection.RxBleConnectionState> =
    device.observeConnectionStateChanges().asSignal(Signal.empty())
}