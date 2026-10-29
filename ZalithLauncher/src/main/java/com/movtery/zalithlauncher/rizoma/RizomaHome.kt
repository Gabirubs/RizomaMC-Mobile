package com.movtery.zalithlauncher.rizoma

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskStage
import com.movtery.zalithlauncher.ui.toAndroidString
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.localLogin
import com.movtery.zalithlauncher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion
import com.movtery.zalithlauncher.game.download.game.GameDownloadInfo
import com.movtery.zalithlauncher.game.download.game.GameInstaller
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.setting.AllSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val Laranja = Color(0xFFFF5A1F)
private val LaranjaClaro = Color(0xFFFF7A45)
private val Fundo = Color(0xFF080D14)
private val Cinza = Color(0xFF93A1B0)
private val Verde = Color(0xFF3FBF7A)

private val Barlow = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold)
)

private fun bc(size: TextUnit, color: Color = Color.White, weight: FontWeight = FontWeight.Bold, spacing: TextUnit = 1.sp) =
    TextStyle(fontFamily = Barlow, fontSize = size, color = color, fontWeight = weight, letterSpacing = spacing)

private const val NOME_VERSAO = "RizomaMC"
private const val SERVIDOR = "jogar.rizomamc.com.br"
private const val ZINK = "0fa435e2-46df-45c9-906c-b29606aaef00"
private const val DISCORD = "https://discord.rizomamc.com.br"
private const val LOJA = "https://rizomamc.com.br"

private enum class Fase { PRONTO, PREPARANDO, INSTALANDO, ATUALIZANDO, ABRINDO, ERRO }

/** Tela única do RizomaMC: um botão que instala, atualiza e entra no servidor. */
@Composable
fun RizomaHome(
    onSettings: () -> Unit,
    onOpenLink: (String) -> Unit,
    onPlay: (version: Version, address: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fase by remember { mutableStateOf(Fase.PRONTO) }
    var detalhe by remember { mutableStateOf("Pronto para jogar") }
    var progresso by remember { mutableFloatStateOf(-1f) }
    var online by remember { mutableStateOf<Int?>(null) }
    var statusLido by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        online = lerStatus()
        statusLido = true
    }

    fun jogar() {
        if (fase != Fase.PRONTO && fase != Fase.ERRO) return
        scope.launch {
            try {
                fase = Fase.PREPARANDO
                detalhe = "Preparando a conta"
                progresso = -1f
                garantirConta()
                if (AllSettings.renderer.getValue().isEmpty()) AllSettings.renderer.save(ZINK)

                val versao = garantirVersao(context, scope) { texto, p ->
                    fase = Fase.INSTALANDO
                    detalhe = texto
                    progresso = p
                }

                fase = Fase.ATUALIZANDO
                detalhe = "Verificando atualizações"
                progresso = -1f
                val pasta = versao.getGameDir()
                val plano = RizomaSync.verificar(pasta)
                if (!plano.emDia) {
                    RizomaSync.aplicar(pasta, plano) { feito, total ->
                        detalhe = "Baixando atualização • ${feito / 1048576} de ${total / 1048576} MB"
                        progresso = if (total > 0) feito.toFloat() / total else -1f
                    }
                }

                fase = Fase.ABRINDO
                detalhe = "Abrindo o jogo"
                progresso = -1f
                onPlay(versao, SERVIDOR)
                delay(3000)
                fase = Fase.PRONTO
                detalhe = "Pronto para jogar • modpack v${plano.versao}"
            } catch (e: Throwable) {
                fase = Fase.ERRO
                detalhe = e.message ?: "Algo deu errado. Tente de novo."
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Fundo)) {
        FundoAnimado()
        Row(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BarraTopo(onOpenLink, onSettings)
                Spacer(Modifier.weight(1f))
                Text("TEMPORADA 1 • APOCALIPSE", style = bc(14.sp, LaranjaClaro, FontWeight.SemiBold, 3.sp))
                Text(
                    buildAnnotatedString {
                        append("RIZOMA")
                        withStyle(SpanStyle(color = Laranja)) { append("MC") }
                    },
                    style = bc(64.sp, spacing = 0.sp)
                )
                Text(
                    "Sobreviva no mundo aberto ou caia direto nos minigames PvP. Escolha o modo dentro do jogo.",
                    style = TextStyle(fontSize = 13.sp, color = Color(0xFFCFD6DD)),
                    modifier = Modifier.width(380.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Versão modificada não oficial do Zalith Launcher 2 (GPL-3.0)",
                    style = TextStyle(fontSize = 10.sp, color = Color(0x99CFD6DD))
                )
                Spacer(Modifier.weight(1f))
                Rodape(online, statusLido, detalhe, fase, progresso)
            }
            Column(
                Modifier.width(260.dp).fillMaxHeight().padding(start = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(40.dp))
                CardModo(R.drawable.rizoma_card_dayz, "MUNDO ABERTO • PVP", "SERVIDOR DAYZ", Modifier.weight(1f))
                CardModo(R.drawable.rizoma_card_minigames, "FFA • TDM • GUN GAME", "MINIGAMES", Modifier.weight(1f))
                BotaoJogar(fase) { jogar() }
            }
        }
    }
}

@Composable
private fun FundoAnimado() {
    val t = rememberInfiniteTransition(label = "fundo")
    val escala by t.animateFloat(1.0f, 1.12f, infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    Image(
        painter = painterResource(R.drawable.rizoma_fundo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = escala; scaleY = escala }
    )
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xD1000000), 0.45f to Color(0x73000000), 0.7f to Color(0x00000000))))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xB3000000), 0.3f to Color(0x00000000), 0.7f to Color(0x00000000), 1f to Color(0xE0000000))))
}

