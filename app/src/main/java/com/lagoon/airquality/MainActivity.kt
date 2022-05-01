package com.lagoon.airquality

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.lagoon.airquality.databinding.ActivityMainBinding
import com.lagoon.airquality.retrofit.AirQualityResponse
import com.lagoon.airquality.retrofit.AirQualityService
import com.lagoon.airquality.retrofit.RetrofitConnection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


class MainActivity : AppCompatActivity() {

    //전면광고를 위한 변수
    var mInterstitialAd : InterstitialAd? = null
    //전면광고 설정 함수
    private fun setInterstitialAds(){
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(this, "ca-app-pub-9118532229245194/6389861910", adRequest, object : InterstitialAdLoadCallback(){
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("ads log", "전면 광고가 로드 실패했습니다. ${adError.responseInfo}")
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("ads log", "전면 광고가 로드되었습니다.")
                mInterstitialAd = interstitialAd
            }
        })
    }
    //위도와 경도 저장
    var latitude: Double = 0.0
    var longitude: Double = 0.0

    //뷰 바인딩 설정
    lateinit var binding: ActivityMainBinding
    //위도와 경도를 가져올 인스턴스를 위한 변수
    lateinit var locationProvider: LocationProvider

    // 런타임 권한 요청 시 필요한 요청 코드
    private val PERMISSIONS_REQUEST_CODE = 100

    //요청할 권한 리스트
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    //위치 서비스 요청 시 필요한 런처
    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    val startMapActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), object :
        ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult?) {
            if(result?.resultCode ?: 0 == RESULT_OK){
                latitude = result?.data?.getDoubleExtra("latitude", 0.0) ?: 0.0
                longitude = result?.data?.getDoubleExtra("longitude", 0.0) ?: 0.0
                updateUI()
            }
        }
    })
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermission() //권한 확인
        updateUI()
        setRefreshButton()
        setFab()

        setBannerAds()
    }

    override fun onResume() {
        super.onResume()
        setInterstitialAds()
    }
    private  fun setFab(){
        binding.fab.setOnClickListener{
            if(mInterstitialAd != null){
                mInterstitialAd!!.fullScreenContentCallback = object : FullScreenContentCallback(){
                    override fun onAdDismissedFullScreenContent() {
                        Log.d("ads log", "전면 광고가 닫혔습니다.")

                        val intent = Intent(this@MainActivity, MapActivity::class.java)
                        intent.putExtra("currentLat", latitude)
                        intent.putExtra("currentLng", longitude)
                        startMapActivityResult.launch(intent)
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
                        Log.d("ads log", "전면 광고가 열리는데 실패했습니다.")
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("ads log", "전면 광고가 성공적으로 열렸습니다.")
                        mInterstitialAd = null
                    }
                }
                mInterstitialAd!!.show(this@MainActivity)
            }else{
                Log.d("InterstitialAd", "전면 광고가 로딩되지 않았습니다.")
                Toast.makeText(this@MainActivity, "잠시 후 다시 시도해주세요", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI(){
        //인스턴스 생성
        locationProvider = LocationProvider(this@MainActivity)

        //위도와 경도 정보 가져오기
        if(latitude == 0.0 || longitude == 0.0){
            latitude = locationProvider.getLocationLatitude()
            longitude = locationProvider.getLocationLongitude()
        }

        if(latitude != 0.0 || longitude != 0.0){
            //현재 위치를 가져오고 UI업데이트
            val address = getCurrentAddress(latitude, longitude)
            //주소가 null이 아닌경우 update
            address?.let {
                binding.tvLocationTitle.text = "${it.thoroughfare}"
                binding.tvLocationSubtitle.text = "${it.countryName} ${it.adminArea}"
            }
            //현재 미세먼지 농도를 가져오고 UI업데이트
            getAirQualityData(latitude, longitude)
        }else{
            Toast.makeText(this@MainActivity, "위도, 경도 정보를 가져올 수 없습니다. 새로고침을 눌러주세요", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * @desc 레트로핏 클래스를 이용하여 미세먼지 오염정보를 가져온다.
     */
    private fun getAirQualityData(latitude: Double, longitude: Double){
        val retrofitAPI = RetrofitConnection.getInstance().create(AirQualityService::class.java)

        retrofitAPI.getAirQualityData(
            latitude.toString(),
            longitude.toString(),
            BuildConfig.AIRVISUAL_API_KEY
        ).enqueue(object : Callback<AirQualityResponse> {
            override fun onResponse(
                call: Call<AirQualityResponse>,
                response: Response<AirQualityResponse>
            ) {
                //정상적인 Response가 왔다면 UI업데이트
                if(response.isSuccessful){
                    Toast.makeText(this@MainActivity, "최신 정보 업데이트 완료!", Toast.LENGTH_SHORT).show()
                    //response.body()가 null이 아니면
                    response.body()?.let { updateAirUI(it) }
                } else{
                    Toast.makeText(this@MainActivity, "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<AirQualityResponse>, t: Throwable) {
                t.printStackTrace()
                Toast.makeText(this@MainActivity, "업데이트에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * @desc 가져온 데이터 정보를 바탕으로 화면 업데이트
     */
    private fun updateAirUI(airQualityData: AirQualityResponse){
        val pollutionData = airQualityData.data.current.pollution

        //수치 지정(메인 화면 가운데 숫자)
        binding.tvCount.text = pollutionData.aqius.toString()

        //측정된 날짜 지정
        val dateTime = ZonedDateTime.parse(pollutionData.ts).withZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toLocalDateTime()
        val dateForatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        binding.tvCheckTime.text = dateTime.format(dateForatter).toString()
        when(pollutionData.aqius){
            in 0..50 ->{
                binding.tvTitle.text = "좋음"
                binding.imgBg.setImageResource(R.drawable.bg_good)
            }
            in 51..150 ->{
                binding.tvTitle.text = "보통"
                binding.imgBg.setImageResource(R.drawable.bg_soso)
            }
            in 151..200 ->{
                binding.tvTitle.text = "나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_bad)
            }
            else ->{
                binding.tvTitle.text = "매우 나쁨"
                binding.imgBg.setImageResource(R.drawable.bg_worst)
            }
        }
    }
    //업데이트 버튼눌렀을 때
    private fun setRefreshButton(){
        binding.btnRefresh.setOnClickListener{
            updateUI()
        }
    }
    fun getCurrentAddress(latitude:Double, longitude: Double): Address?{
        val geocoder = Geocoder(this, Locale.getDefault())
        //Address객체는 주소와 관련된 여러 정보를 가짐
        val addresses: List<Address>?

        addresses = try{
            //Gecoder 객체를 이용하여 위도와 경도로부터 리스트를 가져온다.
            geocoder.getFromLocation(latitude, longitude, 7)
        }catch (ioException: IOException){
            Toast.makeText(this, "지오코더 서비스 사용불가합니다.", Toast.LENGTH_LONG).show()
            return null
        }catch (illegalArgumentException: IllegalArgumentException){
            Toast.makeText(this, "잘못된 위도 경도 입니다.", Toast.LENGTH_LONG).show()
            return null
        }
        //에러는 아니지만 주소가 발견되지 않은 경우
        if(addresses == null || addresses.size == 0){
            Toast.makeText(this, "주소가 발견되지 않았습니다.", Toast.LENGTH_LONG).show()
            return null
        }
        val address: Address = addresses[0]
        return address
    }

    private fun checkAllPermission(){
        //1.위치 서비스 권한이 켜져 있는지 확인
        if(!isLocationServicesAvailable()){
            showDialogForLocationServiceSetting()
        } else{ //2.런타임 앱 권한이 모두 허용됐는지 확인
            isRunTimePermissionsGranted()
        }
    }

    fun isLocationServicesAvailable(): Boolean{
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }
    fun isRunTimePermissionsGranted(){
        //위치 권한을 가지고 있는지 체크
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION)

        // 권한이 둘 중 한 개라도 없는 경우
        if(hasFineLocationPermission != PackageManager.PERMISSION_GRANTED || hasCoarseLocationPermission != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this@MainActivity, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE)
        }
    }

    //모든 퍼미션이 허용되었는지 확인하고 아니라면 앱 종료
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //요청코드가 permissions_request_code이고 요청한 권한 개수만큼 수신되었다면
        if(requestCode == PERMISSIONS_REQUEST_CODE && grantResults.size == REQUIRED_PERMISSIONS.size){
            var checkResult = true

            //모든 퍼미션을 허용했는지 체크
            for (result in grantResults){
                if(result != PackageManager.PERMISSION_GRANTED){
                    checkResult = false
                    break
                }
            }
            if(checkResult){
                //위칫값을 가져올 수 있음
                updateUI()
            } else{
                //권한이 거부되었으므로 앱 종료
                Toast.makeText(this@MainActivity, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 권한을 수락해주세요!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showDialogForLocationServiceSetting(){
        //ActivityResultLauncher 설정. 결괏값을 반환해야하는 인텐트 실행가능
        getGPSPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
            //결괏값을 받았을 때
            if (result.resultCode == RESULT_OK) {
                //사용자가 GPS를 켰는지 확인
                if (isLocationServicesAvailable()) {
                    isRunTimePermissionsGranted() //런타임 권한 확인
                } else {
                    //위치 서비스가 허용되지 않았다면 앱 종료
                    Toast.makeText(this@MainActivity, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
        val builder: AlertDialog.Builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("위치 서비스 비활성화")//제목 설정
        builder.setMessage("위치서비스가 꺼져있습니다. 설정해야 앱을 다시 사용할 수 있습니다.")
        builder.setCancelable(true) //다이얼로그 창 바깥 터치 시 창 닫힘
        builder.setPositiveButton("설정", DialogInterface.OnClickListener{
            dialog, id ->
            val callGPSSettingIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            getGPSPermissionLauncher.launch(callGPSSettingIntent)
        })
        builder.setNegativeButton("취소", DialogInterface.OnClickListener{
            dialog, id ->
            dialog.cancel()
            Toast.makeText(this@MainActivity, "기기에서 위치서비스를 설정한 후 사용하세요.", Toast.LENGTH_SHORT).show()
            finish()
        })
        builder.create().show()
    }

    private fun setBannerAds(){
        //광고 SDK 초기화
        MobileAds.initialize(this)
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        binding.adView.adListener = object: AdListener(){
            override fun onAdLoaded() {
                Log.d("ads log", "배너 광고가 로드 돠었습니다.")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("ads log", "배너 광고가 로드 실패했습니다. ${adError.responseInfo}")
            }

            override fun onAdOpened() {
                Log.d("ads log", "배너 광고를 열었습니다.")
            }

            override fun onAdClicked() {
                Log.d("ads log", "배너 광고를 클릭했습니다.")
            }

            override fun onAdClosed() {
                Log.d("ads log", "배너 광고를 닫았습니다.")
            }
        }
    }
}