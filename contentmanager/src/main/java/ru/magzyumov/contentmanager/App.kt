package ru.magzyumov.contentmanager

import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import ru.magzyumov.contentmanager.di.DaggerAppComponent


class App: DaggerApplication() {


    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerAppComponent.builder().application(this).build()
    }

}