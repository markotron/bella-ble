package com.markotron.ble.scanning

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import com.markotron.ble.BellaBleApp
import com.markotron.ble.device.DeviceActivity
import com.markotron.ble.R
import com.polidea.rxandroidble.RxBleClient
import com.polidea.rxandroidble.scan.ScanResult
import com.polidea.rxandroidble.scan.ScanSettings
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.scan_result_view.view.*
import rx.Observable
import rx.Subscription
import rx.subscriptions.CompositeSubscription

class ScanActivity : AppCompatActivity() {

  lateinit var bleClient: RxBleClient
  lateinit var adapter: ScanResultAdapter
  var refreshBluetoothList = false

  private val disposableBag = CompositeSubscription()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    bleClient = (application as BellaBleApp).bleClient

    enableBluetooth()

    adapter = ScanResultAdapter(this::connect).also {
      disposableBag.add(it.observe(scannedDevices()))
    }
    bluetooth_scan_result_rv.adapter = adapter
    bluetooth_scan_result_rv.layoutManager = LinearLayoutManager(this)

    swipe_refresh_layout.setOnRefreshListener {
      refreshBluetoothList = true
    }
  }

  private fun enableBluetooth() {
    val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    val REQUEST_ENABLE_BLUETOOTH = 1
    startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
  }

  private fun scannedDevices() =
      bleClient
          .scanBleDevices(ScanSettings.Builder().build())
          .filter { it.bleDevice.name != null }
          .scan<List<ScanResult>>(listOf()) { list, scanResult ->
            if (refreshBluetoothList) {
              refreshBluetoothList = false
              swipe_refresh_layout.isRefreshing = false
              listOf()
            } else
              list.minus(list.filter { it.bleDevice.macAddress == scanResult.bleDevice.macAddress })
                  .plus(scanResult)
                  .sortedByDescending { it.rssi }
          }

  override fun onDestroy() {
    super.onDestroy()
    disposableBag.clear()
  }

  private fun connect(macAddress: String) {

    val intent = Intent(this, DeviceActivity::class.java)
    intent.putExtra("macAddress", macAddress)
    startActivity(intent)
  }

}

class ScanResultView @JvmOverloads constructor(context: Context,
                                               attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

  init {
    inflate(context, R.layout.scan_result_view, this)
    layoutParams = LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    orientation = LinearLayout.HORIZONTAL

    attrs?.let {
      context.obtainStyledAttributes(it, R.styleable.ScanResultView, 0, 0).apply {
        getResourceId(R.styleable.ScanResultView_srv_label, -1).let {
          if (it != -1) setLabel(it)
        }
        getString(R.styleable.ScanResultView_srv_label).let {
          if (it != null) setLabel(it)
        }
        getResourceId(R.styleable.ScanResultView_srv_image, -1).let {
          if (it != -1) setImage(it)
        }
      }.recycle()
    }
  }

  fun setImage(@DrawableRes res: Int) {
    device_image.setImageResource(res)
  }

  fun setLabel(label: String) {
    device_name.text = label
  }

  fun setLabel(@StringRes label: Int) {
    device_name.setText(label)
  }

  fun setRssi(rssi: Int) {
    signal_strength.text = rssi.toString()
  }

  fun setMacAddress(macAddress: String) {
    mac_address.text = macAddress
  }

}

class ScanResultViewHolder(val view: ScanResultView) : RecyclerView.ViewHolder(view)

class ScanResultAdapter(val onClick: (String) -> Unit) : RecyclerView.Adapter<ScanResultViewHolder>() {

  private var data: List<ScanResult> = listOf()

  fun observe(observableData: Observable<List<ScanResult>>): Subscription =
      observableData.subscribe {
        data = it
        notifyDataSetChanged()
      }

  fun clearData() {
    data = listOf()
    notifyDataSetChanged()
  }

  override fun getItemCount() = data.size

  override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
    val scanResult = data[position]
    val name = scanResult.bleDevice.name ?: "No name"
    holder.view.setLabel(name)
    val icon = when {
      name.startsWith("Spring") -> R.drawable.ic_sync_spring
      name.startsWith("Leaf") -> R.drawable.ic_sync_leaf
      else -> R.drawable.ic_devices_other_black_24dp
    }
    holder.view.setImage(icon)
    holder.view.setRssi(scanResult.rssi)
    holder.view.setMacAddress(scanResult.bleDevice.macAddress)
    holder.view.setBackgroundColor(ContextCompat.getColor(holder.view.context,
                                                          if (position % 2 == 0)
                                                            R.color.lightBlue
                                                          else
                                                            R.color.white
    ))
    holder.view.setOnClickListener {
      onClick(data[position].bleDevice.macAddress)
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
      ScanResultViewHolder(ScanResultView(
          parent.context))

}
