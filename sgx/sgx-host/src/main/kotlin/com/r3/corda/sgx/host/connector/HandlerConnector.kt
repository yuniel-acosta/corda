package com.r3.corda.sgx.host.connector

import com.r3.sgx.core.common.Handler

interface HandlerConnector {
    fun <CONNECTION> setDownstream(handler: Handler<CONNECTION>): CONNECTION
    fun close()
}