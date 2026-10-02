package com.najmulcodes.vidgrab.di

import javax.inject.Qualifier

/** The process-wide coroutine scope used for work that must outlive any screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
