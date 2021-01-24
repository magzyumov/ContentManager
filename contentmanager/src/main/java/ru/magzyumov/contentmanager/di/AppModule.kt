package ru.magzyumov.contentmanager.di

import android.app.Application
import dagger.Module
import dagger.Provides
import ru.magzyumov.contentmanager.filemanager.StorageManager


@Module
class AppModule {

    @Provides
    fun provideStorageManager(application: Application): StorageManager {
        return StorageManager(application)
    }

}