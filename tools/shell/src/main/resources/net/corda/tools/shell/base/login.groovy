package net.corda.tools.shell.base

// Note that this file MUST be in a sub-directory called "base" relative to the path
// given in the configuration code in InteractiveShell.

welcome = """

Welcome to the Corda interactive shell.
Type 'help' to see what commands are available.

"""

prompt = { ->
    return "${new Date()}>>> "
}
