package com.zayd.assistant

import android.app.Application
import com.zayd.assistant.core.di.ServiceLocator

/**
 * Main application class for Zayd Assistant
 * Initializes core components and dependency injection
 */
class ZaydApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize service locator for dependency injection
        ServiceLocator.initialize(this)
    }
}
