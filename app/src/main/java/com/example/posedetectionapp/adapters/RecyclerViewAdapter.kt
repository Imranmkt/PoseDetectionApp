package com.example.posedetectionapp.adapters


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.posedetectionapp.utils.Constant
import com.example.posedetectionapp.R
import com.example.posedetectionapp.usecase.player.VideoPlayerActivity
import java.io.File


class RecyclerViewAdapter internal constructor(private val mContext: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var onMoreOptionsClick: ((Int, File, View) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view: View = LayoutInflater.from(parent.context).inflate(R.layout.files_list, parent, false)
        return FileLayoutHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as FileLayoutHolder).videoTitle.text = Constant.allMediaList[position].name
        val uri: Uri = Uri.fromFile(Constant.allMediaList[position])
        Glide.with(mContext)
                .load(uri)
                .thumbnail(0.1f)
                .into(holder.thumbnail)
        holder.itemView.setOnClickListener {
            val intent = Intent(holder.itemView.context, VideoPlayerActivity::class.java)
            intent.putExtra("VideoUri", uri)
            holder.itemView.context.startActivity(intent)
        }

        holder.ivOptions.setOnClickListener {
            val intent = Intent(holder.itemView.context, VideoPlayerActivity::class.java)
            intent.putExtra("VideoUri", uri)
            holder.itemView.context.startActivity(intent)
        }

        holder.ivOptions.setOnClickListener {
            onMoreOptionsClick?.invoke(position, Constant.allMediaList[position], holder.ivOptions)
        }

    }

    override fun getItemCount(): Int {
        return Constant.allMediaList.size
    }

    internal inner class FileLayoutHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var ivOptions: ImageView = itemView.findViewById(R.id.iv_options)
        var thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
        var videoTitle: TextView = itemView.findViewById(R.id.videoTitle)
    }

    fun removeElement(position: Int) {
        Constant.allMediaList.removeAt(position)
        notifyDataSetChanged()
    }

}