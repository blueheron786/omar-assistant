package com.omar.assistant.toolbox.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.omar.assistant.toolbox.Tool
import com.omar.assistant.toolbox.ToolExecutionResult

/**
 * Tool for phone-related operations
 * Allows making phone calls and checking phone state via voice commands
 */
class PhoneTool(private val context: Context) : Tool {
    
    companion object {
        private const val TAG = "PhoneTool"
    }
    
    override val name: String = "phone"
    
    override val description: String = "Controls phone functions (make calls, check phone state)"
    
    override val parameters: Map<String, String> = mapOf(
        "action" to "Action to perform: 'call', 'dial', or 'status'",
        "number" to "Phone number to call or dial (required for call/dial actions)"
    )
    
    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    
    override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
        val action = parameters["action"]?.toString()?.lowercase()
        val number = parameters["number"]?.toString()
        
        return when (action) {
            "call" -> makeCall(number)
            "dial" -> dialNumber(number)
            "status" -> getPhoneStatus()
            else -> ToolExecutionResult(
                success = false,
                message = "Invalid action. Use 'call', 'dial', or 'status'"
            )
        }
    }
    
    override fun validateParameters(parameters: Map<String, Any>): Boolean {
        val action = parameters["action"]?.toString()?.lowercase()
        
        return when (action) {
            "call", "dial" -> {
                val number = parameters["number"]?.toString()
                action in listOf("call", "dial") && !number.isNullOrBlank()
            }
            "status" -> true
            else -> false
        }
    }
    
    private fun makeCall(number: String?): ToolExecutionResult {
        if (number.isNullOrBlank()) {
            return ToolExecutionResult(
                success = false,
                message = "Phone number is required for making a call"
            )
        }
        
        // Check if we have permission to make calls
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) 
            != PackageManager.PERMISSION_GRANTED) {
            return ToolExecutionResult(
                success = false,
                message = "Call permission not granted. Please grant phone permission in app settings."
            )
        }
        
        return try {
            // Clean the number (remove spaces, dashes, etc.)
            val cleanNumber = cleanPhoneNumber(number)
            
            if (!isValidPhoneNumber(cleanNumber)) {
                return ToolExecutionResult(
                    success = false,
                    message = "Invalid phone number format"
                )
            }
            
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Initiated call to $cleanNumber")
            
            ToolExecutionResult(
                success = true,
                message = "Calling $number",
                data = mapOf("action" to "call", "number" to cleanNumber)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call", e)
            ToolExecutionResult(
                success = false,
                message = "Failed to make call: ${e.message}"
            )
        }
    }
    
    private fun dialNumber(number: String?): ToolExecutionResult {
        if (number.isNullOrBlank()) {
            return ToolExecutionResult(
                success = false,
                message = "Phone number is required for dialing"
            )
        }
        
        return try {
            // Clean the number
            val cleanNumber = cleanPhoneNumber(number)
            
            if (!isValidPhoneNumber(cleanNumber)) {
                return ToolExecutionResult(
                    success = false,
                    message = "Invalid phone number format"
                )
            }
            
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            context.startActivity(intent)
            Log.d(TAG, "Opened dialer with $cleanNumber")
            
            ToolExecutionResult(
                success = true,
                message = "Opened dialer with $number",
                data = mapOf("action" to "dial", "number" to cleanNumber)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open dialer", e)
            ToolExecutionResult(
                success = false,
                message = "Failed to open dialer: ${e.message}"
            )
        }
    }
    
    private fun getPhoneStatus(): ToolExecutionResult {
        return try {
            val data = mutableMapOf<String, Any>()
            
            // Check if we have READ_PHONE_STATE permission
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) 
                == PackageManager.PERMISSION_GRANTED) {
                
                // Get phone state information
                data["callState"] = when (telephonyManager.callState) {
                    TelephonyManager.CALL_STATE_IDLE -> "idle"
                    TelephonyManager.CALL_STATE_RINGING -> "ringing"
                    TelephonyManager.CALL_STATE_OFFHOOK -> "active_call"
                    else -> "unknown"
                }
                
                data["networkType"] = when (telephonyManager.networkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                    TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
                    TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                    TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
                    else -> "unknown"
                }
                
                try {
                    data["networkOperator"] = telephonyManager.networkOperatorName ?: "unknown"
                } catch (e: Exception) {
                    data["networkOperator"] = "unknown"
                }
                
                data["simState"] = when (telephonyManager.simState) {
                    TelephonyManager.SIM_STATE_READY -> "ready"
                    TelephonyManager.SIM_STATE_ABSENT -> "absent"
                    TelephonyManager.SIM_STATE_UNKNOWN -> "unknown"
                    else -> "other"
                }
            } else {
                data["error"] = "Phone state permission not granted"
            }
            
            data["hasPhoneFeature"] = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
            
            val message = buildString {
                append("Phone status: ")
                if (data.containsKey("callState")) {
                    append("Call state is ${data["callState"]}, ")
                    append("Network: ${data["networkOperator"]} (${data["networkType"]}), ")
                    append("SIM: ${data["simState"]}")
                } else {
                    append("Limited information available (permission required)")
                }
            }
            
            ToolExecutionResult(
                success = true,
                message = message,
                data = data
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get phone status", e)
            ToolExecutionResult(
                success = false,
                message = "Failed to get phone status: ${e.message}"
            )
        }
    }
    
    private fun cleanPhoneNumber(number: String): String {
        // Remove all non-digit characters except + at the beginning
        return number.replace(Regex("[^\\d+]"), "").let { cleaned ->
            if (cleaned.startsWith("+")) {
                "+" + cleaned.substring(1).replace(Regex("[^\\d]"), "")
            } else {
                cleaned.replace(Regex("[^\\d]"), "")
            }
        }
    }
    
    private fun isValidPhoneNumber(number: String): Boolean {
        // Basic validation - should have at least 3 digits
        // Can start with + for international numbers
        return when {
            number.isEmpty() -> false
            number.startsWith("+") -> number.length >= 4 && number.substring(1).all { it.isDigit() }
            else -> number.length >= 3 && number.all { it.isDigit() }
        }
    }
}
