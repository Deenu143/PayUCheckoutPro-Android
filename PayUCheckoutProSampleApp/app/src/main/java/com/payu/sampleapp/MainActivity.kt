package com.payu.sampleapp

import android.app.AlertDialog
import android.os.Bundle
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.CompoundButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.payu.base.models.*
import com.payu.checkoutpro.PayUCheckoutPro
import com.payu.checkoutpro.models.PayUCheckoutProConfig
import com.payu.checkoutpro.utils.PayUCheckoutProConstants
import com.payu.checkoutpro.utils.PayUCheckoutProConstants.CP_HASH_NAME
import com.payu.checkoutpro.utils.PayUCheckoutProConstants.CP_HASH_STRING
import com.payu.sampleapp.databinding.ActivityMainBinding
import com.payu.ui.model.listeners.PayUCheckoutProListener
import com.payu.ui.model.listeners.PayUHashGenerationListener

class MainActivity : AppCompatActivity() {

    private val email: String = "snooze@payu.in"
    private val phone = "9999999999"
    private val merchantName = "RH Group"
    private val surl = "https://payuresponse.firebaseapp.com/success"
    private val furl = "https://payuresponse.firebaseapp.com/failure"
    private val amount = "1.0"

    //Test Key and Salt
    private val testKey = "gtKFFX"
    private val testSalt = "eCwWELxi"

    //Prod Key and Salt
    private val prodKey = "0MQaQP"
    private val prodSalt = "13p0PXZk"

    private lateinit var binding: ActivityMainBinding

