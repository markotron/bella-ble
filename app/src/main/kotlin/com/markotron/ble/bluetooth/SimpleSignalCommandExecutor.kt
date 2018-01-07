package com.markotron.ble.bluetooth

import android.util.Log
import com.polidea.rxandroidble.RxBleConnection
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import org.notests.sharedsequence.*
import org.notests.sharedsequence.api.ErrorReporting
import org.notests.sharedsequence.api.debug

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
  private val replayState = ReplaySubject.createWithSize<SimpleState>(1)

  private val errors =
    ErrorReporting
      .exceptions()
      .doOnNext {
        println("Whaaat")
      }
      .debug("ERRORS") { Log.d("SimpleCommandExecutor", it) }
      .subscribe()

  private val state: Signal<SimpleState> =
    Signal.merge(listOf(
      commands.asSignal { Signal.empty() },
      connectionFeedback(replayState.asSignal { Signal.empty() }))
    )
      .debug("COMMANDS") { Log.d("SimpleCommandExecutor", it) }
      .scan<SimpleCommand, SimpleState>(SimpleState.Connect()) { s, c ->
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

  private fun connectionFeedback(state: Signal<SimpleState>): Signal<SimpleCommand> =
    state
      .filter { it is SimpleState.Connect }
      .switchMapSignal {
        device
          .establishConnection()
          .debug("DEVICE CONNECTION") { Log.d("SimpleCommandExecutor", it) }
          .map<SimpleCommand> { SimpleCommand.Connected }
          .asSignalReturnOnError<SimpleCommand> { SimpleCommand.Disconnected }
      }

  fun responses(): Signal<String> = state.map { it.toString() }

  fun errors(): Signal<Throwable> = Signal.empty()

  fun sendRequest(request: String) = Unit

  fun connectionState(): Signal<RxBleConnection.RxBleConnectionState> =
    device.observeConnectionStateChanges().asSignal(Signal.empty())
}