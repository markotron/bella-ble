//package com.markotron.ble.bluetooth
//
//import android.util.Log
//import com.polidea.rxandroidble.RxBleConnection
//import com.polidea.rxandroidble.exceptions.BleDisconnectedException
//import hu.akarnokd.rxjava.interop.RxJavaInterop
//import io.reactivex.Observable
//import io.reactivex.android.schedulers.AndroidSchedulers
//import io.reactivex.subjects.PublishSubject
//import io.reactivex.subjects.ReplaySubject
//import org.notests.sharedsequence.Signal
//import org.notests.sharedsequence.api.debug
//import org.notests.sharedsequence.asSignal
//import org.notests.sharedsequence.empty
//import org.notests.sharedsequence.map
//
///**
// * Created by markotron on 1/3/18.
// */
//
//sealed class SimpleState {
//  data class Connect(val retryNo: Int = 3) : SimpleState()
//  object Connected : SimpleState()
//}
//
//sealed class SimpleCommand {
//  object Disconnected : SimpleCommand()
//  object Connected : SimpleCommand()
//}
//
//class SimpleCommandExecutor(private val device: BleDevice) {
//
//  private val commands = PublishSubject.create<SimpleCommand>()
//  private val replayState = ReplaySubject.createWithSize<SimpleState>(1)
//
//  private val state =
//    Observable.merge(commands, connectionFeedback(replayState))
//      .debug("COMMANDS") { Log.d("SimpleCommandExecutor", it) }
//      .scan<SimpleState>(SimpleState.Connect()) { s, c ->
//        when (c) {
//          is SimpleCommand.Disconnected -> when (s) {
//            is SimpleState.Connect ->
//              if (s.retryNo == 0)
//                throw RuntimeException("Cannot connect")
//              else
//                s.copy(s.retryNo - 1)
//            is SimpleState.Connected -> SimpleState.Connect()
//          }
//          is SimpleCommand.Connected -> SimpleState.Connected
//        }
//      }
//      .debug("STATE") { Log.d("SimpleCommandExecutor", it) }
//      .doOnNext { replayState.onNext(it) }
////      .observeOn(AndroidSchedulers.mainThread())
////      .subscribeOn(AndroidSchedulers.mainThread())
//      .share()
//
//  fun <T> rx.Observable<T>.debug(msg: String) =
//    this
//      .doOnSubscribe { Log.d("debug_operator", "$msg -> subscribe") }
//      .doOnNext { Log.d("debug_operator", "$msg -> next $it") }
//      .doOnCompleted { Log.d("debug_operator", "$msg -> completed") }
//      .doOnUnsubscribe { Log.d("debug_operator", "$msg -> unsubscribed") }
//      .doOnError { Log.d("debug_operator", "$msg -> error $it") }
//
//  private fun connectionFeedback(state: Observable<SimpleState>): Observable<SimpleCommand> =
//    state
//      .filter { it is SimpleState.Connect }
//      .flatMap {
//        RxJavaInterop.toV2Observable(
//          device.rxBleDevice.establishConnection(false)
//            .debug("after establish connection")
//            .map<SimpleCommand> { SimpleCommand.Connected }
//            .onErrorResumeNext(rx.Observable.just(SimpleCommand.Disconnected))
//            .debug("after onError")
//        )
//          .observeOn(AndroidSchedulers.mainThread())
//          .subscribeOn(AndroidSchedulers.mainThread())
////        device.establishConnection()
//////        Observable.error<SimpleCommand>(BleDisconnectedException())
////          .debug("DEVICE CONNECTION - after establish connection") { Log.d("SimpleCommandExecutor", it) }
////          .map<SimpleCommand> { SimpleCommand.Connected }
////          .onErrorReturnItem(SimpleCommand.Disconnected)
////          .debug("DEVICE CONNECTION - after on error") { Log.d("SimpleCommandExecutor", it) }
//////          .share()
//////          .debug("DEVICE CONNECTION - after share") { Log.d("SimpleCommandExecutor", it) }
////          .subscribeOn(AndroidSchedulers.mainThread())
////          .observeOn(AndroidSchedulers.mainThread())
//////          .onErrorResumeNext(Observable.just(SimpleCommand.Disconnected))
//      }
//
//  fun responses(): Signal<String> = state.asSignal { Signal.empty() }.map { it.toString() }
//
//  fun errors(): Signal<Throwable> = Signal.empty()
//
//  fun sendRequest(request: String) = Unit
//
//  fun connectionState(): Signal<RxBleConnection.RxBleConnectionState> =
//    device.observeConnectionStateChanges().asSignal(Signal.empty())
//}