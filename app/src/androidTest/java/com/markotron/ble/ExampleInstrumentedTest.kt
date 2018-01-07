package com.markotron.ble

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.android.schedulers.AndroidSchedulers

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.notests.sharedsequence.*
import org.notests.sharedsequence.api.debug
import rx.Observable
import java.util.concurrent.TimeUnit

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  @Test
  @Throws(Exception::class)
  fun useAppContext() {
    // Context of the app under test.
    val appContext = InstrumentationRegistry.getTargetContext()

    assertEquals("com.markotron.ble", appContext.packageName)
  }


  @Test
  fun testDriverSwitchMap() {
    val subject = io.reactivex.subjects.PublishSubject.create<Int>()
    subject
      .asDriver(Driver.empty())
      .switchMapDriver { n ->
        RxJavaInterop
          .toV2Observable(
            Observable
              .interval(100, TimeUnit.MILLISECONDS)
              .doOnNext { Log.d("Driver switch map", it.toString()) }
              .doOnUnsubscribe { Log.d("Driver switch map", "UNSUBSCRIBE") }
          )
          .asDriver(Driver.empty())
          .map { n * it }
          .debug("Driver switch map") { Log.d("TEST", it) }
      }
      .drive { }

    subject.onNext(2)
    Thread.sleep(1000)
    subject.onNext(3)
    Thread.sleep(1000)
    subject.onNext(4)
  }

  @Test
  fun testSignal1SwitchMap() {
    val subject = io.reactivex.subjects.PublishSubject.create<Int>()
    subject
      .asSignal(Signal.empty())
      .switchMapSignal { n ->
        RxJavaInterop
          .toV2Observable(
            Observable
              .interval(100, TimeUnit.MILLISECONDS)
              .doOnNext { Log.d("Signal switch map", it.toString()) }
              .doOnUnsubscribe { Log.d("Signal switch map", "UNSUBSCRIBE") }
          )
          .asSignal(Signal.empty())
          .map { n * it }
          .debug("Signal switch map") { Log.d("TEST", it) }
      }
      .asObservable().subscribe()

    subject.onNext(2)
    Thread.sleep(1000)
    subject.onNext(3)
    Thread.sleep(1000)
    subject.onNext(4)
  }

  @Test
  fun testSignal2SwitchMap() {
    val subject = io.reactivex.subjects.PublishSubject.create<Int>()
    subject
      .asSignal(Signal.empty())
      .debug("Before switch map") { Log.d("TEST", it) }
      .switchMapSignal { n ->
        io.reactivex.Observable
          .interval(100, TimeUnit.MILLISECONDS)
          .debug("Signal after intreval") { Log.d("TEST", it) }
          .asSignal(Signal.empty())
          .map { n * it }
          .debug("Signal after map") { Log.d("TEST", it) }
      }
      .asObservable().subscribe()

    subject.onNext(2)
    Thread.sleep(1000)
    subject.onNext(3)
    Thread.sleep(1000)
    subject.onNext(4)
  }

  @Test
  fun switchMapDispose() {
    val subject = io.reactivex.subjects.PublishSubject.create<Int>()

    subject
      .share()
      .filter { it >= 3 }
      .debug("Before switch map") { println(it) }
      .switchMap {
        io.reactivex.Observable.error<Int>(RuntimeException())
          .debug("After initial observable") { println(it) }
          .onErrorReturnItem(1)
          .debug("After onErrorReturnItem()") { println(it) }
          .share()
          .debug("After share()") { println(it) }
          .subscribeOn(AndroidSchedulers.mainThread())
          .observeOn(AndroidSchedulers.mainThread())
      }
      .subscribe()

    subject.onNext(2)
    Thread.sleep(200)
    subject.onNext(3)
    Thread.sleep(200)
    subject.onNext(4)
    Thread.sleep(1000)
  }
}
