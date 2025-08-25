package com.omar.assistant

import android.app.Application
import com.omar.assistant.core.di.ServiceLocator

/**
 * Main application class for Omar Assistant
 * Initializes core components and dependency injection
 */
class OmarApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize service locator for dependency injection
        ServiceLocator.initialize(this)
    }
}
