package com.example.airquality

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.airquality.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    //뷰 바인딩 설정
    lateinit var binding: ActivityMainBinding

    // 런타임 권한 요청 시 필요한 요청 코드
    private val PERMISSIONS_REQUEST_CODE = 100

    //요청할 권한 리스트
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    //위치 서비스 요청 시 필요한 런처
    lateinit var getGPSPermissionLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAllPermission() //권한 확인
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
            if (result.resultCode == Activity.RESULT_OK) {
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
}