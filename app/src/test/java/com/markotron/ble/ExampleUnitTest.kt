package com.markotron.ble

import android.util.Log
import org.junit.Test

import org.junit.Assert.*
import rx.Observable
import rx.schedulers.Schedulers

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
}