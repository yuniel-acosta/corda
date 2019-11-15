package net.corda.node.services.statemachine

import net.corda.core.CordaException

/**
 * This exception is fired once the retry timeout of a [TimedFlow] expires.
 * It will indicate to the flow hospital to restart the flow.
 */
class FlowTimeoutException : CordaException("replaying flow from the last checkpoint")

/**
 * This exception is thrown when the timeout for a receive has elapsed.
 */
class ReceiveTimeoutException: CordaException("receive timed out")