@Composable
private fun BarraTopo(onOpenLink: (String) -> Unit, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Laranja)) { append("RIZOMA") }
                append("MC")
            },
            style = bc(22.sp, spacing = 2.sp)
        )
        Spacer(Modifier.width(12.dp))
        Chip("DISCORD") { onOpenLink(DISCORD) }
        Chip("LOJA") { onOpenLink(LOJA) }
        Chip("⚙ CONFIG", onSettings)
    }
}

@Composable
private fun Chip(texto: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(4.dp))
            .background(Color(0x66000000))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(texto, style = bc(14.sp, weight = FontWeight.SemiBold, spacing = 2.sp)) }
}

@Composable
private fun CardModo(imagem: Int, sub: String, nome: String, modifier: Modifier) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(6.dp))
    ) {
        Image(painterResource(imagem), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color(0x00000000), 1f to Color(0xE0000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(sub, style = bc(11.sp, LaranjaClaro, FontWeight.SemiBold))
            Text(nome, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White))
        }
    }
}

@Composable
private fun Rodape(online: Int?, statusLido: Boolean, detalhe: String, fase: Fase, progresso: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (online != null) Verde else if (statusLido) Color(0xFFE24B4A) else Cinza))
                Spacer(Modifier.width(6.dp))
                Text(if (!statusLido) "VERIFICANDO" else if (online != null) "ONLINE" else "OFFLINE", style = bc(16.sp, weight = FontWeight.SemiBold))
                if (online != null) Text("  • $online jogadores", style = bc(16.sp, Cinza, FontWeight.SemiBold))
            }
            Text(SERVIDOR, style = TextStyle(fontSize = 11.sp, color = Color(0xFF8794A2)))
        }
        Box(Modifier.padding(horizontal = 20.dp).width(1.dp).height(36.dp).background(Color(0xFF2A313A)))
        Column(Modifier.weight(1f)) {
            Text(
                detalhe,
                maxLines = 2,
                style = TextStyle(
                    fontSize = 13.sp,
                    color = when (fase) {
                        Fase.ERRO -> Color(0xFFF09595)
                        Fase.PRONTO -> Color(0xFF9BE3A9)
                        else -> Color(0xFFCFD6DD)
                    }
                )
            )
            if (fase == Fase.INSTALANDO || fase == Fase.ATUALIZANDO || fase == Fase.PREPARANDO || fase == Fase.ABRINDO) {
                Spacer(Modifier.height(6.dp))
                val mod = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                if (progresso in 0f..1f) {
                    LinearProgressIndicator(progress = { progresso }, modifier = mod, color = Laranja, trackColor = Color(0xFF1A222C))
                } else {
                    LinearProgressIndicator(modifier = mod, color = Laranja, trackColor = Color(0xFF1A222C))
                }
            }
        }
    }
}

