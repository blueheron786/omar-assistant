package com.example.omarassistant.orchestrator

import android.content.Context
import android.util.Log
import com.example.omarassistant.model.ExecutionResult
import com.example.omarassistant.model.FunctionCall

/**
 * Abstract base class for all toolbox functions
 * Provides a standardized interface for command execution
 */
abstract class ToolboxFunction {
    abstract val name: String
    abstract val description: String
    abstract suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult
}

/**
 * Toolbox manager that handles registration and execution of functions
 * Provides a plugin-like architecture for extending OMAR's capabilities
 */
class ToolboxManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolboxManager"
    }
    
    private val functions = mutableMapOf<String, ToolboxFunction>()
    
    init {
        // Register built-in functions
        registerBuiltInFunctions()
    }
    
    /**
     * Register a new toolbox function
     */
    fun registerFunction(function: ToolboxFunction) {
        functions[function.name] = function
        Log.d(TAG, "Registered function: ${function.name}")
    }
    
    /**
     * Execute a function by name with parameters
     */
    suspend fun executeFunction(functionCall: FunctionCall): ExecutionResult {
        val function = functions[functionCall.name]
        
        if (function == null) {
            Log.w(TAG, "Unknown function: ${functionCall.name}")
            return ExecutionResult(
                success = false,
                message = "I don't know how to ${functionCall.name}. This function isn't available yet."
            )
        }
        
        try {
            Log.d(TAG, "Executing function: ${functionCall.name} with parameters: ${functionCall.parameters}")
            return function.execute(functionCall.parameters, context)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing function: ${functionCall.name}", e)
            return ExecutionResult(
                success = false,
                message = "I had trouble executing that command. Please try again."
            )
        }
    }
    
    /**
     * Get list of available functions
     */
    fun getAvailableFunctions(): List<String> {
        return functions.keys.toList()
    }
    
    /**
     * Get function description
     */
    fun getFunctionDescription(functionName: String): String? {
        return functions[functionName]?.description
    }
    
    /**
     * Register all built-in functions
     */
    private fun registerBuiltInFunctions() {
        registerFunction(LightControlFunction())
        registerFunction(ThermostatControlFunction())
        registerFunction(VolumeControlFunction())
        registerFunction(FlashlightControlFunction())
        registerFunction(WeatherFunction())
        registerFunction(TimerFunction())
    }
}
