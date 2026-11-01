package com.salesdrive.voice

import com.salesdrive.BuildConfig

/** local.properties의 pleos.clientId / pleos.clientSecret. 없으면 음성 SDK를 On-device 모드로 쓴다 */
internal object PleosCredentials {
    val clientId: String = BuildConfig.PLEOS_CLIENT_ID
    val clientSecret: String = BuildConfig.PLEOS_CLIENT_SECRET
    val isSet: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}
