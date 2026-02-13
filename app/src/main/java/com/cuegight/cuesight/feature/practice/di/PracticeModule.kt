package com.cuegight.cuesight.feature.practice.di

import com.cuegight.cuesight.data.database.CueSightDatabase
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import com.cuegight.cuesight.feature.practice.domain.engine.ErpfEngine
import org.koin.dsl.module

val practiceModule = module {

    single { get<CueSightDatabase>().practiceGuessDao() }

    single { get<CueSightDatabase>().practiceSessionDao() }

    single { get<CueSightDatabase>().emotionMasteryDao() }

    single { get<CueSightDatabase>().therapistWeightDao() }

    single { PracticeRepository(get(), get(), get(), get()) }

    single { ErpfEngine(get()) }
}
