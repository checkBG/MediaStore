package org.example.project.di

import org.example.project.AndroidPhotoRepository
import org.example.project.PhotoRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module


actual val platformModule: Module = module {

    includes(networkModule)

    single<PhotoRepository> { AndroidPhotoRepository(androidContext()) }
}