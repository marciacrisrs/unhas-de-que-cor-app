package br.com.unhasdequecor

import android.app.Application
import br.com.unhasdequecor.domain.usecase.EnsureDefaultHandReferenceUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class UnhasDeQueCorApp : Application() {

    @Inject
    lateinit var ensureDefaultHandReference: EnsureDefaultHandReferenceUseCase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            ensureDefaultHandReference()
        }
    }
}
