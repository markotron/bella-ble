package com.markotron.ble.scanning

import android.content.Context
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import com.markotron.ble.R
import android.util.AttributeSet
import android.view.ViewGroup
import com.polidea.rxandroidble.scan.ScanResult
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.view_scan_result.view.*
import org.notests.sharedsequence.Driver
import org.notests.sharedsequence.drive

/**
 * Created by markotron on 26/10/2017.
 */
class ScanResultView @JvmOverloads constructor(context: Context,
                                               attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

  init {
    inflate(context, R.layout.view_scan_result, this)
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

  fun observe(observableData: Driver<List<ScanResult>>): Disposable =
    observableData.drive {
      data = it
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
