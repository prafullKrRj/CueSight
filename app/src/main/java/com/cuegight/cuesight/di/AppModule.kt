package com.cuegight.cuesight.di

import com.cuegight.cuesight.core.network.ConnectionManager
import com.cuegight.cuesight.core.network.HttpCommandSender
import com.cuegight.cuesight.core.network.HttpMjpegStreamService
import com.cuegight.cuesight.data.database.CueSightDatabase
import com.cuegight.cuesight.data.repository.StudentRepository
import com.cuegight.cuesight.feature.analytics.AnalyticsViewModel
import com.cuegight.cuesight.feature.analytics.StudentAnalyticsViewModel
import com.cuegight.cuesight.feature.connection.ConnectionViewModel
import com.cuegight.cuesight.feature.practice.NewPracticeViewModel
import com.cuegight.cuesight.feature.teaching.NewTeachingViewModel
import com.cuegight.cuesight.viewmodel.StudentViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single { CueSightDatabase.getDatabase(androidContext()) }
    
    // Repositories
    single { StudentRepository(get()) }
    
    // Services - HTTP-based
    single { HttpMjpegStreamService() }
    single { HttpCommandSender() }
    single { ConnectionManager(androidContext()) }

    // ViewModels - HTTP-based
    viewModel { StudentViewModel(get(), get()) }
    viewModel { ConnectionViewModel(get(), get()) }
    viewModel { NewTeachingViewModel(get(), get()) }
    viewModel { NewPracticeViewModel(get(), get()) }
    viewModel { AnalyticsViewModel(get(), get()) }
    viewModel { (studentId: Long) -> 
        StudentAnalyticsViewModel(studentId, get(), get())
    }
}
