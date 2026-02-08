package com.cuegight.cuesight.di

import com.cuegight.cuesight.data.database.CueSightDatabase
import com.cuegight.cuesight.data.repository.EmotionLogRepository
import com.cuegight.cuesight.data.repository.SessionRepository
import com.cuegight.cuesight.data.repository.StudentRepository
import com.cuegight.cuesight.viewmodel.DashboardViewModel
import com.cuegight.cuesight.viewmodel.SessionViewModel
import com.cuegight.cuesight.viewmodel.StudentViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { CueSightDatabase.getDatabase(androidContext()) }
    
    // Repositories
    single { StudentRepository(get()) }
    single { SessionRepository(get()) }
    single { EmotionLogRepository(get()) }
    
    // ViewModels
    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { StudentViewModel(get(), get(), get()) }
    viewModel { SessionViewModel(get(), get(), androidContext()) }
}
