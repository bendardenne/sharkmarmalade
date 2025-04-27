package be.bendardenne.jellyfin.aaos.signin

import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import be.bendardenne.jellyfin.aaos.R
import be.bendardenne.jellyfin.aaos.signin.SignInActivityViewModel.Companion.JELLYFIN_SERVER_URL
import kotlinx.coroutines.launch


class ServerSignInFragment : Fragment() {

    private lateinit var serverInput: EditText
    private lateinit var submitServer: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var viewModel: SignInActivityViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.server_sign_in, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[SignInActivityViewModel::class.java]

        serverInput = view.findViewById(R.id.server_uri)
        submitServer = view.findViewById(R.id.submit_server_button)
        progressBar = view.findViewById(R.id.progress_bar)

        submitServer.setOnClickListener {
            val serverUrl = serverInput.text
            if (!TextUtils.isEmpty(serverUrl)) {
                progressBar.visibility = View.VISIBLE

                viewLifecycleOwner.lifecycleScope.launch {
                    val pingServer = viewModel.pingServer(serverUrl.toString())

                    if (pingServer) {
                        signInToServer(serverUrl)
                    } else {
                        progressBar.visibility = View.INVISIBLE
                        Toast.makeText(context, R.string.server_unreachable, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    private fun signInToServer(serverUrl: Editable) {
        val args = Bundle()
        args.putString(JELLYFIN_SERVER_URL, serverUrl.toString())
        val fragment = UsernamePasswordSignInFragment()
        fragment.arguments = args

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.sign_in_container, fragment)
            .addToBackStack("landingPage")
            .commit()
    }
}