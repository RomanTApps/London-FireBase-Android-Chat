package anonimous.chat.london

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.storage.FirebaseStorage
import java.util.*
import android.graphics.drawable.ColorDrawable




class MainActivity : AppCompatActivity() {

    private var mMessageListView: ListView? = null
    private var mMessageAdapter: MessageAdapter? = null
    private var mProgressBar: ProgressBar? = null
    private var mPhotoPickerButton: ImageButton? = null
    private var mMessageEditText: EditText? = null
    private var mSendButton: Button? = null

    private var mUsername: String? = null

    // Firebase instance variables
    private var mFirebaseDatabase: FirebaseDatabase? = null
    private var mMessagesDatabaseReference: DatabaseReference? = null
    private var mChildEventListener: ChildEventListener? = null
    private var mFirebaseAuth: FirebaseAuth? = null
    private var mAuthStateListener: FirebaseAuth.AuthStateListener? = null
    private var mFirebaseStorage: FirebaseStorage? = null
    private var mFirebaseRemoteConfig: FirebaseRemoteConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mUsername = ANONYMOUS

        // Initialize Firebase components
        mFirebaseDatabase = FirebaseDatabase.getInstance()
        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseStorage = FirebaseStorage.getInstance()
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
        mMessagesDatabaseReference = mFirebaseDatabase!!.reference.child("messages")

        // Initialize references to views
        mProgressBar = findViewById<ProgressBar>(R.id.progressBar)
        mMessageListView = findViewById<ListView>(R.id.messageListView)
        mPhotoPickerButton = findViewById<ImageButton>(R.id.photoPickerButton)
        mMessageEditText = findViewById<EditText>(R.id.messageEditText)
        mSendButton = findViewById<Button>(R.id.sendButton)

        // Initialize message ListView and its adapter
        val friendlyMessages = ArrayList<FriendlyMessage>()
        mMessageAdapter = MessageAdapter(this, R.layout.item_message, friendlyMessages)
        mMessageListView!!.adapter = mMessageAdapter

        // Initialize progress bar
        mProgressBar!!.visibility = ProgressBar.INVISIBLE

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton!!.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER)
        }

        // Enable Send button when there's text to send
        mMessageEditText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                mSendButton!!.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        mMessageEditText!!.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))

        // Send button sends a message and clears the EditText
        mSendButton!!.setOnClickListener {
            val friendlyMessage = FriendlyMessage(mMessageEditText!!.text.toString(), mUsername, null)
            mMessagesDatabaseReference!!.push().setValue(friendlyMessage)

            // Clear input box
            mMessageEditText!!.setText("")
        }

        mAuthStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in
                onSignedInInitialize(user.displayName)
            } else {
                // User is signed out
                onSignedOutCleanup()
                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setIsSmartLockEnabled(false)
                                .enableAnonymousUsersAutoUpgrade()
                                .setAvailableProviders(arrayListOf(
                                        AuthUI.IdpConfig.AnonymousBuilder().build(),
                                        AuthUI.IdpConfig.EmailBuilder().build(),
                                        AuthUI.IdpConfig.PhoneBuilder().build(),
                                        AuthUI.IdpConfig.GoogleBuilder().build()
                                ))
                                .build(),
                        RC_SIGN_IN)
            }
        }


    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                // Sign-in succeeded, set up the UI
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // Sign in was canceled by the user, finish the activity
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK) {


        }
    }


    override fun onResume() {
        super.onResume()
        mFirebaseAuth!!.addAuthStateListener(mAuthStateListener!!)
    }

    override fun onPause() {
        super.onPause()
        if (mAuthStateListener != null) {
            mFirebaseAuth!!.removeAuthStateListener(mAuthStateListener!!)
        }
        mMessageAdapter!!.clear()
        detachDatabaseReadListener()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sign_out_menu -> {
                AuthUI.getInstance().signOut(this)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun onSignedInInitialize(username: String?) {
        mUsername = username
        attachDatabaseReadListener()
    }

    private fun onSignedOutCleanup() {
        mUsername = ANONYMOUS
        mMessageAdapter!!.clear()
        detachDatabaseReadListener()
    }

    private fun attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = object : ChildEventListener {
                override fun onChildAdded(dataSnapshot: DataSnapshot, s: String?) {
                    val friendlyMessage = dataSnapshot.getValue(FriendlyMessage::class.java)
                    mMessageAdapter!!.add(friendlyMessage)
                }

                override fun onChildChanged(dataSnapshot: DataSnapshot, s: String?) {}
                override fun onChildRemoved(dataSnapshot: DataSnapshot) {}
                override fun onChildMoved(dataSnapshot: DataSnapshot, s: String?) {}
                override fun onCancelled(databaseError: DatabaseError) {}
            }
            mMessagesDatabaseReference!!.addChildEventListener(mChildEventListener!!)
        }
    }

    private fun detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            mMessagesDatabaseReference!!.removeEventListener(mChildEventListener!!)
            mChildEventListener = null
        }
    }

    // Fetch the config to determine the allowed length of messages.
    fun fetchConfig() {
        var cacheExpiration: Long = 3600 // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that each fetch goes to the
        // server. This should not be used in release builds.
        if (mFirebaseRemoteConfig!!.info.configSettings.isDeveloperModeEnabled) {
            cacheExpiration = 0
        }
        mFirebaseRemoteConfig!!.fetch(cacheExpiration)
                .addOnSuccessListener {
                    // Make the fetched config available
                    // via FirebaseRemoteConfig get<type> calls, e.g., getLong, getString.
                    mFirebaseRemoteConfig!!.activateFetched()

                    // Update the EditText length limit with
                    // the newly retrieved values from Remote Config.
                    applyRetrievedLengthLimit()
                }
                .addOnFailureListener { e ->
                    // An error occurred when fetching the config.
                    Log.w(TAG, "Error fetching config", e)

                    // Update the EditText length limit with
                    // the newly retrieved values from Remote Config.
                    applyRetrievedLengthLimit()
                }
    }


    private fun applyRetrievedLengthLimit() {
        val friendly_msg_length = mFirebaseRemoteConfig!!.getLong(FRIENDLY_MSG_LENGTH_KEY)
        mMessageEditText!!.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(friendly_msg_length.toInt()))
        Log.d(TAG, "$FRIENDLY_MSG_LENGTH_KEY = $friendly_msg_length")
    }

    companion object {

        private val TAG = "MainActivity"

        val ANONYMOUS = "anonymous"
        val DEFAULT_MSG_LENGTH_LIMIT = 1000
        val FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length"

        val RC_SIGN_IN = 1
        private val RC_PHOTO_PICKER = 2
    }
}