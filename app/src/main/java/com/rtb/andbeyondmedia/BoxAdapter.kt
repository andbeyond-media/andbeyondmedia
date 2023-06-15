package com.rtb.andbeyondmedia

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rtb.andbeyondmedia.common.AdRequest
import com.rtb.andbeyondmedia.databinding.BoxLayoutBinding
import com.rtb.andbeyondmedia.sdk.BannerAdListener

class BoxAdapter(private val context: Context, private val boxes: List<String>) : RecyclerView.Adapter<BoxAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder =
            MyViewHolder(BoxLayoutBinding.inflate(LayoutInflater.from(context), parent, false))

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val tempPost = position
        holder.binding.bannerAd.loadAd(AdRequest().Builder().build())
        holder.binding.bannerAd.setAdListener(object : BannerAdListener {
            override fun onAdClicked() {

            }

            override fun onAdClosed() {
            }

            override fun onAdFailedToLoad(error: String, retrying: Boolean) {
                Log.d("Sonu", "$tempPost onAdFailedToLoad: $retrying , $error")
            }

            override fun onAdImpression() {
            }

            override fun onAdLoaded() {
                Log.d("Sonu", "$tempPost onAdLoaded: ")
            }

            override fun onAdOpened() {

            }

        })
    }

    override fun getItemCount(): Int = 5
    class MyViewHolder(val binding: BoxLayoutBinding) : RecyclerView.ViewHolder(binding.root)
}