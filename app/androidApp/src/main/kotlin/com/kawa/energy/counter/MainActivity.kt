package com.kawa.energy.counter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.kawa.energy.counter.feature.dashboard.App

class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeRequestLocationPermission()
        setContent { App() }
    }

    private fun maybeRequestLocationPermission() {
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(this, coarse) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(coarse))
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
