package com.movtery.zalithlauncher.game.download.jvm_server

/*
 * Rodízio de processos para o instalador.
 * Alguns sistemas (HyperOS/MIUI e outros) bloqueiam um processo que morre várias vezes seguidas
 * ("proc frequent died"). Cada execução do instalador usa o próximo processo da lista (:jvm, :jvm1 ... :jvm9),
 * então nenhum deles morre vezes suficientes para ser bloqueado.
 */
class JvmService1 : JvmService()
class JvmService2 : JvmService()
class JvmService3 : JvmService()
class JvmService4 : JvmService()
class JvmService5 : JvmService()
class JvmService6 : JvmService()
class JvmService7 : JvmService()
class JvmService8 : JvmService()
class JvmService9 : JvmService()

private val JVM_SERVICE_POOL: List<Class<out JvmService>> = listOf(
    JvmService::class.java, JvmService1::class.java, JvmService2::class.java, JvmService3::class.java,
    JvmService4::class.java, JvmService5::class.java, JvmService6::class.java, JvmService7::class.java,
    JvmService8::class.java, JvmService9::class.java
)

private var poolIndex = -1

/** Serviço usado na última execução (para parar o serviço certo). */
fun currentJvmServiceClass(): Class<out JvmService> = JVM_SERVICE_POOL[poolIndex.coerceAtLeast(0)]

/** Avança para o próximo processo do rodízio. */
@Synchronized
fun nextJvmServiceClass(): Class<out JvmService> {
    poolIndex = (poolIndex + 1) % JVM_SERVICE_POOL.size
    return JVM_SERVICE_POOL[poolIndex]
}
