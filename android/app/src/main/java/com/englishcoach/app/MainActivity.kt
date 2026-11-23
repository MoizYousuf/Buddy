package com.englishcoach.app

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.englishcoach.app.ui.App

class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** A tapped alert opens the screen it is about; something shared from another app goes to the Mac. */
    private fun openFrom(intent: android.content.Intent?) {
        when (intent?.action) {
            android.content.Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                          else @Suppress("DEPRECATION") intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                if (uri != null) vm.sendFiles(listOf(uri)) else intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let { vm.sendTextToMac(it) }
                vm.screen = Screen.Transfer; return
            }
            android.content.Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
                           else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM)
                vm.sendFiles(uris ?: emptyList()); vm.screen = Screen.Transfer; return
            }
        }
        if (intent?.getBooleanExtra("voice", false) == true && vm.paired) { vm.voiceStart(); return }
        when (intent?.getStringExtra("screen")) {
            "Studio" -> vm.screen = Screen.Studio
            "Assistant" -> vm.screen = Screen.Assistant
            "Cleaner" -> vm.screen = Screen.Cleaner
            "Videos" -> vm.screen = Screen.Videos
            "Money" -> vm.screen = Screen.Money
            "Ideas" -> vm.screen = Screen.Ideas
            "Staff" -> vm.screen = Screen.Staff
            "Stores" -> { vm.etsyRun = null; vm.etsyStore = null; vm.screen = Screen.Stores }
            "Brief" -> vm.screen = Screen.Home
            "Weekly" -> vm.screen = Screen.Weekly
            "Practice" -> vm.screen = Screen.Home
        }
    }

    override fun onResume() {
        super.onResume()
        vm.checkServer()  // coming back to the app: find the Mac again right away
    }

    private val vm: CoachViewModel by viewModels()

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(this, vm.strings.micPermission, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askMic.launch(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        if (vm.paired) AlertService.start(this)
        Reminders.schedule(this)
        // long-press the app icon -> "Talk to Mac"
        runCatching {
            val sm = getSystemService(android.content.pm.ShortcutManager::class.java)
            sm.dynamicShortcuts = listOf(android.content.pm.ShortcutInfo.Builder(this, "voice")
                .setShortLabel("Talk to Mac").setLongLabel("Talk to your Mac")
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now))
                .setIntent(android.content.Intent(this, MainActivity::class.java).setAction(android.content.Intent.ACTION_VIEW).putExtra("voice", true)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
                .build())
        }
        openFrom(intent)
        setContent {
            BackHandler(vm.screen != Screen.Home) {
                when (vm.screen) {
                    Screen.Review -> vm.screen = Screen.Progress
                    Screen.Assistant, Screen.Remote, Screen.Cleaner, Screen.Studio, Screen.Alerts -> vm.screen = Screen.Mac
                    Screen.Transfer -> if (vm.fileSub.isNotEmpty()) { vm.fileSub = vm.fileSub.substringBeforeLast('/', ""); vm.loadFiles() } else vm.screen = Screen.Mac
                    Screen.Cv -> if (vm.cvOpen != null) vm.cvOpen = null else vm.screen = Screen.Home
                    Screen.Activity -> vm.screen = Screen.Mac
                    Screen.Rules -> vm.screen = Screen.Studio
                    Screen.Money -> vm.screen = Screen.Home
                    Screen.Ideas -> vm.screen = Screen.Home
                    Screen.Staff -> vm.screen = Screen.Home
                    Screen.AppAssistant -> vm.screen = Screen.Home
                    Screen.Stores -> when { vm.etsyRun != null -> vm.etsyRun = null; vm.etsyStore != null -> vm.etsyStore = null; else -> vm.screen = Screen.Mac }
                    Screen.Weekly -> vm.screen = Screen.Progress
                    Screen.Naat -> if (vm.naatOpen != null) vm.naatOpen = null else vm.screen = Screen.Studio
                    Screen.Routines -> if (vm.editing != null) vm.editing = null else vm.screen = Screen.Mac
                    Screen.Videos -> if (vm.openVideo != null) vm.openVideo = null else vm.screen = Screen.Studio
                    else -> vm.leave()
                }
            }
            App(vm)
        }
    }
}
