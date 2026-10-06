package br.com.thorlink.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import br.com.thorlink.ThorApp
import br.com.thorlink.domain.*

class LinkViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as ThorApp).repository
    val state = repo.state
    val receiver = repo.receiver
    fun disconnect() = repo.disconnect()
    fun confirm(g: Grants) = repo.confirm(g)
    fun controls(c: ControlState) = repo.controls(c)
    fun selectFile(uri: Uri) = repo.offerFile(uri)
}
