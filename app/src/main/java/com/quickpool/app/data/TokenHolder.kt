package com.quickpool.app.data

object TokenHolder {
    @Volatile
    var accessToken: String? = null
}