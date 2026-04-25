package com.app.ralaunch.core.logging.contract

interface Logger {
    fun v(tag: String, message: String): Int
    fun v(tag: String, message: String, throwable: Throwable?): Int
    fun d(tag: String, message: String): Int
    fun d(tag: String, message: String, throwable: Throwable?): Int
    fun i(tag: String, message: String): Int
    fun i(tag: String, message: String, throwable: Throwable?): Int
    fun w(tag: String, message: String): Int
    fun w(tag: String, message: String, throwable: Throwable?): Int
    fun e(tag: String, message: String): Int
    fun e(tag: String, message: String, throwable: Throwable?): Int
}
