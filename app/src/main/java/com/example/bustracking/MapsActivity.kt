package com.example.bustracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.bustracking.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var database: DatabaseReference
    private val locationProvider = LocationProvider(this)
    private val permissionManager = PermissionsManager(this, locationProvider)

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        database = Firebase.database.reference
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        //1
        locationProvider.liveLocation.observe(this) { latLng ->
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
        }

        val toggle: ToggleButton = findViewById(R.id.stopTracking)
        //toggle button
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked){
                //ask permission from user
                permissionManager.requestUserLocation()
                Toast.makeText(this,  "Location started tracking" , Toast.LENGTH_SHORT).show()
                Intent(applicationContext, MyService::class.java).apply {
                    action = MyService.ACTION_START
                    startService(this)
                }
            }
            else{
                locationProvider.stopTracking()
                Intent(applicationContext, MyService::class.java).apply {
                    action = MyService.ACTION_STOP
                    startService(this)
                }

                database.child("user_location").setValue(null)
                    .addOnSuccessListener{
                        Toast.makeText(applicationContext, "Location stopped tracking", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener{
                        Toast.makeText(applicationContext, "Error occured while stopping tracking", Toast.LENGTH_LONG).show()
                    }
            }
        }

        // Update the toggle button state if permission is denied
        if (!permissionManager.hasLocationPermissions()) {
            toggle.isChecked = false
        }

        map.uiSettings.isZoomControlsEnabled = true
        if (permissionManager.hasLocationPermissions()) {
            map.isMyLocationEnabled = true
        }
    }


    class LocationProvider(private val activity: AppCompatActivity) {

        //get object to get user's location
        private val client
                by lazy { LocationServices.getFusedLocationProviderClient(activity) }

        //2
        private val locations = mutableListOf<LatLng>()

        //contains device location
        val liveLocation = MutableLiveData<LatLng>()

        //request for user's position
        @SuppressLint("MissingPermission")
        fun getUserLocation() {
            client.lastLocation.addOnSuccessListener { location ->
                val latLng = LatLng(location.latitude, location.longitude)
                locations.add(latLng)
                liveLocation.value = latLng

            }
        }

        @SuppressLint("MissingPermission")
        fun trackUser() {
            //1
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).apply {
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(true)
            }.build()

            //2
            client.requestLocationUpdates(locationRequest, locationCallback,
                Looper.getMainLooper())
        }

        fun stopTracking() {
            client.removeLocationUpdates(locationCallback)
            locations.clear()
            val ref = FirebaseDatabase.getInstance().getReference("user_location")
            ref.removeValue()
        }
        private val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                //1
                val currentLocation = result.lastLocation
                val latLng = currentLocation?.let { LatLng(it.latitude, currentLocation.longitude) }

                result ?: return
                for (location in result.locations) {
                    // Update the driver's location in Firebase Realtime Database
                    val ref = FirebaseDatabase.getInstance().getReference("user_location")
                    ref.child("latitude").setValue(location.latitude)
                    ref.child("longitude").setValue(location.longitude)
                }
            }
        }

    }

    //asking permission to get user's location
    class PermissionsManager(
        private val activity: AppCompatActivity,
        private val locationProvider: LocationProvider) {

        companion object {
            private const val PERMISSION_REQUEST_CODE = 1001
        }

        //if permission granted, request device location
        private val locationPermissionProvider = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                locationProvider.getUserLocation()
                locationProvider.trackUser()
            }
            else{
                // Handle permission denied case
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        }

        fun hasLocationPermissions(): Boolean {
            val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
            val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

            return ActivityCompat.checkSelfPermission(
                activity,
                fineLocationPermission
            ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        activity,
                        coarseLocationPermission
                    ) == PackageManager.PERMISSION_GRANTED
        }

        fun requestUserLocation() {
            if (hasLocationPermissions()) {
                locationProvider.getUserLocation()
                locationProvider.trackUser()
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }

}