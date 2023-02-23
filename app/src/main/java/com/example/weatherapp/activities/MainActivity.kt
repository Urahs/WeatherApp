package com.example.weatherapp.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.R
import com.example.weatherapp.constants.Constants
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.weatherapp.models.WeatherResponse
import com.weatherapp.network.WeatherService
import retrofit.Callback
import retrofit.GsonConverterFactory
import retrofit.Response
import retrofit.Retrofit
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var proggressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if(!isLocationEnabled()){
            Toast.makeText(this, "Please turn on your location provider", Toast.LENGTH_SHORT).show()
            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        else {
            Dexter.withActivity(this)
                .withPermissions(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            requestLocationData()
                        }

                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(this@MainActivity,"You have denied location permission. Please allow, it is mandatory.",Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?,token: PermissionToken?) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread()
                .check()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation: Location? = locationResult.lastLocation
            val latitude = lastLocation!!.latitude
            val longitude = lastLocation!!.longitude
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude:Double){

        if (Constants.isNetworkAvailable(this@MainActivity)) {
            val retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)

            val listCall = service.getWeather(
                latitude,
                longitude,
                Constants.METRIC_UNIT,
                Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object: Callback<WeatherResponse>{
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {

                    hideProgressDialog()

                    if(response!!.isSuccess){
                        val weatherList = response.body()
                        setupUI(weatherList)
                    }
                    else{
                        val rc = response.code()
                        when(rc){
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 ->{
                                Log.e("Error 404", "Not found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(t: Throwable?) {
                    hideProgressDialog()
                    Log.e("Error", t!!.message.toString())
                }
            })
        }
        else {
            Toast.makeText(this@MainActivity,"No internet connection available.",Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        // This provides access to the system location services.
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog() {
        proggressDialog = Dialog(this)
        proggressDialog!!.setContentView(R.layout.dialog_custom_progress)
        proggressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (proggressDialog != null) {
            proggressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUI(weatherList: WeatherResponse) {

        for (z in weatherList.weather.indices) {

            binding.tvMain.text = weatherList.weather[z].main
            binding.tvMainDescription.text = weatherList.weather[z].description
            binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
            binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
            binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
            binding.tvSpeed.text = weatherList.wind.speed.toString()
            binding.tvName.text = weatherList.name
            binding.tvCountry.text = weatherList.sys.country
            binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
            binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

            // Here we update the main icon
            when (weatherList.weather[z].icon) {
                "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "50d" -> binding.ivMain.setImageResource(R.drawable.mist)
            }
        }
    }

    /**
     * Function is used to get the temperature unit value.
     */
    private fun getUnit(value: String): String? {
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    /**
     * The function is used to get the formatted time based on the Format and the LOCALE we pass to it.
     */
    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}