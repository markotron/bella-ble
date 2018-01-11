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
 - a finite, non empty set of commands. I adopted the name *command* instead of *event*, don't ask me why. 
 In a formal mathematical definition it is called *the input alphabet*.
 - a finite, non empty set of states
 - the initial state
 - the state-transition function. This function takes the current state and a command, and returns 
 a new state.
 - a possibly empty set of finial states. These are states in which we stop our state machine. 
 
We start with the initial state. Side-effects observe the state and produce commands which are then
given to the state-transition function. The state-transition function takes the current state and a 
produced command and returns a new state. Let's give a concrete example. 

We start with the `StartScanning` state. *The Bluetooth state observable* side-effect waits for the `StartScanning`
state and when the state arrives maps the `observableStateChanges` into commands. When the 
`observableStateChanges` returns the `READY` state, we map it into the `BluetoothReady` command and 
give it to the state-transition function. This function will take the `StartScanning` state and the
`BluetoothReady` command and return the `BluetoothReady(listOf())` state. *The scanning observable* waits for 
the `BluetoothReady` state and when the state arrives maps the `scanBleDevices` into the `NewScanResult`
command. The state-transition function adds the scanned device to the `BluetoothReady` state's list. 

In the activity's `onResume` we subscribe to the state machine and update the UI as the state changes. 

It's completely understandable if you're confused. I'll try to illustrate this example with a picture.

![State machine example](https://drive.google.com/file/d/1AhOdEvpKeUO_xmqHE1LeaG_CNIcHC_fh/view?usp=sharing)
 







 