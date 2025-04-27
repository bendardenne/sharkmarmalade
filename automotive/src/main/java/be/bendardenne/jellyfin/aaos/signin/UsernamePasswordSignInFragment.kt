package be.bendardenne.jellyfin.aaos.signin

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import be.bendardenne.jellyfin.aaos.R
import be.bendardenne.jellyfin.aaos.signin.SignInActivityViewModel.Companion.JELLYFIN_SERVER_URL
import kotlinx.coroutines.launch


class UsernamePasswordSignInFragment : Fragment() {

    private lateinit var viewModel: SignInActivityViewModel
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button

    private lateinit var quickConnectCode: TextView
    private lateinit var quickConnectProgressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.username_password_sign_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[SignInActivityViewModel::class.java]
        val server = arguments?.getString(JELLYFIN_SERVER_URL)!!

        usernameInput = view.findViewById(R.id.username)
        passwordInput = view.findViewById(R.id.password)
        loginButton = view.findViewById(R.id.login_button)
        quickConnectCode = view.findViewById(R.id.quickconnect_code)
        quickConnectProgressBar = view.findViewById(R.id.quickconnect_progressbar)

        viewModel.startQuickConnect(server)

        viewModel.quickConnectCode.observe(viewLifecycleOwner, object : Observer<Int> {
            override fun onChanged(value: Int) {
                quickConnectProgressBar.visibility = View.GONE
                quickConnectCode.visibility = View.VISIBLE

                if( value == -1 ){
                    quickConnectCode.text = context?.getText(R.string.unavailable)
                } else {
                    val code = value.toString()
                    val formattedCode = code.substring(0, 3) + " " + code.substring(3)
                    quickConnectCode.text = formattedCode
                }
            }
        })

        loginButton.setOnClickListener {
            val username = usernameInput.text
            val password = passwordInput.text

            if (TextUtils.isEmpty(username)) {
                toast(R.string.username_textfield_error)
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = viewModel.login(server, username.toString(), password.toString())

                    if (!result) {
                        toast(R.string.login_unsuccessful)
                    }

                    // If successful, the Activity will finish. Apparently we need to manually hide the keyboard.
                    val inputManager =
                        activity?.getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputManager.hideSoftInputFromWindow(
                        activity?.currentFocus?.windowToken,
                        InputMethodManager.HIDE_NOT_ALWAYS
                    )
                }
            }

        }
    }

    private fun toast(message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}