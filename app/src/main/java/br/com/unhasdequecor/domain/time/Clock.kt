package br.com.unhasdequecor.domain.time

fun interface Clock {
    fun now(): Long
}
