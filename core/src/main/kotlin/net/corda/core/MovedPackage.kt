package net.corda.core

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@CordaInternal
annotation class MovedPackage(val originalPackage: String, val newPackage: String)