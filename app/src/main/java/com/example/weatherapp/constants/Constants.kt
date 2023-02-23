package com.example.weatherapp.constants

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {

    const val APP_ID = "c5e7809c5de7a9f31c1e9441ed0e6ce9"
    const val BASE_URL = "https://api.openweathermap.org/data/"
    const val METRIC_UNIT = "metric"

    fun isNetworkAvailable(context: Context): Boolean{
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when{
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> return true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> return true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> return true
                else -> return false
            }
        }
        else{
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }
    }
}