    // variable to track event time
    private var mLastClickTime: Long = 0
    private var reviewOrderAdapter: ReviewOrderRecyclerViewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        setInitalData()
        initListeners()
    }

    private fun setInitalData() {
        updateProdEnvDetails()
        binding.etSurl.setText(surl)
        binding.etFurl.setText(furl)
        binding.etMerchantName.setText(merchantName)
        binding.etPhone.setText(phone)
        binding.etAmount.setText(amount)
        binding.etUserCredential.setText("${binding.etKey.text}:$email")
        binding.etSurePayCount.setText("0")
    }

    private fun initListeners() {
        binding.radioGrpEnv.setOnCheckedChangeListener { radioGroup: RadioGroup, i: Int ->
            when (i) {
                R.id.radioBtnTest -> updateTestEnvDetails()
                R.id.radioBtnProduction -> updateProdEnvDetails()
                else -> updateTestEnvDetails()
            }
        }

        binding.switchEnableReviewOrder.setOnCheckedChangeListener { compoundButton: CompoundButton, b: Boolean ->
            if (b) showReviewOrderView() else hideReviewOrderView()
        }

        binding.btnAddItem.setOnClickListener { reviewOrderAdapter?.addRow() }
    }

    private fun hideReviewOrderView() {
        binding.rlReviewOrder.visibility = View.GONE
        reviewOrderAdapter = null
    }

    private fun showReviewOrderView() {
        binding.rlReviewOrder.visibility = View.VISIBLE
        reviewOrderAdapter = ReviewOrderRecyclerViewAdapter()
        binding.rvReviewOrder.layoutManager = LinearLayoutManager(this)
        binding.rvReviewOrder.adapter = reviewOrderAdapter
    }

    private fun updateTestEnvDetails() {
        //For testing
        binding.etKey.setText(testKey)
        binding.etSalt.setText(testSalt)
    }

    private fun updateProdEnvDetails() {
        //For Production
        binding.etKey.setText(prodKey)
        binding.etSalt.setText(prodSalt)
    }

    fun startPayment(view: View) {
        // Preventing multiple clicks, using threshold of 1 second
        if (SystemClock.elapsedRealtime() - mLastClickTime < 1000) {
            return
        }
        mLastClickTime = SystemClock.elapsedRealtime()

        val paymentParams = preparePayUBizParams()
        initUiSdk(paymentParams)
    }

    fun preparePayUBizParams(): PayUPaymentParams {
        val vasForMobileSdkHash = HashGenerationUtils.generateHashFromSDK(
            "${binding.etKey.text}|${PayUCheckoutProConstants.CP_VAS_FOR_MOBILE_SDK}|${PayUCheckoutProConstants.CP_DEFAULT}|",
            binding.etSalt.text.toString()
        )
        val paymenRelatedDetailsHash = HashGenerationUtils.generateHashFromSDK(
            "${binding.etKey.text}|${PayUCheckoutProConstants.CP_PAYMENT_RELATED_DETAILS_FOR_MOBILE_SDK}|${binding.etUserCredential.text}|",
            binding.etSalt.text.toString()
        )

        val additionalParamsMap: HashMap<String, Any?> = HashMap()
        additionalParamsMap[PayUCheckoutProConstants.CP_UDF1] = "udf1"
        additionalParamsMap[PayUCheckoutProConstants.CP_UDF2] = "udf2"
        additionalParamsMap[PayUCheckoutProConstants.CP_UDF3] = "udf3"
        additionalParamsMap[PayUCheckoutProConstants.CP_UDF4] = "udf4"
        additionalParamsMap[PayUCheckoutProConstants.CP_UDF5] = "udf5"
        additionalParamsMap[PayUCheckoutProConstants.CP_VAS_FOR_MOBILE_SDK] = vasForMobileSdkHash
        additionalParamsMap[PayUCheckoutProConstants.CP_PAYMENT_RELATED_DETAILS_FOR_MOBILE_SDK] =
            paymenRelatedDetailsHash

        return PayUPaymentParams.Builder().setAmount(binding.etAmount.text.toString())
            .setIsProduction(binding.radioBtnProduction.isChecked)
            .setKey(binding.etKey.text.toString())
            .setProductInfo("Macbook Pro")
            .setPhone(binding.etPhone.text.toString())
            .setTransactionId(System.currentTimeMillis().toString())
            .setFirstName("Abc")
            .setEmail(email)
            .setSurl(binding.etSurl.text.toString())
            .setFurl(binding.etFurl.text.toString())
            .setUserCredential(binding.etUserCredential.text.toString())
            .setAdditionalParams(additionalParamsMap)
            .build()
    }

    private fun initUiSdk(payUPaymentParams: PayUPaymentParams) {
        PayUCheckoutPro.open(
            this,
            payUPaymentParams,
            getCheckoutProConfig(),
            object : PayUCheckoutProListener {

                override fun onPaymentSuccess(response: Any) {
                    processResponse(response)
                }

                override fun onPaymentFailure(response: Any) {
                    processResponse(response)
                }

                override fun onPaymentCancel(isTxnInitiated: Boolean) {
                    showSnackBar(resources.getString(R.string.transaction_cancelled_by_user))
                }

                override fun onError(errorResponse: ErrorResponse) {

                    val errorMessage: String
                    if (errorResponse != null && errorResponse.errorMessage != null && errorResponse.errorMessage!!.isNotEmpty())
                        errorMessage = errorResponse.errorMessage!!
                    else
                        errorMessage = resources.getString(R.string.some_error_occurred)
                    showSnackBar(errorMessage)
                }

                override fun generateHash(
                    map: HashMap<String, String?>,
                    hashGenerationListener: PayUHashGenerationListener
                ) {
                    if (map.containsKey(CP_HASH_STRING)
                        && map.containsKey(CP_HASH_STRING) != null
                        && map.containsKey(CP_HASH_NAME)
                        && map.containsKey(CP_HASH_NAME) != null
                    ) {

                        val hashData = map[CP_HASH_STRING]
                        val hashName = map[CP_HASH_NAME]

                        val hash: String? =
                            HashGenerationUtils.generateHashFromSDK(
                                hashData!!,
                                binding.etSalt.text.toString()
                            )
                        if (!TextUtils.isEmpty(hash)) {
                            val hashMap: HashMap<String, String?> = HashMap()
                            hashMap[hashName!!] = hash!!
                            hashGenerationListener.onHashGenerated(hashMap)
                        }
                    }
                }
            })
    }

    private fun getCheckoutProConfig(): PayUCheckoutProConfig {
        val checkoutProConfig = PayUCheckoutProConfig()
        checkoutProConfig.paymentModesOrder = getCheckoutOrderList()
        checkoutProConfig.showCbToolbar = !binding.switchHideCbToolBar.isChecked
        checkoutProConfig.autoSelectOtp = binding.switchAutoSelectOtp.isChecked
        checkoutProConfig.autoApprove = binding.switchAutoApprove.isChecked
        checkoutProConfig.surePayCount = binding.etSurePayCount.text.toString().toInt()
        checkoutProConfig.cartDetails = reviewOrderAdapter?.getOrderDetailsList()
        checkoutProConfig.showExitConfirmationOnPaymentScreen =
            !binding.switchDiableCBDialog.isChecked
        checkoutProConfig.showExitConfirmationOnCheckoutScreen =
            !binding.switchDiableUiDialog.isChecked
        checkoutProConfig.merchantName = binding.etMerchantName.text.toString()
        checkoutProConfig.merchantLogo = R.drawable.merchant_logo
        return checkoutProConfig
    }

    private fun getCheckoutOrderList(): ArrayList<PaymentMode> {
        val checkoutOrderList = ArrayList<PaymentMode>()
        if (binding.switchShowGooglePay.isChecked) checkoutOrderList.add(
            PaymentMode(
                PaymentType.UPI,
                PayUCheckoutProConstants.CP_GOOGLE_PAY
            )
        )
        if (binding.switchShowPhonePe.isChecked) checkoutOrderList.add(
            PaymentMode(
                PaymentType.WALLET,
                PayUCheckoutProConstants.CP_PHONEPE
            )
        )
        if (binding.switchShowPaytm.isChecked) checkoutOrderList.add(
            PaymentMode(
                PaymentType.WALLET,
                PayUCheckoutProConstants.CP_PAYTM
            )
        )
        return checkoutOrderList
    }

    private fun showSnackBar(message: String) {
        Snackbar.make(binding.clMain, message, Snackbar.LENGTH_LONG).show()
    }

    private fun processResponse(response: Any) {
        response as HashMap<*, *>
        Log.d(
            BaseApiLayerConstants.SDK_TAG,
            "payuResponse ; > " + response[PayUCheckoutProConstants.CP_PAYU_RESPONSE]
                    + ", merchantResponse : > " + response[PayUCheckoutProConstants.CP_MERCHANT_RESPONSE]
        )

        AlertDialog.Builder(this, R.style.Theme_AppCompat_Light_Dialog_Alert)
            .setCancelable(false)
            .setMessage(
                "Payu's Data : " + response.get(PayUCheckoutProConstants.CP_PAYU_RESPONSE) + "\n\n\n Merchant's Data: " + response.get(
                    PayUCheckoutProConstants.CP_MERCHANT_RESPONSE
                )
            )
            .setPositiveButton(
                android.R.string.ok
            ) { dialog, cancelButton -> dialog.dismiss() }.show()
    }
}