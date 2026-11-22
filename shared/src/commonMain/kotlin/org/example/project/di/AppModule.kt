package org.example.project.di

import org.example.project.viewmodel.MediaViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel {
        MediaViewModel(
            photoRepository = get(),
            cloudRepository = get()
        )
    }
}

expect val platformModule: Module