/*
 * Copyright 2020-2024 RW-HPS Team and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/RW-HPS/RW-HPS/blob/master/LICENSE
 */

package net.rwhps.server.plugin.internal.headless.inject.net

import net.rwhps.server.core.thread.CallTimeTask
import net.rwhps.server.core.thread.Threads
import net.rwhps.server.data.global.Data
import net.rwhps.server.data.global.NetStaticData
import net.rwhps.server.data.totalizer.TimeAndNumber
import net.rwhps.server.func.Control
import net.rwhps.server.game.enums.GameCommandActions
import net.rwhps.server.game.enums.GamePingActions
import net.rwhps.server.game.event.game.PlayerChatEvent
import net.rwhps.server.game.event.game.PlayerLeaveEvent
import net.rwhps.server.game.event.game.PlayerOperationFactoryBuildUnitEvent
import net.rwhps.server.game.event.game.PlayerOperationUnitEvent
import net.rwhps.server.game.player.PlayerHess
import net.rwhps.server.io.GameInputStream
import net.rwhps.server.io.GameOutputStream
import net.rwhps.server.io.output.CompressOutputStream
import net.rwhps.server.io.packet.GameCommandOnePacket
import net.rwhps.server.io.packet.Packet
import net.rwhps.server.net.core.DataPermissionStatus.ServerStatus
import net.rwhps.server.net.core.server.AbstractNetConnect
import net.rwhps.server.net.core.server.AbstractNetConnectData
import net.rwhps.server.net.core.server.AbstractNetConnectServer
import net.rwhps.server.net.netconnectprotocol.internal.relay.relayServerTypeInternal
import net.rwhps.server.net.netconnectprotocol.internal.relay.relayServerTypeReplyInternalPacket
import net.rwhps.server.net.netconnectprotocol.internal.server.playerExitInternalPacket
import net.rwhps.server.plugin.internal.headless.inject.core.GameEngine
import net.rwhps.server.plugin.internal.headless.inject.lib.PlayerConnectX
import net.rwhps.server.util.PacketType
import net.rwhps.server.util.annotations.MainProtocolImplementation
import net.rwhps.server.util.game.command.CommandHandler
import net.rwhps.server.util.log.ColorCodes
import net.rwhps.server.util.log.Log
import java.io.IOException
import kotlin.math.min
import com.corrodinggames.rts.gameFramework.j.k as GameNetInputStream

/**
 * Common server implementation
 *
 * @property supportedversionBeta   Server is Beta
 * @property supportedversionGame   Server Support Version String
 * @property supportedVersionInt    Server Support Version Int
 * @property player                 Player
 * @property permissionStatus       ServerStatus
 * @property version                Protocol version
 * @constructor
 *
 * @date 2020/9/5 17:02:33
 * @author Dr (dr@der.kim)
 */
@MainProtocolImplementation
open class GameVersionServer(val playerConnectX: PlayerConnectX): AbstractNetConnect(playerConnectX.connectionAgreement), AbstractNetConnectData, AbstractNetConnectServer {
    init {
        playerConnectX.serverConnect = this
    }

    private var relaySelect: ((String) -> Unit)? = null

    override val supportedversionBeta = false
    override val supportedversionGame = "1.15"
    override val supportedVersionInt = 176

    override val name: String get() = player.name
    override val registerPlayerId: String? get() = player.connectHexID

    override val betaGameVersion: Boolean get() = supportedversionBeta
    override val clientVersion: Int get() = supportedVersionInt

    /** 玩家  */
    override lateinit var player: PlayerHess
    override var permissionStatus: ServerStatus = ServerStatus.InitialConnection
        internal set

    override val version: String
        get() = "1.15 RW-HPS"

    val update = TimeAndNumber(2, 1)

    override fun sendSystemMessage(msg: String) {
        try {
            sendPacket(NetStaticData.RwHps.abstractNetPacket.getSystemMessagePacket(msg))
        } catch (e: IOException) {
            Log.error("[Player] Send System Chat Error", e)
        }
    }

    override fun sendChatMessage(msg: String, sendBy: String, team: Int) {
        try {
            sendPacket(NetStaticData.RwHps.abstractNetPacket.getChatMessagePacket(msg, sendBy, team))
        } catch (e: IOException) {
            Log.error("[Player] Send Player Chat Error", e)
        }
    }

    @Throws(IOException::class)
    override fun sendServerInfo(utilData: Boolean) {
    }

    override fun sendTeamData(gzip: CompressOutputStream) {
    }

    override fun sendSurrender() {
    }

    override fun sendKick(reason: String) {
        val o = GameOutputStream()
        o.writeString(reason)
        sendPacket(o.createPacket(PacketType.KICK))
        Thread.sleep(100)
        disconnect()
    }

    override fun sendPing() {
    }

    @Throws(IOException::class)
    override fun sendStartGame() {
    }

