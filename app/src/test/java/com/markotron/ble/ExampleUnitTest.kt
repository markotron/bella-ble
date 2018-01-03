package com.markotron.ble

import android.util.Log
import hu.akarnokd.rxjava.interop.RxJavaInterop
import org.junit.Test

import org.junit.Assert.*
import org.notests.sharedsequence.*
import org.notests.sharedsequence.api.debug
import rx.Observable
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
class ExampleUnitTest {
  @Test
  @Throws(Exception::class)
  fun addition_isCorrect() {
    assertEquals(4, (2 + 2).toLong())
  }

  @Test
  fun testingReplay() {
    val o = Observable.just(1, 2, 3, 4, 5, 6)
      .doOnSubscribe { println("SUBSCRIBE") }
      .doOnNext { println("NEXT: $it") }
      .doOnCompleted { println("COMPLETED") }
      .doOnUnsubscribe { println("UNSUBSCRIBED") }
      .replay(1)
      .autoConnect()
      .subscribeOn(Schedulers.io())

    o.subscribe { println("1") }
    o.subscribe { println("2") }

    Thread.sleep(1000)
  }

  @Test
  fun testingError() {
    Observable.just(1, 2, 3, 4, 5, 6)
      .map { if (it == 4) throw RuntimeException("Whaaat") else it }
      .doOnSubscribe { println("SUBSCRIBE") }
      .doOnNext { println("NEXT: $it") }
      .doOnCompleted { println("COMPLETED") }
      .doOnUnsubscribe { println("UNSUBSCRIBED") }
      .doOnError { println("ERROR: $it") }
      .onErrorResumeNext { Observable.empty() }
      .subscribe()
    Thread.sleep(1000)
  }

  @Test
  fun testingErrorV2() {
    val o = io.reactivex.Observable.just(1, 2, 3, 4, 5, 6)
//      .map { if (it == 4) throw RuntimeException("Whaaat") else it }
//      .debug("Testing error in RxJava2") { println(it) }
      .doOnSubscribe { println("SUBSCRIBE") }
      .doOnNext { println("NEXT: $it") }
      .doOnComplete { println("COMPLETED") }
      .doOnDispose { println("UNSUBSCRIBED") }
      .doFinally { println("Finally") }
      .doOnError { println("ERROR: $it") }
      .onErrorResumeNext { t: Throwable -> io.reactivex.Observable.empty<Int>() }
      .subscribeOn(io.reactivex.schedulers.Schedulers.io())
      .subscribe()
    o.dispose()
    Thread.sleep(1000)
  }

  @Test
  fun testUnsubscriveOnErrorV1() {
    val subject = PublishSubject.create<Int>()
    subject
      .doOnSubscribe { println("SUBSCRIBE") }
      .doOnNext { println("NEXT: $it") }
      .doOnCompleted { println("COMPLETED") }
      .doOnUnsubscribe { println("UNSUBSCRIBED") }
      .doOnError { println("ERROR: $it") }
      .subscribe()

    subject.onNext(1)
    subject.onNext(2)
    subject.onError(Exception())
    Thread.sleep(1000)
  }

  @Test
  fun testInteropUnsubscribe() {

    val subject = PublishSubject.create<Int>()
    val v1o = subject
      .doOnSubscribe { println("SUBSCRIBE") }
      .doOnNext { println("NEXT: $it") }
      .doOnCompleted { println("COMPLETED") }
      .doOnUnsubscribe { println("UNSUBSCRIBED") }
      .doOnError { println("ERROR: $it") }

    RxJavaInterop.toV2Observable(v1o)
      .subscribe()

    subject.onNext(1)
    subject.onNext(2)
    subject.onError(Exception())

    Thread.sleep(1000)
  }

  @Test
  fun testSwitchMapWithInterop() {

    val subject = io.reactivex.subjects.PublishSubject.create<Int>()

    subject
      .switchMap { n ->
        RxJavaInterop.toV2Observable(
          Observable
            .interval(100, TimeUnit.MILLISECONDS)
            .map { it * n }
            .doOnSubscribe { println("SUBSCRIBE") }
            .doOnNext { println("NEXT: $it") }
            .doOnCompleted { println("COMPLETED") }
            .doOnUnsubscribe { println("UNSUBSCRIBED") }
            .doOnError { println("ERROR: $it") }
        )
          .debug("") { println(it) }
      }
      .subscribe()

    subject.onNext(2)
    Thread.sleep(1000)
    subject.onNext(3)
    Thread.sleep(1000)
    subject.onNext(4)
  }

}