package com.markotron.ble

import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log
import hu.akarnokd.rxjava.interop.RxJavaInterop

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.notests.sharedsequence.*
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
}