    @Throws(IOException::class)
    override fun receiveChat(packet: Packet) {
        GameInputStream(packet).use { stream ->
            val message: String = stream.readString()
            var response: CommandHandler.CommandResponse? = null

            //Log.clog("[${playerConnectX.room.roomID}] [&by {0} &fr]: &y{1}", name, ColorCodes.formatColors(message,true))
            Log.clog("[&by {0} &fr]: &y{1}", player.name, ColorCodes.formatColors(message, true))

            // Afk Stop
            if (player.isAdmin && Threads.containsTimeTask(CallTimeTask.PlayerAfkTask)) {
                Threads.closeTimeTask(CallTimeTask.PlayerAfkTask)
            }

            if (Data.vote != null) {
                Data.vote!!.toVote(player, message)
            }

            // Msg Command
            if (message.startsWith(".") || message.startsWith("-") || message.startsWith("_")) {
                val strEnd = min(message.length, 3)
                // 提取出消息前三位 判定是否为QC命令
                response = if ("qc" == message.substring(1, strEnd)) {
                    GameEngine.data.room.clientHandler.handleMessage("/" + message.substring(5), player)
                } else {
                    GameEngine.data.room.clientHandler.handleMessage("/" + message.substring(1), player)
                }
            }

            if (response == null || response.type == CommandHandler.ResponseType.noCommand) {
                if (message.length > Data.configServer.maxMessageLen) {
                    sendSystemMessage(Data.i18NBundle.getinput("message.maxLen"))
                    packet.status = Control.EventNext.STOPPED
                    return
                }
                GameEngine.data.eventManage.fire(PlayerChatEvent(player, message))
            } else if (response.type != CommandHandler.ResponseType.valid) {
                when (response.type) {
                    CommandHandler.ResponseType.manyArguments -> {
                        player.sendSystemMessage("Too many arguments. Usage: " + response.command.text + " " + response.command.paramText)
                        packet.status = Control.EventNext.STOPPED
                    }
                    CommandHandler.ResponseType.fewArguments -> {
                        player.sendSystemMessage("Too few arguments. Usage: " + response.command.text + " " + response.command.paramText)
                        packet.status = Control.EventNext.STOPPED
                    }
                    else -> {
                        // Ignore
                    }
                }
            } else {
                packet.status = Control.EventNext.STOPPED
            }
        }
    }

    @Throws(IOException::class)
    override fun receiveCommand(packet: Packet) {
        try {
            GameCommandOnePacket(GameInputStream(packet).getDecodeBytes()).let { command ->
                if (command.operationUnit != null) {
                    command.operationUnit!!.let {
                        if (Data.configServer.turnStoneIntoGold && it.gameCommandActions == GameCommandActions.BUILD) {
                            gameSummon(it.unitName!!, it.x, it.y)
                            packet.status = Control.EventNext.STOPPED
                            return
                        } else {
                            PlayerOperationUnitEvent(player, it.gameCommandActions, it.unitName, it.x, it.y).let { event ->
                                GameEngine.data.eventManage.fire(event).await()
                                if (!event.resultStatus) {
                                    packet.status = Control.EventNext.STOPPED
                                    return
                                }
                            }
                        }
                    }
                }
                if (command.mapPoint != null && command.actionIdData.actionId.startsWith("c_6_")) {
                    GamePingActions.from(command.actionIdData.actionId.removePrefix("c_6_"))!!.let { action ->
                        val lambda = player.getData<(AbstractNetConnectServer, GamePingActions, Float, Float) -> Unit>("Ping")
                        if (lambda != null) {
                            lambda(this, action, command.mapPoint!![0], command.mapPoint!![1])
                            player.removeData("Ping")
                        }
                    }
                }

                if (!command.actionIdData.actionId.startsWith("_")) {
                    PlayerOperationFactoryBuildUnitEvent(player, command.actionIdData).let { event->
                        GameEngine.data.eventManage.fire(event).await()
                        if (!event.resultStatus) {
                            packet.status = Control.EventNext.STOPPED
                            return
                        }
                    }

                }
            }
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    @Throws(IOException::class)
    override fun receiveCheckPacket(packet: Packet) {
    }

    @Throws(IOException::class)
    override fun getGameSaveData(packet: Packet): ByteArray {
        return PacketType.nullPacket.bytes
    }

    //@Throws(IOException::class)
    override fun getPlayerInfo(packet: Packet): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun registerConnection(packet: Packet) {
    }

    override fun gameSummon(unit: String, x: Float, y: Float) {
        try {
            val commandPacket = GameEngine.gameEngine.cf.b()

            val out = GameOutputStream()
            out.flushEncodeData(CompressOutputStream.getGzipOutputStream("c", false).apply {
                writeBytes(NetStaticData.RwHps.abstractNetPacket.gameSummonPacket(player.site, unit, x, y).bytes)
            })

            commandPacket.a(GameNetInputStream(playerConnectX.netEnginePackaging.transformHessPacket(out.createPacket(PacketType.TICK))))

            commandPacket.c = GameEngine.data.gameHessData.tickNetHess + 10
            GameEngine.gameEngine.cf.b.add(commandPacket)
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    override fun sendRelayServerType(msg: String, run: ((String) -> Unit)?) {
        try {
            sendPacket(relayServerTypeInternal(msg))

            relaySelect = run

            connectReceiveData.inputPassword = true
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    override fun sendRelayServerTypeReply(packet: Packet) {
        try {
            connectReceiveData.inputPassword = false

            val id = relayServerTypeReplyInternalPacket(packet)
            if (relaySelect != null) {
                relaySelect!!(id)
                relaySelect = null
            }
        } catch (e: Exception) {
            Log.error(e)
        }
    }

    override fun sendErrorPasswd() {
    }

    override fun disconnect() {
        if (super.isDis) {
            return
        }
        super.isDis = true
        if (this::player.isInitialized) {
            playerConnectX.room.playerManage.playerGroup.remove(player)
            if (!playerConnectX.room.isStartGame) {
                playerConnectX.room.playerManage.playerAll.remove(player)
            }
            GameEngine.data.eventManage.fire(PlayerLeaveEvent(player)).await()

            playerConnectX.netEnginePackaging.addPacket(playerExitInternalPacket())

            player.clear()
        }
        super.close(null)
    }

    override fun sendGameSave(packet: Packet) {
    }

    override fun receivePacket(packet: Packet) {
        playerConnectX.netEnginePackaging.addPacket(packet)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        if (player != (other as GameVersionServer).player) {
            return false
        }

        return false
    }

    override fun hashCode(): Int {
        return player.hashCode()
    }
}