private val CantoCortado = GenericShape { size, _ ->
    val c = size.height * 0.35f
    moveTo(0f, 0f)
    lineTo(size.width - c, 0f)
    lineTo(size.width, c)
    lineTo(size.width, size.height)
    lineTo(c, size.height)
    lineTo(0f, size.height - c)
    close()
}

@Composable
private fun BotaoJogar(fase: Fase, onClick: () -> Unit) {
    val ocupado = fase != Fase.PRONTO && fase != Fase.ERRO
    val texto = when (fase) {
        Fase.PRONTO -> "JOGAR"
        Fase.PREPARANDO -> "PREPARANDO"
        Fase.INSTALANDO -> "INSTALANDO"
        Fase.ATUALIZANDO -> "ATUALIZANDO"
        Fase.ABRINDO -> "ABRINDO"
        Fase.ERRO -> "TENTAR DE NOVO"
    }
    val t = rememberInfiniteTransition(label = "botao")
    val desloc by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "brilho")
    val fundo = if (ocupado) Brush.horizontalGradient(listOf(Color(0xFF1A222C), Color(0xFF1A222C)))
    else Brush.horizontalGradient(
        listOf(Color(0xFFE8400C), Color(0xFFFF6A1F), Color(0xFFFFA040), Color(0xFFFF6A1F), Color(0xFFE8400C)),
        startX = -600f * desloc, endX = 900f - 600f * desloc
    )
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(CantoCortado).background(fundo)
            .clickable(enabled = !ocupado, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, style = bc(if (texto.length > 10) 22.sp else 30.sp, if (ocupado) Cinza else Color.White, spacing = 3.sp))
    }
}

private suspend fun lerStatus(): Int? = withContext(Dispatchers.IO) {
    try {
        val json = JSONObject(URL("https://api.mcsrvstat.us/3/$SERVIDOR").readText())
        if (json.optBoolean("online")) json.optJSONObject("players")?.optInt("online") ?: 0 else null
    } catch (_: Throwable) {
        null
    }
}

/** O nick de verdade é escolhido no servidor (DarkLogin); aqui só existe uma conta offline fixa. */
private suspend fun garantirConta() {
    if (AccountsManager.accountsFlow.value.isEmpty()) {
        localLogin("Jogador", null)
        AccountsManager.reloadAccounts()
        repeat(40) {
            if (AccountsManager.accountsFlow.value.isNotEmpty()) return@repeat
            delay(250)
        }
    }
    if (AccountsManager.currentAccountFlow.value == null) {
        AccountsManager.accountsFlow.value.firstOrNull()?.let { AccountsManager.setCurrentAccount(it) }
    }
}

private fun acharVersao(): Version? =
    VersionsManager.versions.value.firstOrNull { it.getVersionName() == NOME_VERSAO && it.isValid() }

/** Instala Minecraft 1.21.1 + NeoForge 21.1.233 na primeira vez. */
private suspend fun garantirVersao(context: Context, scope: CoroutineScope, onProgresso: (String, Float) -> Unit): Version {
    acharVersao()?.let { return it }
    val installer = GameInstaller(
        context = context,
        info = GameDownloadInfo(
            gameVersion = "1.21.1",
            customVersionName = NOME_VERSAO,
            neoforge = NeoForgeVersion("21.1.233", false)
        ),
        scope = scope
    )
    val observador = scope.launch {
        installer.tasksFlow.collect { tarefas ->
            val atual = tarefas.firstOrNull { it.task.stage.value == TaskStage.RUNNING } ?: return@collect
            onProgresso("Instalando o jogo • " + atual.title.toAndroidString(context), atual.task.progress.value)
        }
    }
    try {
        suspendCancellableCoroutine { cont ->
            installer.installGame(
                isRunning = {},
                onInstalled = { if (cont.isActive) cont.resume(Unit) },
                onError = { if (cont.isActive) cont.resumeWithException(it) },
                onGameAlreadyInstalled = { if (cont.isActive) cont.resume(Unit) }
            )
        }
    } finally {
        observador.cancel()
    }
    VersionsManager.refresh("RizomaMC", NOME_VERSAO)
    repeat(60) {
        acharVersao()?.let { return it }
        delay(500)
    }
    throw IllegalStateException("O jogo foi instalado, mas não foi encontrado. Tente de novo.")
}
