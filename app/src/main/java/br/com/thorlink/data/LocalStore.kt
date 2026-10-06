package br.com.thorlink.data

import android.content.Context
import br.com.thorlink.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Only settings, pinned identities and bounded session history. No screen or control recordings. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("authorized_settings", Context.MODE_PRIVATE)
    fun authorized(): List<AuthorizedPeer> = runCatching {
        val a = JSONArray(prefs.getString("peers", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { AuthorizedPeer(it.getString("address"), it.getString("name"), it.getString("fp")) } }
    }.getOrDefault(emptyList())
    @Synchronized fun remember(peer: AuthorizedPeer) {
        val peers = authorized().filterNot { it.address == peer.address } + peer
        prefs.edit().putString("peers", JSONArray().apply { peers.forEach { put(JSONObject().put("address", it.address).put("name", it.name).put("fp", it.fingerprint)) } }.toString()).apply()
    }
    @Synchronized fun forget(address: String) {
        prefs.edit().putString("peers", JSONArray().apply { authorized().filterNot { it.address == address }.forEach {
            put(JSONObject().put("address", it.address).put("name", it.name).put("fp", it.fingerprint))
        } }.toString()).apply()
    }
    fun history(): List<HistoryEntry> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { HistoryEntry(it.getLong("time"), it.getString("name"), it.getString("result")) } }
    }.getOrDefault(emptyList())
    @Synchronized fun log(name: String, result: String) {
        val entries = (listOf(HistoryEntry(System.currentTimeMillis(), name, result)) + history()).take(50)
        prefs.edit().putString("history", JSONArray().apply { entries.forEach { put(JSONObject().put("time", it.time).put("name", it.name).put("result", it.result)) } }.toString()).apply()
    }
    fun clearHistory() { prefs.edit().remove("history").apply() }
    fun controlMode() = if(prefs.getBoolean("nativeHid",true)) ControlMode.HID else ControlMode.GESTURES
    fun controlMode(mode:ControlMode) { prefs.edit().putBoolean("nativeHid",mode==ControlMode.HID).apply() }
    fun target() = prefs.getString("target", "").orEmpty()
    fun targetLabel() = prefs.getString("targetLabel", "Modo de teste").orEmpty()
    fun target(pkg: String, label: String) { prefs.edit().putString("target", pkg).putString("targetLabel", label).apply() }
    fun mapping(): Mapping = runCatching {
        val a = prefs.getString("mapping", null)?.split(",")?.map { it.toFloat() } ?: return Mapping()
        Mapping(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10],a[11],a[12])
    }.getOrDefault(Mapping())
    fun mapping(m: Mapping) { prefs.edit().putString("mapping", listOf(m.stickX,m.stickY,m.radius,m.aX,m.aY,m.bX,m.bY,m.xX,m.xY,m.yX,m.yY,m.rightX,m.rightY).joinToString(",")).apply() }
}
