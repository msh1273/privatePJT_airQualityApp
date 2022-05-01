package com.lagoon.airquality

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.lang.Exception

class LocationProvider(val context: Context) {
    //Location는 위도, 경도, 고도와 같이 위치에 관련된 정보를 가지고 있는 클래스.
    private var location: Location? = null
    private var locationManager: LocationManager? = null

    init{
        //초기화시 위치 가져오기
        getLocation()
    }

    private fun getLocation(): Location? {
        try{
            //GPS를 가져온다.
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            var gpsLocation: Location? = null
            var networkLocation: Location? = null

            //GPS Provider와 Network Provider가 활성화 되어 있는지 확인
            val isGPSEnabled: Boolean = locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled: Boolean = locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if(!isGPSEnabled && !isNetworkEnabled){
                //둘 다 못 쓰는 경우
                return null
            } else{
                val hasFineLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                //두개의 권한 중 하나라도 없다면 null
                if(hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED){
                    return null
                }
                //네트워크를 통한 위치 파악이 가능한 경우
                if(isNetworkEnabled){
                    networkLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
                //GPS를 통한 위치 파악이 가능한 경우
                if(isGPSEnabled){
                    gpsLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }

                if(gpsLocation != null && networkLocation != null){
                    //두 개의 위치가 있다면 정확도 높은것으로 선택
                    if(gpsLocation.accuracy > networkLocation.accuracy){
                        location = gpsLocation
                        return gpsLocation
                    }else{
                        location = networkLocation
                        return networkLocation
                    }
                }else{
                    //가능한 위치 정보가 한 개만 있는 경우
                    if(gpsLocation != null){
                        location = gpsLocation
                    }
                    if(networkLocation != null){
                        location = networkLocation
                    }
                }

            }
        } catch (e: Exception){
            e.printStackTrace()
        }
        return location
    }
    //위도 정보를 가져오는 함수
    fun getLocationLatitude(): Double{
        return location?.latitude ?: 0.0 //null이면 0.0반환
    }
    //경도 정보를 가져오는 함수
    fun getLocationLongitude(): Double{
        return location?.longitude ?: 0.0
    }
}