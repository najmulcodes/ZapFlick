package com.najmulcodes.zapflick.di

import com.najmulcodes.zapflick.domain.security.PinManager
import com.najmulcodes.zapflick.domain.security.PinStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun providePinManager(store: PinStore): PinManager = PinManager(store)
}
