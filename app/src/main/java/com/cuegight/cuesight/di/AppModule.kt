package com.cuegight.cuesight.di

import com.cuegight.cuesight.core.network.ConnectionManager
import com.cuegight.cuesight.core.network.HttpCommandSender
import com.cuegight.cuesight.core.network.HttpMjpegStreamService
import com.cuegight.cuesight.data.database.CueSightDatabase
import com.cuegight.cuesight.data.repository.EmotionLogRepository
import com.cuegight.cuesight.data.repository.SessionRepository
import com.cuegight.cuesight.data.repository.StudentRepository
import com.cuegight.cuesight.feature.connection.ConnectionViewModel
import com.cuegight.cuesight.feature.practice.NewPracticeViewModel
import com.cuegight.cuesight.feature.teaching.NewTeachingViewModel
import com.cuegight.cuesight.service.TcpFrameService
import com.cuegight.cuesight.viewmodel.DashboardViewModel
import com.cuegight.cuesight.viewmodel.PracticeViewModel
import com.cuegight.cuesight.viewmodel.SessionViewModel
import com.cuegight.cuesight.viewmodel.StudentViewModel
import com.cuegight.cuesight.viewmodel.TeachingViewModel
import com.cuegight.cuesight.viewmodel.TestViewModel
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
    
    // Services - Legacy (will be phased out)
    single { TcpFrameService() }
    
    // Services - New HTTP-based
    single { HttpMjpegStreamService() }
    single { HttpCommandSender() }
    single { ConnectionManager(androidContext()) }

    // ViewModels - Legacy (keeping for backwards compatibility during transition)
    viewModel { DashboardViewModel(get(), get(), get()) }
    viewModel { StudentViewModel(get(), get()) }
    viewModel { SessionViewModel(get(), get(), get()) }
    viewModel { TeachingViewModel(get(), get(), get()) }
    viewModel { PracticeViewModel(get(), get(), get()) }
    viewModel { TestViewModel(get()) }
    
    // ViewModels - New HTTP-based (no database logging)
    viewModel { ConnectionViewModel(get(), get()) }
    viewModel { NewTeachingViewModel(get(), get()) }
    viewModel { NewPracticeViewModel(get()) }
}
