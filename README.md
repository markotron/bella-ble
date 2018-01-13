BellaBle or Bellabeat Bluetooth is a small application for inspecting and debugging Bellabeat's 
devices.

It is implemented using our [SharedSequence](https://github.com/NoTests/SharedSequence.kt) library
and its main purpose is two show it in action. Along with SharedSequence, the app uses RxBleAndroid
to wrap the Bluetooth network stack in RxJava1, and RxJava2Interop to wrap the RxBleAndroid's 
Bluetooth client in RxJava2. 

I won't get into the details of RxBleAndroid and RxJava2Interop but no previous knowledge of these
libraries is required as most of the methods are self-explanatory. 

If you have ever worked with the Android Bluetooth stack, you probably know that there is a lot of
state to manage. All the cases you have to cover cause the problem to be particularly hard to reason
about. Only if we have a pragmatic way to model this state, we have a chance to cover all the 
edge-cases.

Let's get started by specifying what the requirements are. 

*Problem 1.* We want to build an Android app that can scan and list Bellabeat devices nearby, 
connect to any of them, send arbitrary commands, and get responses. 

Note that I decided to filter only Bellabeat devices to simplify the program. When we use specific 
devices, we can hardcode Bluetooth characteristics, thus skipping a part where we need to list all
the characteristics for every device we connect to and choose among them. If you don't know what 
Bluetooth characteristics are, don't worry. It's not important for understanding the concepts we're
going to explain in this posts. 

Let's list all the things that can go wrong while scanning for devices:

 - Bluetooth can be disabled at any time
 - Bluetooth can be unavailable 
 - Location permissions are needed and can be disabled at any time
 - Location services must be enabled and can be disabled at any time
 
If nothing of these happens, the bluetooth is ready. Luckily, in the RxBleAndroid library, 
we have an `observeStateChanges()` function that returns the `Observable<State>`, where the `State`
enum is: 

```java
public enum State {
    /**
     * Bluetooth Adapter is not available on the given OS. 
     * Most functions will throw {@link UnsupportedOperationException} when called.
     */
    BLUETOOTH_NOT_AVAILABLE,
    /**
     * Location permission is not given. Scanning and connecting to a device will not work. 
     * Used on API >=23.
     */
    LOCATION_PERMISSION_NOT_GRANTED,
    /**
     * Bluetooth Adapter is not switched on. Scanning and connecting to a device will not work.
     */
    BLUETOOTH_NOT_ENABLED,
    /**
     * Location Services are switched off. Scanning will not work. Used on API >=23.
     */
    LOCATION_SERVICES_NOT_ENABLED,
    /**
     * Everything is ready to be used.
    */
    READY
}
```

If we observe the state, we can react on time and clear the appropriate resources. Once we know 
that the Bluetooth is ready, we can start scanning for devices. The function 

```java
/**
 * Returns an infinite observable emitting BLE scan results.
 * Scan is automatically started and stopped based on the Observable lifecycle.
 * Scan is started on subscribe and stopped on unsubscribe. You can safely subscribe multiple 
 * observers to this observable.
 * <p>
 * The library automatically handles Bluetooth adapter state changes but you are supposed to 
 * prompt the user to enable it if it is disabled
 *
 * This function works on Android 4.3 in compatibility (emulated) mode.
 *
 * @param scanSettings Scan settings
 * @param scanFilters Filtering settings
 */
public abstract Observable<ScanResult> scanBleDevices(ScanSettings scanSettings, ScanFilter... scanFilters);
```

returns a `ScanResult` every time it finds a device nearby. `ScanResult` contains all the important
information about the device: name, mac address, rssi, ...

Now, we have everything to model our `ScanActivity`. It is obvious that we have to combine the two
observables somehow, but it's not immediately obvious how. To complicate things a little bit more, 
we'll add a third observable - a filter. Remember that we want to filter only Bellabeat devices. We'll
add a menu in which we can select if we want to display only `Leaf` devices, only `Spring` devices, or 
both `Leaf` and `Spring` devices.

If we take a step backwards and look at the whole picture
 - we have the Bluetooth stack which can be in five different states (`BLUETOOTH_NOT_AVAILABLE`, 
 `LOCATION_PERMISSION_NOT_GRANTED`, `BLUETOOTH_NOT_ENABLED`, `LOCATION_SERVICES_NOT_ENABLED`, `READY`)
 - if the Bluetooth is ready, we have to collect all the scan results in a list
 - if the users changes the filter settings we have to filter this list. 
 
Basically, our state has to change based on different events that are emitted from different sources. 
The sources are:
 - *the bluetooth state observable* - if the user turns the bluetooth off, we'll see it through the 
 `observeStateChanges` observable. The same is true for location services and location permissions. 
 - *the filter observable* - if the user changes the filter settings, we'll see see it through another
 observable that we'll construct later.
 - *the scanning observable* - if the new device is found, we'll see it through the `scanBleDevices` 
 observable. 
 
This three sources emit events that can change our state. They interact with different resources and react
when they change. They are popularly called side-effects. What a better way to model the state reacting
to events than a state machine! 

To describe a [state machine](https://en.wikipedia.org/wiki/Finite-state_machine#Mathematical_model)
we need 5 object: 
 - a finite, non empty set of states
 - a finite, non empty set of commands. I adopted the name *command* instead of *event*, don't ask me why. 
 In a formal mathematical definition it is called *the input alphabet*.
 - the initial state
 - the state-transition function. This function takes the current state and a command, and returns 
 a new state.
 - a possibly empty set of finial states. These are states in which we stop our state machine. 
 
We start with the initial state. Side-effects observe the state and produce commands which are then
given to the state-transition function. The state-transition function takes the current state and a 
produced command and returns a new state. Let's give a concrete example. 

We start with the `StartScanning` state. *The Bluetooth state observable* side-effect waits for the 
`StartScanning` state and when the state arrives maps the `observableStateChanges` into commands. When the 
`observableStateChanges` returns the `READY` state, we map it into the `BluetoothReady` command and 
give it to the state-transition function. This function will take the `StartScanning` state and the
`BluetoothReady` command and return the `BluetoothReady(listOf())` state. *The scanning observable* waits for 
the `BluetoothReady` state and when the state arrives maps the `scanBleDevices` into the `NewScanResult`
command. The state-transitioning function adds the scanned device to the `BluetoothReady` state's list. 

In the activity's `onResume` we subscribe to the state machine and update the UI as the state changes. 

It's completely understandable if you're confused. I'll try to illustrate this example with a picture.

![State machine example 1](https://lh3.googleusercontent.com/HuMzt4yIrilGAcTC6Pkw63Lc1tvLLvS0TwlL7_JlcgVXd5E6KteUbiM5rv140fzaAuzD4XtgMdwawnWkHFev3MERmysYSA5jOfOPttC2XSYaiFcsLKOTuCPs7wHrdqKogavqllxgkXsRVz-Otg0vNj5NferKVAL3LBFleXJ0i8JLP426vxdIs9t6AyjjsIDmUBBpQvaiE7N-u0T6a_esROqepu6ECvb2BhTQrKuVxhNrL44tErnDUfsH5e-hevY3U_nNvEh4kCYI9SbEDFfi578is4Tv1-v0-7uuEfj8cU5YRegRnESjbJVx-j3wyDf_vauXDymzpL4r6NgUQCETdimOuoJy6hDr6RYkNGW_UZfCih1XjPFG0CAKMhRqXcGoQsLiLzYWNNLsVWyYrrIZQIazCcM5V4IJ_HrZQ-IWRhNJ0HkqokIA1AAIseF_NYxOi2wcK2Ayq9wu4o_ByRLMeS4BlMGRZbSP7SFU6vlj89klFRjVhfQvsPAD5jyuGOt8NglCn3vBlk0ry-x4_G6L7QRHbUnWUDRnvDj1sOw_Mc3rB0qIiZikj6V_WBUtNCNS=w2560-h1321)
 
When we subscribe to the state machine, it emits the first state - `StartScanning`. Every side-effect
observes the output state of the state-transitioning function, reacts on it, creates zero, one or more
commands and feeds them back to the function. As you can see, this side-effects are loops. They are so 
common in our architectures that we named them **feedback loops** or simply **feedbacks**. With this 
new terminology, our state machine looks like this:
 
![State machine example 2](https://lh3.googleusercontent.com/HuMzt4yIrilGAcTC6Pkw63Lc1tvLLvS0TwlL7_JlcgVXd5E6KteUbiM5rv140fzaAuzD4XtgMdwawnWkHFev3MERmysYSA5jOfOPttC2XSYaiFcsLKOTuCPs7wHrdqKogavqllxgkXsRVz-Otg0vNj5NferKVAL3LBFleXJ0i8JLP426vxdIs9t6AyjjsIDmUBBpQvaiE7N-u0T6a_esROqepu6ECvb2BhTQrKuVxhNrL44tErnDUfsH5e-hevY3U_nNvEh4kCYI9SbEDFfi578is4Tv1-v0-7uuEfj8cU5YRegRnESjbJVx-j3wyDf_vauXDymzpL4r6NgUQCETdimOuoJy6hDr6RYkNGW_UZfCih1XjPFG0CAKMhRqXcGoQsLiLzYWNNLsVWyYrrIZQIazCcM5V4IJ_HrZQ-IWRhNJ0HkqokIA1AAIseF_NYxOi2wcK2Ayq9wu4o_ByRLMeS4BlMGRZbSP7SFU6vlj89klFRjVhfQvsPAD5jyuGOt8NglCn3vBlk0ry-x4_G6L7QRHbUnWUDRnvDj1sOw_Mc3rB0qIiZikj6V_WBUtNCNS=w2560-h1321)

Note that, in this general form, seems like all feedbacks react when a state changes. That's usually 
true, but sometimes it's enough to implement a simpler version. We can simply ignore the state and 
send commands when a resources changes. Actually that's what happen with the *Bluetooth state feedback*.
We don't have to wait for the `State.StartScanning` to arrive, because that's the initial state and 
will arrive as soon as we subscribe. Instead, we ignore the feedback's input state and start
emitting the Bluetooth state commands as soon as we subscribe. Something like this:

![State machine example 3](https://lh3.googleusercontent.com/HuMzt4yIrilGAcTC6Pkw63Lc1tvLLvS0TwlL7_JlcgVXd5E6KteUbiM5rv140fzaAuzD4XtgMdwawnWkHFev3MERmysYSA5jOfOPttC2XSYaiFcsLKOTuCPs7wHrdqKogavqllxgkXsRVz-Otg0vNj5NferKVAL3LBFleXJ0i8JLP426vxdIs9t6AyjjsIDmUBBpQvaiE7N-u0T6a_esROqepu6ECvb2BhTQrKuVxhNrL44tErnDUfsH5e-hevY3U_nNvEh4kCYI9SbEDFfi578is4Tv1-v0-7uuEfj8cU5YRegRnESjbJVx-j3wyDf_vauXDymzpL4r6NgUQCETdimOuoJy6hDr6RYkNGW_UZfCih1XjPFG0CAKMhRqXcGoQsLiLzYWNNLsVWyYrrIZQIazCcM5V4IJ_HrZQ-IWRhNJ0HkqokIA1AAIseF_NYxOi2wcK2Ayq9wu4o_ByRLMeS4BlMGRZbSP7SFU6vlj89klFRjVhfQvsPAD5jyuGOt8NglCn3vBlk0ry-x4_G6L7QRHbUnWUDRnvDj1sOw_Mc3rB0qIiZikj6V_WBUtNCNS=w2560-h1321)

Anyway, that's just a technicality. Conceptually, our state machine is fully described with a second
picture. 

All of this may seem too complicated for a problem we're trying to solve. But two things are important

 1. It's not easy to understand it af first, but once you do, you'll see the power and the simplicity 
 of it. If these pictures are not enough, keep reading. Dive into the code and get back to the pictures 
 later. 
 2. I'm not trying to solve this particular bluetooth problem. I'm trying to generalize it as much as
 possible so that it's applicable to a broader class of problems. 
 
Let's turn our concept into code!

The first thing we need to describe our state machine is a set of states. To describe states and 
commands we use `sealed` and `data` classes and objects. 

```kotlin
sealed class State {
  object StartScanning : State()
  object BluetoothNotAvailable : State()
  object LocationPermissionNotGranted : State()
  object BluetoothNotEnabled : State()
  object LocationServicesNotEnabled : State()
  data class BluetoothReady(val devices: List<ScanResult> = listOf(), val filter: String = "SPRING_LEAF") : State()
}
```

Our state machine can be in six different states. 

 - The initial state: `StartScanning`
 - Bluetooth-problematic states: `BluetoothNotAvailable`, `LocationPermissionNotGranted`, 
 `BluetoothNotEnabled` and `LocationServicesNotEnabled`
 - The state we want to be in: `BluetoothReady`. This state has two extra properties:
   - `devices` - a list of scanned devices
   - `filter` - the filter we're applying to the list of devices
   
Next are commands

```kotlin
sealed class Command {
  object Refresh : Command()
  data class NewScanResult(val scanResult: ScanResult) : Command()
  data class SetBleState(val state: State) : Command()
  data class Filter(val value: String) : Command()
}
```

 - We'll use the `Refresh` command to refresh the list of scanned devices. 
 - Every time we find a new device, we'll send a `NewScanResult` command along with the scanned device
 data (`ScanResult`). 
 - When the *Bluetooth state feedback* detects a change in the bluetooth state (e.g. the user turn 
 the bluetooth off), it will send the `SetBleState` command with the appropriate state. We could have
 a separate command for every state but there is no need for that. Arguably, the code would be easier 
 to understand but definitely longer. 
 - When the user changes the filter settings, the *filter feedback* will send the `Filter` command 
 with the specified filter (represented as a `String` in this example). Note that we haven't displayed
 the *filter feedback* in the pictures above to make them easier to understand. 
 
We've already said that the initial state is `StartScanning`.

The state-transitioning function looks like this:

```kotlin
fun stateTransitioning(state: State, command: Command) = 
  when (command) {
    is Command.Refresh ->
      if(state is State.BluetoothReady) state.copy(devices = listOf()) else state
    is Command.SetBleState -> command.state
    is Command.NewScanResult ->
      if (state is State.BluetoothReady)
        state.copy(devices = updateScanResultList(state.devices, command.scanResult))
      else state
    is Command.Filter ->
      if(state is State.BluetoothReady) state.copy(filter=command.value) else state
  }
```

 - If the command is `Refresh` and the state is `BluetoothReady` we clear the `devices` list. If the
 state is not `BluetoothReady`, we just return the current state because there is nothing to refresh. 
 - If the command is `SetBleState`, we simply set the state. 
 - If the command is `NewScanResult` we append the `ScanResult` to the current list.
 - If the command is `Filter` we change the filter property of the `BluetoothReady` state. 
 
In this example our set of final states is empty, as we want our state machine to run indefinitely.

Good, we have all the components except the feedback loops. Before we define them, let's see the 
machine's structure. 

```kotlin
  private val replay = ReplaySubject.createWithSize<State>(1)

  val state: Driver<State> = Driver
    .merge(listOf(                                                          // (1)
      userCommandsFeedback(),
      scanningFeedback(replay.asDriverCompleteOnError()),
      bleStateFeedback(),
      filterFeedback())
    )
    .scan<Command, State>(State.StartScanning, ::stateTransitioning)        // (2)
    .doOnNext { replay.onNext(it) }                                         // (3)
```

Now, take a look at the third pircture again. 

**(1)** We merge all the commands from all the feedback loops together. Note that we have four feedbacks:

- `userCommandsFeedback()` - we use this feedback if we want to send commands manually. 
- `scanningFeedback` - this feedback waits for the waits for the `BluetoothReady` state and sends 
`NewScanResult` commands if it finds new devices that are not filtered out.
- `bleStateFeedback` - this feedback reacts on Bluetooth state changes (e.g. the user turns the bluetooth 
off)
- `filterFeedback` - this feedback reacts on the filter settings changes (e.g. the user wants to filter
only *Spring* devices)

**(2)** We combine the commands with the current state to produce a new state. 

**(3)** We feed the state back to the feedback loops. In this case only the `scanningFeedback` needs 
a state, others only react on the resource they are observing. (Take a look at the third picture.)

Let's define the feedback loops. 

**The user commands feedback**

```kotlin
private val userCommands = PublishSubject.create<Command>()
  
private fun userCommandsFeedback() = userCommands.asDriverCompleteOnError()
  
fun sendCommand(c: Command) = userCommands.onNext(c)
```

This is how we manually send commands into our state machine. We call the `sendCommand` function which
emits the command into the `PublishSubject` which then gets merged into the our state machine. 

**The scanning feedback**

```kotlin
private val devices = bleClient.scanBleDevices(ScanSettings.Builder().build())

private fun scanningFeedback(state: Driver<State>) =
    state
      .distinctUntilChanged { s -> (s as? State.BluetoothReady)?.filter ?: "" }     
      .switchMapDriver { s ->
        if (s is State.BluetoothReady)
          devices
            .asDriver { logErrorAndComplete("Error while scanning!", it) }
            .filter { filterOnlySelectedDevices(it.bleDevice.name, s.filter) }
        else Driver.empty()
      }
      .map { Command.NewScanResult(it) }
```

This feedback waits until the filter changes and, if the state is `BluetoothReady`, it starts 
scanning for devices, applies the filter and emits the `NewScanResult` command. If the state changes
from `BluetoothReady` to something else, the `switchMap` cleans the resources and returns the empty
`Driver`. 

**Bluetooth state feedback**

```kotlin
private fun bleStateFeedback() =
    Driver.defer {
      bleClient
        .observeStateChanges()
        .asDriverCompleteOnError()
        .startWith(bleClient.state)
        .distinctUntilChanged()
        .map {
          when (it) {
            READY -> Command.SetBleState(State.BluetoothReady(listOf()))
            BLUETOOTH_NOT_AVAILABLE -> Command.SetBleState(State.BluetoothNotAvailable)
            LOCATION_PERMISSION_NOT_GRANTED -> Command.SetBleState(State.LocationPermissionNotGranted)
            LOCATION_SERVICES_NOT_ENABLED -> Command.SetBleState(State.LocationServicesNotEnabled)
            BLUETOOTH_NOT_ENABLED -> Command.SetBleState(State.BluetoothNotEnabled)
            null -> throw RuntimeException("The bluetooth state enum is null!")
          }
        }
}
```
The feedback start with the current Bluetooth state (`bleClient.state`) and emits new values every
time the state changes. The `observeStateChanges` returns the `State` enum which we map into commands.

**Filter feedback**

```kotlin
  private val prefs = PreferenceManager.getDefaultSharedPreferences(app)
  
  private fun filterFeedback() = 
    Observable.create<Command> { emitter ->
      val listener: (SharedPreferences, String) -> Unit = { sp, k ->
        if (k == "device_types_to_scan")
          emitter.onNext(Command.Filter(sp.getString(k, "")))
      }
      prefs.registerOnSharedPreferenceChangeListener(listener)
      emitter.setCancellable {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
      }
    }
    .startWith(Observable.fromCallable<Command> {
      Command.Filter(prefs.getString("device_types_to_scan", ""))
    })
    .asDriverCompleteOnError()
```
 
We store the filter settings in `SharedPreferences` and wrap it in `RxJava` with the `Observable.create`
method. Every time the filter is changed we emit the new filter value in the `Filter` command.

Let's put everything together. 

Note that a state machine is not an architecture specific object and you can implement it wherever 
you want. If I'm using it in an activity I usually put the logic in the [ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel.html) 
class of the [Android's Architecture Components](https://developer.android.com/topic/libraries/architecture/index.html)

The `ViewModel` looks like this:

```kotlin
sealed class State {
  object StartScanning : State()
  object BluetoothNotAvailable : State()
  object LocationPermissionNotGranted : State()
  object BluetoothNotEnabled : State()
  object LocationServicesNotEnabled : State()
  data class BluetoothReady(val devices: List<ScanResult> = listOf(), val filter: String = "SPRING_LEAF") : State()
}

sealed class Command {
  object Refresh : Command()
  data class NewScanResult(val scanResult: ScanResult) : Command()
  data class SetBleState(val state: State) : Command()
  data class Filter(val value: String) : Command()
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

  private val bleClient = getApplication<BellaBleApp>().bleClient
  private val devices = bleClient.scanBleDevices(ScanSettings.Builder().build())

  private val userCommands = PublishSubject.create<Command>()
  private val replay = ReplaySubject.createWithSize<State>(1)

  private val prefs = PreferenceManager.getDefaultSharedPreferences(app)

  // API
  val state: Driver<State> = Driver
    .merge(listOf(
      userCommandsFeedback(),
      scanningFeedback(replay.asDriverCompleteOnError()),
      bleStateFeedback(),
      filterFeedback())
    )
    .scan<Command, State>(State.StartScanning) { state, command ->
      when (command) {
        is Command.Refresh ->
          if (state is State.BluetoothReady) state.copy(devices = listOf()) else state
        is Command.SetBleState -> command.state
        is Command.NewScanResult ->
          if (state is State.BluetoothReady)
            state.copy(devices = updateScanResultList(state.devices, command.scanResult))
          else state
        is Command.Filter ->
          if (state is State.BluetoothReady) state.copy(filter = command.value) else state
      }
    }
    .doOnNext { replay.onNext(it) }

  fun sendCommand(c: Command) = userCommands.onNext(c)

  // FEEDBACKS
  private fun userCommandsFeedback() = userCommands.asDriverCompleteOnError()

  private fun scanningFeedback(state: Driver<State>) =
    state
      .distinctUntilChanged { s -> (s as? State.BluetoothReady)?.filter ?: "" }
      .switchMapDriver { s ->
        if (s is State.BluetoothReady)
          devices
            .asDriver { logErrorAndComplete("Error while scanning!", it) }
            .filter { filterOnlySelectedDevices(it.bleDevice.name, s.filter) }
        else Driver.empty()
      }
      .map { Command.NewScanResult(it) }

  private fun bleStateFeedback() =
    Driver.defer {
      bleClient
        .observeStateChanges()
        .asDriverCompleteOnError()
        .startWith(bleClient.state)
        .distinctUntilChanged()
        .map {
          when (it) {
            READY -> Command.SetBleState(State.BluetoothReady(listOf()))
            BLUETOOTH_NOT_AVAILABLE -> Command.SetBleState(State.BluetoothNotAvailable)
            LOCATION_PERMISSION_NOT_GRANTED -> Command.SetBleState(State.LocationPermissionNotGranted)
            LOCATION_SERVICES_NOT_ENABLED -> Command.SetBleState(State.LocationServicesNotEnabled)
            BLUETOOTH_NOT_ENABLED -> Command.SetBleState(State.BluetoothNotEnabled)
            null -> throw RuntimeException("The bluetooth state enum is null!")
          }
        }
    }

  private fun filterFeedback() =
    Observable.create<Command> { emitter ->
      val listener: (SharedPreferences, String) -> Unit = { sp, k ->
        if (k == "device_types_to_scan")
          emitter.onNext(Command.Filter(sp.getString(k, "")))
      }
      prefs.registerOnSharedPreferenceChangeListener(listener)
      emitter.setCancellable {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
      }
    }
      .startWith(Observable.fromCallable<Command> {
        Command.Filter(prefs.getString("device_types_to_scan", ""))
      })
      .asDriverCompleteOnError()

  // HELPERS
  private fun updateScanResultList(currentResults: List<ScanResult>, newResult: ScanResult) =
    currentResults
      .minus(currentResults
        .filter { it.bleDevice.macAddress == newResult.bleDevice.macAddress })
      .plus(newResult)
      .sortedByDescending { it.rssi }

  private fun logErrorAndComplete(msg: String, t: Throwable): Driver<ScanResult> {
    Log.d("SCAN VIEW MODEL", msg, t)
    return Driver.empty()
  }

  private fun filterOnlySelectedDevices(name: String?, selection: String): Boolean {
    return if (name == null)
      false
    else when (selection) {
      "LEAF" -> name.startsWith("Leaf")
      "SPRING" -> name.startsWith("Spring")
      "SPRING_LEAF" -> name.startsWith("Leaf") || name.startsWith("Spring")
      else -> throw RuntimeException("No devices like this: $selection")
    }
  }
}
```

Now, to start the state machine, we can subscribe in the `onResume` or `onStart` functions. 

```kotlin
viewModel
  .state
  .drive {
    when (it) {
      is State.BluetoothNotEnabled -> showBluetoothDisableSnackbar()
      is State.BluetoothNotAvailable -> showBluetoothNotAvailableSnackbar()
      is State.LocationPermissionNotGranted -> showLocationPermissionNotGrantedSnackbar()
      is State.LocationServicesNotEnabled -> showLocationServiceNotEnabledSnackbar()
    }
  }
```

