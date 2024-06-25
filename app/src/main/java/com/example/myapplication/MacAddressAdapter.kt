package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MacAddressAdapter(
    val items: MutableList<MacAddressItem>,
    private val deleteMacAddress: (MacAddressItem) -> Unit,
    private val renameMacAddress: (MacAddressItem, String) -> Unit,
    private val connectToMacAddress: (MacAddressItem) -> Unit
) : RecyclerView.Adapter<MacAddressAdapter.MacAddressViewHolder>() {

    inner class MacAddressViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val macAddressTextView: TextView = view.findViewById(R.id.macAddressTextView)
        val deleteButton: Button = view.findViewById(R.id.deleteButton)
        val editButton: Button = view.findViewById(R.id.editButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MacAddressViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mac_address, parent, false)
        return MacAddressViewHolder(view)
    }

    override fun onBindViewHolder(holder: MacAddressViewHolder, position: Int) {
        val item = items[position]
        holder.macAddressTextView.text = item.name

        holder.deleteButton.setOnClickListener {
            deleteMacAddress(item)
        }

        holder.editButton.setOnClickListener {
            // Here you can implement a dialog to rename the item
            val newName = "New Name" // Replace with user input
            renameMacAddress(item, newName)
        }

        holder.itemView.setOnClickListener {
            connectToMacAddress(item)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun addItem(item: MacAddressItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun removeItem(item: MacAddressItem) {
        val index = items.indexOf(item)
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun renameItem(item: MacAddressItem, newName: String) {
        val index = items.indexOf(item)
        if (index >= 0) {
            items[index].name = newName
            notifyItemChanged(index)
        }
    }
}
