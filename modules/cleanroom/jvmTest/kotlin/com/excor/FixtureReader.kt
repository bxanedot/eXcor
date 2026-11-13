package com.excor

import java.io.File

internal actual fun fixture(name: String): String = File(FIXTURES, name).readText()
