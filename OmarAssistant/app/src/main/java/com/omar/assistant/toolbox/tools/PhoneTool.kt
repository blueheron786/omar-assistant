package com.omar.assistant.toolbox.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
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
        "number" to "Phone number to call or dial (required for call/dial actions)",
        "name" to "Contact name to look up and call (alternative to number for call/dial actions)"
    )
    
    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    
    override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
        val action = parameters["action"]?.toString()?.lowercase()
        val number = parameters["number"]?.toString()
        val name = parameters["name"]?.toString()
        
        return when (action) {
            "call" -> {
                if (!number.isNullOrBlank()) {
                    makeCall(number)
                } else if (!name.isNullOrBlank()) {
                    makeCallByName(name)
                } else {
                    ToolExecutionResult(
                        success = false,
                        message = "Either phone number or contact name is required for making a call"
                    )
                }
            }
            "dial" -> {
                if (!number.isNullOrBlank()) {
                    dialNumber(number)
                } else if (!name.isNullOrBlank()) {
                    dialByName(name)
                } else {
                    ToolExecutionResult(
                        success = false,
                        message = "Either phone number or contact name is required for dialing"
                    )
                }
            }
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
                val name = parameters["name"]?.toString()
                action in listOf("call", "dial") && (!number.isNullOrBlank() || !name.isNullOrBlank())
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
    
    private fun makeCallByName(name: String): ToolExecutionResult {
        return try {
            val phoneNumber = lookupContactByName(name)
            if (phoneNumber != null) {
                makeCall(phoneNumber)
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Contact '$name' not found or has no phone number"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling contact by name", e)
            ToolExecutionResult(
                success = false,
                message = "Error looking up contact: ${e.message}"
            )
        }
    }
    
    private fun dialByName(name: String): ToolExecutionResult {
        return try {
            val phoneNumber = lookupContactByName(name)
            if (phoneNumber != null) {
                dialNumber(phoneNumber)
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Contact '$name' not found or has no phone number"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dialing contact by name", e)
            ToolExecutionResult(
                success = false,
                message = "Error looking up contact: ${e.message}"
            )
        }
    }
    
    private fun lookupContactByName(name: String): String? {
        // Check if we have permission to read contacts
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Contacts permission not granted")
            return null
        }
        
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            
            // Query contacts with phone numbers
            val cursor: Cursor? = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    if (nameIndex >= 0 && numberIndex >= 0) {
                        var bestMatch: Pair<String, String>? = null
                        var exactMatch: Pair<String, String>? = null
                        
                        do {
                            val contactName = c.getString(nameIndex)
                            val phoneNumber = c.getString(numberIndex)
                            
                            if (contactName != null && phoneNumber != null) {
                                // Check for exact match (case-insensitive)
                                if (contactName.equals(name, ignoreCase = true)) {
                                    exactMatch = Pair(contactName, phoneNumber)
                                    break
                                }
                                
                                // Check for partial match (first name, last name, or contains)
                                if (bestMatch == null && 
                                    (contactName.contains(name, ignoreCase = true) ||
                                     name.split(" ").any { part -> 
                                         contactName.contains(part, ignoreCase = true) && part.length > 2 
                                     })) {
                                    bestMatch = Pair(contactName, phoneNumber)
                                }
                            }
                        } while (c.moveToNext())
                        
                        val result = exactMatch ?: bestMatch
                        if (result != null) {
                            Log.d(TAG, "Found contact: ${result.first} -> ${result.second}")
                            return result.second
                        }
                    }
                }
            }
            
            Log.d(TAG, "No contact found for name: $name")
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
            return null
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
