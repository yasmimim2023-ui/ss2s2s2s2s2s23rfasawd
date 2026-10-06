package br.com.thorlink

import android.app.Application
import br.com.thorlink.data.LinkRepository

class ThorApp : Application() {
    val repository: LinkRepository by lazy { LinkRepository(this, BuildConfig.IS_RECEIVER) }
}
