package com.example.splitreader.data.update

import android.app.Application
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.example.splitreader.domain.model.UpdateState
import com.example.splitreader.domain.repository.AppUpdates.Companion.SNOOZE_MILLIS
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Wraps the fake to hold or fail Play's answer and to record which AppUpdateInfo each flow used.
 */
private class CountingManager(private val inner: AppUpdateManager) : AppUpdateManager by inner {
    var hold: TaskCompletionSource<AppUpdateInfo>? = null
    var fail = false
    val startedWith = mutableListOf<AppUpdateInfo>()

    override fun getAppUpdateInfo(): Task<AppUpdateInfo> = when {
        fail -> Tasks.forException(IllegalStateException("not owned"))
        hold != null -> hold!!.task.also { hold = null }
        else -> inner.appUpdateInfo
    }

    override fun startUpdateFlowForResult(
        info: AppUpdateInfo,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        options: AppUpdateOptions,
    ): Boolean {
        startedWith += info
        return inner.startUpdateFlowForResult(info, launcher, options)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlayAppUpdatesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("app_updates_test", Context.MODE_PRIVATE)
    private lateinit var fake: FakeAppUpdateManager
    private var clock = 1_000_000L

    private val launcher = object : ActivityResultLauncher<IntentSenderRequest>() {
        override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit
        override fun unregister() = Unit
        override val contract: ActivityResultContract<IntentSenderRequest, *> =
            ActivityResultContracts.StartIntentSenderForResult()
    }

    private fun updates() = PlayAppUpdates(fake, prefs) { clock }

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        fake = FakeAppUpdateManager(context)
    }

    /** Play delivers task results and install states through the main looper. */
    private fun idle() = ShadowLooper.idleMainLooper()

    @Test
    fun `no newer version means no offer`() = runTest {
        val u = updates()
        fake.setUpdateNotAvailable()
        u.refresh()
        assertEquals(UpdateState.None, u.state.value)
    }

    @Test
    fun `a newer version is offered`() = runTest {
        val u = updates()
        fake.setUpdateAvailable(10)
        u.refresh()
        assertEquals(UpdateState.Available(10, snoozed = false), u.state.value)
    }

    @Test
    fun `not now hides this version for three days, then offers it again`() = runTest {
        fake.setUpdateAvailable(10)
        val u = updates()
        u.refresh()
        u.snooze()
        assertEquals(UpdateState.Available(10, snoozed = true), u.state.value)

        // A relaunch inside the window still remembers it.
        clock += SNOOZE_MILLIS - 1
        updates().also { it.refresh() }.let { assertEquals(UpdateState.Available(10, snoozed = true), it.state.value) }

        clock += 2
        updates().also { it.refresh() }.let { assertEquals(UpdateState.Available(10, snoozed = false), it.state.value) }
    }

    @Test
    fun `not now on one version does not hide a newer one`() = runTest {
        fake.setUpdateAvailable(10)
        val u = updates()
        u.refresh()
        u.snooze()
        fake.setUpdateAvailable(11)
        u.refresh()
        assertEquals(UpdateState.Available(11, snoozed = false), u.state.value)
    }

