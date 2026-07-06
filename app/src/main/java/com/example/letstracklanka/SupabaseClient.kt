package com.example.letstracklanka

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.OTP // අලුත් OTP Provider එක
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AuthCallback {
    fun onSuccess()
    fun onError(errorMessage: String)
}

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://riyjkfwxkamqbuuuwdli.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJpeWprZnd4a2FtcWJ1dXV3ZGxpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODEyNzc2NjQsImV4cCI6MjA5Njg1MzY2NH0.Kb7q0cdhJPbCaHb3gjEQOF1ACwU7mrp5uu5hk9ra0zg"
    ) {
        install(Postgrest)
        install(Auth)
    }

    // 1. Phone එකට OTP කේතය යැවීම
    fun sendOTP(phoneNumber: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // පැරණි sendOtpTo වෙනුවට අලුත්ම signInWith(OTP) ක්‍රමය භාවිතා කිරීම
                client.auth.signInWith(OTP) {
                    phone = phoneNumber
                }
                withContext(Dispatchers.Main) { callback.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback.onError(e.message ?: "OTP යැවීමට නොහැකි විය") }
            }
        }
    }

    // 2. ආපු කේතය Verify කිරීම
    fun verifyOTP(phoneNumber: String, code: String, callback: AuthCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.auth.verifyPhoneOtp(
                    type = OtpType.Phone.SMS,
                    phone = phoneNumber,
                    token = code
                )
                withContext(Dispatchers.Main) { callback.onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { callback.onError(e.message ?: "කේතය වැරදියි") }
            }
        }
    }
}