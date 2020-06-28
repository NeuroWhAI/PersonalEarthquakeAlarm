package com.neurowhai.pews.ui.main

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.gms.location.LocationServices
import com.neurowhai.pews.R

/**
 * A placeholder fragment containing a simple view.
 */
class PlaceholderFragment : Fragment() {

    private lateinit var pageViewModel: PageViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProviders.of(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_main, container, false)

        val webView: WebView = root.findViewById(R.id.web)
        webView.settings.apply {
            javaScriptEnabled = true
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
        }
        webView.webChromeClient = object: WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                super.onGeolocationPermissionsShowPrompt(origin, callback)

                callback?.invoke(origin, true, true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && ContextCompat.checkSelfPermission(webView.context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    // Request a permission for location.
                    val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
                    requestPermissions(permissions, 0)
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(view!!.context)
                    .setTitle("알림")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setCancelable(false)
                    .create()
                    .show()

                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(view!!.context)
                    .setTitle("확인")
                    .setMessage(message)
                    .setPositiveButton("확인") { _, _ -> result?.confirm() }
                    .setNegativeButton("취소") { _, _ -> result?.cancel() }
                    .setCancelable(false)
                    .create()
                    .show()

                return true
            }
        }

        pageViewModel.url.observe(this, Observer<String> {
            if (pageViewModel.getIndex() == 1) {
                webView.webViewClient = object: WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Turn off an alarm.
                        view?.evaluateJavascript("iframe.fn_setAlarm()", null)
                        // Remove info button.
                        view?.evaluateJavascript("iframe.document.getElementsByClassName('btn_nav')[0].remove()", null)

                        if (ContextCompat.checkSelfPermission(webView.context, Manifest.permission.ACCESS_COARSE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            LocationServices.getFusedLocationProviderClient(activity!!).apply {
                                lastLocation.addOnSuccessListener { loc : Location? ->
                                    if (loc != null) {
                                        // Set GPS location.
                                        view?.evaluateJavascript("fn_getLocation=function(){}", null)
                                        val js = "fn_showPosition({coords:{latitude:${loc.latitude},longitude:${loc.longitude}}})"
                                        view?.evaluateJavascript(js, null)
                                    }
                                }
                            }
                        }

                        // Restore default client.
                        webView.webViewClient = null
                    }
                }
            }
            else {
                webView.webViewClient = null
            }
            webView.loadUrl(it)
        })

        return root
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}