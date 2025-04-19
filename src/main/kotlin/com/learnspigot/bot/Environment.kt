package com.learnspigot.bot

import io.github.cdimascio.dotenv.Dotenv

object Environment {
    private val env = Dotenv.configure().systemProperties().load()
    operator fun get(variable: String) = env[variable]
}