    @Test
    fun `update downloads in the background, then installs on restart`() = runTest {
        fake.setUpdateAvailable(10)
        val u = updates()
        u.attach(launcher)
        u.refresh()

        assertTrue(u.startUpdate())
        assertTrue(fake.isConfirmationDialogVisible)
        fake.userAcceptsUpdate()
        idle()
        assertEquals(UpdateState.Downloading(null), u.state.value)

        fake.downloadStarts()
        fake.setTotalBytesToDownload(200)
        fake.setBytesDownloaded(50)
        idle()
        assertEquals(UpdateState.Downloading(25), u.state.value)

        fake.downloadCompletes()
        idle()
        assertEquals(UpdateState.Downloaded, u.state.value)

        u.completeUpdate()
        assertTrue(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun `an update downloaded while the app was away is ready on return`() = runTest {
        fake.setUpdateAvailable(10)
        val first = updates()
        first.attach(launcher)
        first.refresh()
        first.startUpdate()
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        fake.downloadCompletes()
        idle()

        val relaunched = updates()
        relaunched.refresh()
        assertEquals(UpdateState.Downloaded, relaunched.state.value)
    }

    @Test
    fun `a failed download offers the update again`() = runTest {
        fake.setUpdateAvailable(10)
        val u = updates()
        u.attach(launcher)
        u.refresh()
        u.startUpdate()
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        idle()
        fake.downloadFails()
        idle()
        assertEquals(UpdateState.Available(10, snoozed = false), u.state.value)
    }

    @Test
    fun `after No thanks, Update opens the confirmation again with a fresh answer from Play`() = runTest {
        fake.setUpdateAvailable(10)
        val gated = CountingManager(fake)
        val u = PlayAppUpdates(gated, prefs) { clock }
        u.attach(launcher)
        u.refresh()
        assertTrue(u.startUpdate())
        fake.userRejectsUpdate()
        assertFalse(fake.isConfirmationDialogVisible)

        assertTrue(u.startUpdate())
        assertTrue(fake.isConfirmationDialogVisible)
        // Play accepts each AppUpdateInfo for one flow: both starts used a different one.
        assertEquals(2, gated.startedWith.distinct().size)
    }

    @Test
    fun `a second tap while the confirmation is being opened does nothing`() = runTest {
        fake.setUpdateAvailable(10)
        val gated = CountingManager(fake)
        val u = PlayAppUpdates(gated, prefs) { clock }
        u.attach(launcher)
        u.refresh()
        val gate = TaskCompletionSource<AppUpdateInfo>()
        gated.hold = gate
        val first = async { u.startUpdate() }
        runCurrent()
        assertFalse(u.startUpdate())
        gate.setResult(fake.appUpdateInfo.result)
        idle()
        assertTrue(first.await())
        assertEquals(1, gated.startedWith.size)
    }

    @Test
    fun `a failed answer from Play keeps what was shown`() = runTest {
        fake.setUpdateAvailable(10)
        val gated = CountingManager(fake)
        val u = PlayAppUpdates(gated, prefs) { clock }
        u.refresh()
        gated.fail = true
        u.refresh()
        assertEquals(UpdateState.Available(10, snoozed = false), u.state.value)
    }

    @Test
    fun `a sideloaded or debug build, which Play refuses, shows nothing`() = runTest {
        val gated = CountingManager(fake).apply { fail = true }
        val u = PlayAppUpdates(gated, prefs) { clock }
        u.refresh()
        assertEquals(UpdateState.None, u.state.value)
    }

    @Test
    fun `a download finishing while a refresh waits for Play keeps Restart`() = runTest {
        fake.setUpdateAvailable(10)
        val gated = CountingManager(fake)
        val u = PlayAppUpdates(gated, prefs) { clock }
        u.attach(launcher)
        u.refresh()
        u.startUpdate()
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        idle()
        val staleDownloading = fake.appUpdateInfo.result

        val gate = TaskCompletionSource<AppUpdateInfo>()
        gated.hold = gate
        val refresh = launch { u.refresh() }
        runCurrent()
        fake.downloadCompletes()
        idle()
        assertEquals(UpdateState.Downloaded, u.state.value)

        gate.setResult(staleDownloading) // answered before DOWNLOADED arrived
        idle()
        refresh.join()
        assertEquals(UpdateState.Downloaded, u.state.value)
    }

    @Test
    fun `a download that outlived the process offers the update again if it fails`() = runTest {
        fake.setUpdateAvailable(10)
        val first = updates()
        first.attach(launcher)
        first.refresh()
        first.startUpdate()
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        idle()

        val relaunched = updates() // never saw the offer itself
        relaunched.refresh()
        fake.downloadFails()
        idle()
        assertEquals(UpdateState.Available(10, snoozed = false), relaunched.state.value)
    }

    @Test
    fun `without an activity the confirmation can't be shown`() = runTest {
        fake.setUpdateAvailable(10)
        val u = updates()
        u.refresh()
        assertFalse(u.startUpdate())
        assertFalse(fake.isConfirmationDialogVisible)
    }
}
