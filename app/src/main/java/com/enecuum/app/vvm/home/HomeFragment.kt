package com.enecuum.app.vvm.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.enecuum.app.BuildConfig
import com.enecuum.app.R
import com.enecuum.app.extensions.hideProgress
import com.enecuum.app.extensions.showProgress
import com.enecuum.app.service.DateService
import com.enecuum.app.utils.AmountValue
import com.enecuum.app.utils.Constants
import com.enecuum.app.vvm.common.TextButton
import com.enecuum.app.vvm.host.MainActivity
import com.enecuum.lib.KeyStore
import com.enecuum.lib.SageSign
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pixplicity.easyprefs.library.Prefs
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.getViewModel
import kotlin.coroutines.CoroutineContext

class HomeFragment : Fragment(), CoroutineScope {

    private val viewModel: HomeViewModel by lazy {
        requireActivity().getViewModel<HomeViewModel>()
    }

    private var dateService: DateService? = null
    private var isServiceBound = false
    private var isMining = false

    private lateinit var parentJob: Job
    private val coroutineScope: CoroutineScope
        get() = CoroutineScope(Dispatchers.Main + parentJob)

    private var data: SageSign.GeneratedData? = null

    override val coroutineContext: CoroutineContext
        get() = parentJob + Dispatchers.IO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentJob = Job()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
        parentJob.cancel()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        runLibraryMethod.configure(R.string.run_method, backStyle = TextButton.BackStyle.GRAY_DISABLED, allCaps = true)
        balance.configure(R.string.balance, backStyle = TextButton.BackStyle.GRAY_DISABLED, allCaps = true)
        getBIT.configure(R.string.get_bit, backStyle = TextButton.BackStyle.GRAY_DISABLED, allCaps = true)
        mining.configure(R.string.tab_mining, backStyle = TextButton.BackStyle.GRAY_DISABLED, allCaps = true)
        signIn.configure(R.string.signin, backStyle = TextButton.BackStyle.BLUE)
        signIn.setText("GENERATE KEY")
        signIn.setOnClickListener {
            signIn()
        }

        Prefs.putString(Constants.MINING_TOKEN, BuildConfig.TOKEN)

        viewModel.observeDetailedBalance(this, Observer {
            if (it == null) {
                balance.setText("ERROR")
                return@Observer
            }
            balance.setText(AmountValue.formatFromApiRaw(it.amount))
            enableCheckBalance()
        })

        context?.let {
            if (KeyStore.publicKey(it).isNotEmpty()) {

                signIn.setText(KeyStore.publicKey(it))
                signIn.setDisabled()

                enableCheckBalance()
                viewModel.getDetailedBalance()
                enableGetBIT()
                libraryConnected()
            }
        }
    }

    private fun signIn() {
        rootLayout.showProgress()
        coroutineScope.launch(Dispatchers.Main) {

            data = SageSign.generateKeys()
            KeyStore.saveKeys(view?.context!!, data!!)

            //You may put your private key here instead of random generated
//            context?.let { KeyStore.saveKeys(it, "") }

            context?.let { signIn.setText(KeyStore.publicKey(it)) }

            signIn.setDisabled()

            enableCheckBalance()
            enableGetBIT()
            libraryConnected()

            rootLayout.hideProgress()
        }
    }

    private fun enableCheckBalance() {
        balance.setEnabled()
        balance.setOnClickListener {
            balance.setDisabled()
            viewModel.getDetailedBalance()
        }
    }

    private fun enableGetBIT() {
        getBIT.setEnabled()
        getBIT.setOnClickListener {
            getBIT.setDisabled()
            viewModel.get25BIT { enableGetBIT() }
        }
    }

    private enum class LibraryMethod() {
        AVAILABLE_BALANCE() {
            override fun getCalculatedValue(viewModel : HomeViewModel): String {
                return viewModel.getAvailableBalance()
            }

            override fun setPrettyName(context: Context) {
                prettyName = context.getString(R.string.run_method)
            }

            override fun toString(): String {
                return prettyName
            }
        }, SUM_BALANCE() {
            override fun getCalculatedValue(viewModel : HomeViewModel): String {
                return viewModel.getSumBalance()
            }

            override fun toString(): String {
                return prettyName
            }

            override fun setPrettyName(context: Context) {
                prettyName = context.getString(R.string.about)
            }
        };

        var prettyName = super.toString()
        abstract fun getCalculatedValue(viewModel : HomeViewModel): String
        abstract fun setPrettyName(context : Context)
    }

    private fun enableSpinner() {
        for (method in LibraryMethod.values())
            activity?.let { method.setPrettyName(it) }
        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, LibraryMethod.values())
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        selectMethod!!.adapter = arrayAdapter
        selectMethod.isEnabled = true
    }

    private fun enableTestLibrary() {
        runLibraryMethod.setEnabled()
        runLibraryMethod.setOnClickListener {
            val methodResult = (selectMethod.selectedItem as LibraryMethod).getCalculatedValue(viewModel)
            val toast = Toast.makeText(context, methodResult, Toast.LENGTH_LONG)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        }
        enableSpinner()
    }

    private fun setupStartMiningButton() {
        mining.setEnabled()
        mining.setText("MINING")
        mining.setOnClickListener {
            mining.setDisabled()
            startService()
        }
    }

    private fun setupStopMiningButton() {
        mining.setEnabled()
        mining.setText("STOP")
        mining.setOnClickListener {
            mining.setDisabled()
            stopService()
        }
    }

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            dateService = (binder as DateService.DatesBinder).getService()
            dateService?.let { service ->
                service.service.status.observe(viewLifecycleOwner, Observer { status ->
                    run {

                        Log.d("Mining", "Status: $status")
                        Log.d("Mining", "Bind: $isServiceBound")
                        Log.d("Mining", "Mining: $isMining")

                        if (status.name == "CONNECTING") {
                            mining.setText("CONNECTING")
                        }

                        if (status.name == "STARTED" && !isMining) {
                            isMining = true
                            setupStopMiningButton()
                        }

                        if (status.name == "STOPPED" && isMining) {
                            isMining = false
                            libraryConnected()
                        }
                    }
                })
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            dateService = null
        }
    }

    private fun libraryConnected() {
        setupStartMiningButton()
        enableTestLibrary()
    }

    private fun startService() {
        val intent = Intent(context, DateService::class.java).apply {}
        if (!isServiceBound) bindService()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.startForegroundService(intent)
        } else {
            activity?.startService(intent)
        }
    }

    private fun stopService() {
        val intent = Intent(context, DateService::class.java)
        activity?.stopService(intent)
        unbindService()
    }

    private fun bindService() {
        activity?.bindService(
                Intent(context, DateService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
        )
        isServiceBound = true
    }

    private fun unbindService() {
        if (isServiceBound) {
            try {
                activity?.unbindService(connection)
            } catch (e: Exception) {
                FirebaseCrashlytics.getInstance()
                    .recordException(e)
            }
            isServiceBound = false
        }
    }
}
